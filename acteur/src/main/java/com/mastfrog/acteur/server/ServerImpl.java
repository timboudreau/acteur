/*
 * The MIT License
 *
 * Copyright 2011-2014 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.server;

import com.google.inject.Provider;
import com.google.inject.name.Named;
import static com.mastfrog.acteur.server.ServerModule.EVENT_THREADS;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_SYSTEM_EXIT_ON_BIND_FAILURE;
import static com.mastfrog.acteur.server.ServerModule.WORKER_THREADS;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 *
 * @author Tim BoudreauUpstreamHandlerImpl
 */
final class ServerImpl implements Server {

    private final ChannelInitializer<SocketChannel> pipelineFactory;
    private int port = 8123;
    private final ThreadFactory eventThreadFactory;
    private final ThreadCount eventThreadCount;
    private final ThreadFactory workerThreadFactory;
    private final ThreadCount workerThreadCount;
    private final String applicationName;
    private final ShutdownHookRegistry registry;
    private final Provider<ServerBootstrap> bootstrapProvider;
    private final Provider<ApplicationControl> app;
    private final Settings settings;

    @Inject
    ServerImpl(
            ChannelInitializer<SocketChannel> pipelineFactory,
            @Named(EVENT_THREADS) ThreadFactory eventThreadFactory,
            @Named(EVENT_THREADS) ThreadCount eventThreadCount,
            @Named(WORKER_THREADS) ThreadFactory workerThreadFactory,
            @Named(WORKER_THREADS) ThreadCount workerThreadCount,
            @Named("application") String applicationName,
            Provider<ServerBootstrap> bootstrapProvider,
            ShutdownHookRegistry registry,
            Provider<ApplicationControl> app,
            Settings settings) {
        this.port = settings.getInt(ServerModule.PORT, 8123);
        this.pipelineFactory = pipelineFactory;
        this.eventThreadFactory = eventThreadFactory;
        this.eventThreadCount = eventThreadCount;
        this.workerThreadFactory = workerThreadFactory;
        this.workerThreadCount = workerThreadCount;
        this.applicationName = applicationName;
        this.bootstrapProvider = bootstrapProvider;
        this.registry = registry;
        this.app = app;
        this.settings = settings;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return applicationName + " on port " + port + " with "
                + eventThreadCount.get() + " event threads and "
                + workerThreadCount.get() + " worker threads.";
    }

