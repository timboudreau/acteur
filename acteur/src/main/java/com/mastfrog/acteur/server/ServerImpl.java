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
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.mastfrog.settings.Settings;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class ServerImpl implements Server {

    private final ExecutorService workerThreadPool;
    private final ExecutorService backgroundThreadPool;
    private final ChannelInitializer<SocketChannel> pipelineFactory;
    private int port = 8123;
    private final ThreadFactory eventThreadFactory;
    private final ThreadCount eventThreadCount;
    private final ThreadFactory workerThreadFactory;
    private final ThreadCount workerThreadCount;
    private final ServerBootstrap bootstrap;

    @Inject
    ServerImpl(@Named(ServerImpl.WORKER_THREAD_POOL_NAME) ExecutorService workerThreadPool,
            @Named(ServerImpl.BACKGROUND_THREAD_POOL_NAME) ExecutorService backgroundThreadPool,
            ChannelInitializer<SocketChannel> pipelineFactory,
            @Named("event") ThreadFactory eventThreadFactory, 
            @Named("event") ThreadCount eventThreadCount,
            @Named("workers") ThreadFactory workerThreadFactory, 
            @Named("workers") ThreadCount workerThreadCount,
            ServerBootstrap bootstrap,
            Settings settings) {
        this.port = settings.getInt("port", 8123);
        this.workerThreadPool = workerThreadPool;
        this.backgroundThreadPool = backgroundThreadPool;
        this.pipelineFactory = pipelineFactory;
        this.eventThreadFactory = eventThreadFactory;
        this.eventThreadCount = eventThreadCount;
        this.workerThreadFactory = workerThreadFactory;
        this.workerThreadCount = workerThreadCount;
        this.bootstrap = bootstrap;
    }
    
    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return super.toString() + " on port " + port;
    }
    private Channel localChannel;

    @Override
    public Condition start(int port) throws IOException {
        this.port = port;
        return start();
    }
    
    public Condition start() throws IOException {
        try {

            NioEventLoopGroup events = new NioEventLoopGroup(eventThreadCount.get(), eventThreadFactory);
            NioEventLoopGroup workers = new NioEventLoopGroup(workerThreadCount.get(), workerThreadFactory);

            bootstrap.group(events, workers)
             .channel(NioServerSocketChannel.class)
             .childHandler(pipelineFactory)
             .localAddress(new InetSocketAddress(port));
            
            localChannel = bootstrap.bind().sync().channel();
            System.err.println("Starting " + this);

            // Bind and start to accept incoming connections.
            return getCondition();
        } catch (InterruptedException ex) {
            throw new Error(ex);
        }
    }

    public void shutdown(boolean immediately, long timeout, TimeUnit unit) throws InterruptedException {
        shutdown(immediately, timeout, unit, true);
    }
    
    private void shutdownThreadPool(ExecutorService threadPool, boolean immediately) {
        if (immediately) {
            threadPool.shutdownNow();
        } else {
            threadPool.shutdown();
        }
    }

    private void shutdown(boolean immediately, long timeout, TimeUnit unit, boolean await) throws InterruptedException {
        shutdownThreadPool (workerThreadPool, immediately);
        shutdownThreadPool (backgroundThreadPool, immediately);
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
            if (await) {
                workerThreadPool.awaitTermination(timeout, unit);
                backgroundThreadPool.awaitTermination(timeout, unit);
            }
        }
    }

    @Override
    public void shutdown(boolean immediately) throws InterruptedException {
        if (immediately) {
            workerThreadPool.shutdownNow();
            backgroundThreadPool.shutdownNow();
        } else {
            workerThreadPool.shutdown();
            backgroundThreadPool.shutdown();
        }
        try {
            if (localChannel != null) {
                if (localChannel.isOpen()) {
                    localChannel.close().awaitUninterruptibly();
                }
            }
        } finally {
            await();
        }
    }

    @Override
    public void await() throws InterruptedException {
        while (!workerThreadPool.isTerminated()) {
            workerThreadPool.awaitTermination(1, TimeUnit.SECONDS);
        }
        while (!backgroundThreadPool.isTerminated()) {
            backgroundThreadPool.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private boolean isTerminated() {
        return workerThreadPool.isTerminated();
    }

    @Override
    public Condition getCondition() {
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
            if (!isTerminated()) {
                workerThreadPool.awaitTermination(nanosTimeout, TimeUnit.NANOSECONDS);
            }
            return 0;
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            workerThreadPool.awaitTermination(time, unit);
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
