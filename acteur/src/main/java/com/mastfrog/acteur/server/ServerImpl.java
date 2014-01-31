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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import static com.mastfrog.acteur.server.ServerModule.EVENT_THREADS;
import static com.mastfrog.acteur.server.ServerModule.WORKER_THREADS;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class ServerImpl implements Server {

    private final ChannelInitializer<SocketChannel> pipelineFactory;
    private int port = 8123;
    private final ThreadFactory eventThreadFactory;
    private final ThreadCount eventThreadCount;
    private final ThreadGroup eventThreadGroup;
    private final ThreadFactory workerThreadFactory;
    private final ThreadCount workerThreadCount;
    private final ThreadGroup workerThreadGroup;
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
            @Named(EVENT_THREADS) ThreadGroup eventThreadGroup,
            @Named(WORKER_THREADS) ThreadFactory workerThreadFactory,
            @Named(WORKER_THREADS) ThreadCount workerThreadCount,
            @Named(WORKER_THREADS) ThreadGroup workerThreadGroup,
            @Named("application") String applicationName,
            Provider<ServerBootstrap> bootstrapProvider,
            ShutdownHookRegistry registry,
            Provider<ApplicationControl> app,
            Settings settings) {
        this.port = settings.getInt(ServerModule.PORT, 8123);
        this.pipelineFactory = pipelineFactory;
        this.eventThreadFactory = eventThreadFactory;
        this.eventThreadCount = eventThreadCount;
        this.eventThreadGroup = eventThreadGroup;
        this.workerThreadFactory = workerThreadFactory;
        this.workerThreadCount = workerThreadCount;
        this.workerThreadGroup = workerThreadGroup;
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
    private Channel localChannel;

    @Override
    public ServerControl start(int port) throws IOException {
        this.port = port;
        try {
            final ServerControlImpl result = new ServerControlImpl(port);
            ServerBootstrap bootstrap = bootstrapProvider.get();

            bootstrap.group(result.events, result.workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(pipelineFactory)
                    .localAddress(new InetSocketAddress(port));

            localChannel = bootstrap.bind().sync().channel();
            System.err.println("Starting " + this);

            final CountDownLatch afterStart = new CountDownLatch(1);
            if (settings.getBoolean(ServerModule.SETTINGS_KEY_CORS_ENABLED, true)) {
                app.get().enableDefaultCorsHandling();
            }
            result.events.submit(new Runnable() {

                @Override
                public void run() {
                    // Ensure a server is not held in memory if it has been
                    // gc'd
                    registry.add(new WeakRunnable(result));
                    afterStart.countDown();
                }
            });
            afterStart.await();
            // Bind and start to accept incoming connections.
            return result;
        } catch (InterruptedException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public ServerControl start() throws IOException {
        return start(this.port);
    }

    private static class WeakRunnable implements Runnable {

        private final Reference<Runnable> delegate;

        WeakRunnable(Runnable real) {
            this.delegate = new WeakReference(real);
        }

        @Override
        public void run() {
            Runnable real = delegate.get();
            if (real != null) {
                real.run();
            }
        }
    }

    private class ServerControlImpl implements ServerControl, Runnable {

        private final NioEventLoopGroup events = new NioEventLoopGroup(eventThreadCount.get(), eventThreadFactory);
        private final NioEventLoopGroup workers = new NioEventLoopGroup(workerThreadCount.get(), workerThreadFactory);
        private final int port;

        ServerControlImpl(int port) {
            this.port = port;
        }

        public void shutdown(boolean immediately, long timeout, TimeUnit unit) throws InterruptedException {
            shutdown(immediately, timeout, unit, true);
        }

        private boolean isTerminated() {
            return events.isTerminated() && workers.isTerminated();
        }

        private void shutdown(boolean immediately, long timeout, TimeUnit unit, boolean await) throws InterruptedException {
            // XXX this can actually take 3x the timeout
            eventThreadGroup.interrupt();
            if (events != null) {
                events.shutdownGracefully(0, immediately ? 0L : timeout / 2, unit);
            }
            if (workers != null) {
                workers.shutdownGracefully(0, immediately ? 0L : timeout / 2, unit);
            }
            workerThreadGroup.interrupt();
            try {
                if (localChannel != null) {
                    if (localChannel.isOpen()) {
                        if (await) {
                            localChannel.close().await(timeout, unit);
                        } else {
                            localChannel.close();
                        }
                    }
                }
            } finally {
                events.awaitTermination(timeout, unit);
                workers.awaitTermination(timeout, unit);
            }
        }

        @Override
        public void shutdown(boolean immediately) throws InterruptedException {
            shutdown(false, 1, TimeUnit.SECONDS, true);
            await();
        }

        @Override
        public void await() throws InterruptedException {
            if (events != null) {
                events.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
            }
            if (workers != null) {
                workers.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
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
            if (!isTerminated() && events != null) {
                events.awaitTermination(nanosTimeout, TimeUnit.NANOSECONDS);
            }
            return 0;
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (events != null) {
                events.awaitTermination(time, unit);
            }
            return isTerminated();
        }

        @Override
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            long howLong = deadline.getTime() - System.currentTimeMillis();
            if (howLong > 0) {
                await(howLong, TimeUnit.MILLISECONDS);
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
                shutdown(false, 0, TimeUnit.MILLISECONDS, false);
            } catch (InterruptedException ex) {
                Logger.getLogger(ServerImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void run() {
            try {
                if (!isTerminated()) {
                    shutdown(true, 0, TimeUnit.MILLISECONDS, false);
                }
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        @Override
        public int getPort() {
            return port;
        }
    }
}