    @Override
    public ServerControl start(int port) throws IOException {
        this.port = port;
        try {
            final CountDownLatch afterStart = new CountDownLatch(1);
            final ServerControlImpl result = new ServerControlImpl(port, afterStart);
            ServerBootstrap bootstrap = bootstrapProvider.get();

            String bindAddress = settings.getString("bindAddress");
            InetAddress addr = null;
            if (bindAddress != null) {
                addr = InetAddress.getByName(bindAddress);
            }

            bootstrap.group(result.events, result.workers)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler())
//                    .option(ChannelOption.TCP_NODELAY, true)
                    .childHandler(pipelineFactory);
            if (addr == null) {
                bootstrap = bootstrap.localAddress(new InetSocketAddress(port));
            } else {
                bootstrap = bootstrap.localAddress(addr, port);
            }

            // Bind and start to accept incoming connections.
            bootstrap.bind().addListener(result);
            System.err.println("Starting " + this);
            if (settings.getBoolean(ServerModule.SETTINGS_KEY_CORS_ENABLED, true)) {
                // XXX ugly place to do this
                app.get().enableDefaultCorsHandling();
            }
            afterStart.await();
            return result.throwIfFailure();
        } catch (InterruptedException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public ServerControl start() throws IOException {
        return start(this.port);
    }

    @Override
    public ServerControl start(boolean ssl) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ServerControl start(int port, boolean ssl) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private static class WeakRunnable implements Runnable {

        private final Reference<Runnable> delegate;

        WeakRunnable(Runnable real) {
            this.delegate = new WeakReference<>(real);
        }

        @Override
        public void run() {
            Runnable real = delegate.get();
            if (real != null) {
                real.run();
            }
        }
    }

    private class ServerControlImpl implements ServerControl, Runnable, ChannelFutureListener {

        private Channel localChannel;

        private final EventLoopGroup events = new NioEventLoopGroup(eventThreadCount.get(), eventThreadFactory);
        private final EventLoopGroup workers = new NioEventLoopGroup(workerThreadCount.get(), workerThreadFactory);
        private final int port;
        private final CountDownLatch afterStart;
        private final CountDownLatch waitClose = new CountDownLatch(1);
        private volatile boolean shuttingDown;

        ServerControlImpl(int port, CountDownLatch afterStart) {
            this.port = port;
            this.afterStart = afterStart;
        }

        public void shutdown(boolean immediately, long timeout, TimeUnit unit) throws InterruptedException {
            shutdown(timeout, unit, true);
        }

        private synchronized boolean isTerminated() {
            return localChannel == null ? true : !localChannel.isOpen();
        }

        private void shutdown(long timeout, TimeUnit unit, boolean await) throws InterruptedException {
            if (shuttingDown) {
                // We can reenter on the shutdown hook thread
                await(timeout, unit);
                return;
            }
            shuttingDown = true;
            try {
                Channel ch;
                synchronized (this) {
                    ch = localChannel;
                }
                if (ch != null) {
                    if (ch.isOpen()) {
                        if (await) {
                            ch.close().await(timeout, unit);
                        } else {
                            ch.close();
                        }
                    }
                }
            } catch (InterruptedException ex) {
                // OK
            } finally {
                if (await) {
                    events.shutdownGracefully(0, timeout / 3, unit);
                    workers.shutdownGracefully(0, timeout / 3, unit);
                } else {
                    events.shutdownGracefully();
                    workers.shutdownGracefully();
                }
                shuttingDown = false;
                synchronized(this) {
                    localChannel = null;
                }
            }
        }

        @Override
        public void shutdown(boolean immediately) throws InterruptedException {
            shutdown(1, TimeUnit.SECONDS, true);
            await();
        }

        @Override
        public void await() throws InterruptedException {
            try {
                // Can be interrupted if the JVM starts running shutdown hooks
                // and we are already waiting
                waitClose.await();
            } catch (InterruptedException ex) {
                return;
            }
        }

        @Override
        public void awaitUninterruptibly() {
            while (!isTerminated()) {
                try {
                    await();
                } catch (InterruptedException ex) {
                    //do nothing
                }
            }
        }

        @Override
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            waitClose.await(nanosTimeout, TimeUnit.NANOSECONDS);
            return 0;
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            waitClose.await(time, unit);
            return isTerminated();
        }

        @Override
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            long howLong = deadline.getTime() - System.currentTimeMillis();
            if (howLong > 0) {
                return await(howLong, TimeUnit.MILLISECONDS);
            }
            return isTerminated();
        }

        @Override
        public void signal() {
            signalAll();
        }

        @Override
        public void signalAll() {
            try {
                shutdown(0, TimeUnit.MILLISECONDS, false);
            } catch (InterruptedException ex) {
                Logger.getLogger(ServerImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void run() {
            try {
                if (!isTerminated()) {
                    shutdown(0, TimeUnit.MILLISECONDS, false);
                }
            } catch (InterruptedException ex) {
                Exceptions.chuck(ex);
            }
        }

        @Override
        public int getPort() {
            return port;
        }

        private Throwable failure;
        private boolean initialized;

        @Override
        public synchronized void operationComplete(ChannelFuture f) throws Exception {
            if (!initialized) {
                initialized = true;
                failure = f.cause();
                if (failure == null) {
                    localChannel = f.channel();
                    registry.add(new WeakRunnable(this));
                } else {
                    events.shutdownGracefully();
                    workers.shutdownGracefully();
                }
                afterStart.countDown();
                f.channel().closeFuture().addListener(this);
            } else {
                // Waiting for close
                waitClose.countDown();
            }
        }

        public synchronized ServerControl throwIfFailure() {
            if (failure != null) {
                if (failure instanceof BindException
                        && settings.getBoolean(SETTINGS_KEY_SYSTEM_EXIT_ON_BIND_FAILURE, true)) {
                    failure.printStackTrace(System.err);
                    System.err.flush();
                    System.exit(1);
                }
                Exceptions.chuck(failure);
            }
            return this;
        }
    }
}
