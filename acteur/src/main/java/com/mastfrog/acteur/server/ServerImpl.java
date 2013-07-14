/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
import com.google.inject.name.Named;
import static com.mastfrog.acteur.server.ServerModule.EVENT_THREADS;
import static com.mastfrog.acteur.server.ServerModule.WORKER_THREADS;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Exceptions;

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
    private final ServerBootstrap bootstrap;

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
            ServerBootstrap bootstrap,
            ShutdownHookRegistry registry,
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
        this.bootstrap = bootstrap;
        registry.add(new ServerShutdown(this));
    }

    private static final class ServerShutdown implements Runnable {

        private final WeakReference<ServerImpl> server;

        public ServerShutdown(ServerImpl server) {
            this.server = new WeakReference<>(server);
        }

        @Override
        public void run() {
            ServerImpl im = server.get();
            if (im != null && im.isStarted()) {
                try {
                    im.shutdown(false, 10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    boolean isStarted() {
        return events != null && !events.isTerminated();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return applicationName + " on port " + port + " with " + 
                eventThreadCount.get() + " event threads and " + 
                workerThreadCount.get() + " worker threads.";
    }
    private Channel localChannel;

    @Override
    public Condition start(int port) throws IOException {
        this.port = port;
        return start();
    }

    NioEventLoopGroup events;
    NioEventLoopGroup workers;

    public Condition start() throws IOException {
        if (events != null) {
            throw new IllegalStateException("Already started");
        }
        try {
            events = new NioEventLoopGroup(eventThreadCount.get(), eventThreadFactory);
            workers = new NioEventLoopGroup(workerThreadCount.get(), workerThreadFactory);
            
            bootstrap.group(events, workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(pipelineFactory)
                    .localAddress(new InetSocketAddress(port));

            localChannel = bootstrap.bind().sync().channel();
            System.err.println("Starting " + this);
            
            final CountDownLatch afterStart = new CountDownLatch(1);
            events.submit(new Runnable() {

                @Override
                public void run() {
                    afterStart.countDown();
                }
            });

            afterStart.await();
            // Bind and start to accept incoming connections.
            return getCondition();
        } catch (InterruptedException ex) {
            throw new Error(ex);
        }
    }

    public void shutdown(boolean immediately, long timeout, TimeUnit unit) throws InterruptedException {
        shutdown(immediately, timeout, unit, true);
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
            if (events != null) {
                events.awaitTermination(timeout, unit);
                if (events.isTerminated()) {
                    events = null;
                }
            }
            if (workers != null) {
                workers.awaitTermination(timeout, unit);
                if (workers.isTerminated()) {
                    workers = null;
                }
            }
        }
    }

    @Override
    public void shutdown(boolean immediately) throws InterruptedException {
        shutdown(false, 1, TimeUnit.SECONDS, true);
        await();
    }

    void await() throws InterruptedException {
        if (events == null) {
            return;
        }
        if (events != null) {
            events.awaitTermination(1, TimeUnit.DAYS);
        }
        if (workers != null) {
            workers.awaitTermination(1, TimeUnit.DAYS);
        }
    }

    private boolean isTerminated() {
        return events == null ? true : events.isTerminated();
    }

    Condition getCondition() {
        return new ConditionImpl();
    }

    private class ConditionImpl implements Condition {

        @Override
        public void await() throws InterruptedException {
            ServerImpl.this.await();
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
    }
}
