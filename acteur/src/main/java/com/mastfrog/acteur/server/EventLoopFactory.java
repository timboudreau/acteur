/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import com.google.inject.ImplementedBy;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.EventLoopFactory.DefaultEventLoopFactory;
import static com.mastfrog.acteur.server.ServerModule.EVENT_THREADS;
import static com.mastfrog.acteur.server.ServerModule.WORKER_THREADS;
import com.mastfrog.giulius.thread.ThreadCount;
import com.mastfrog.settings.Settings;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Factory for event loop groups used by the Netty transport. This interface
 * exists to abstract creation of these such that Netty's native transport
 * implementations can be plugged in by external code.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultEventLoopFactory.class)
public abstract class EventLoopFactory {

    public abstract EventLoopGroup getEventGroup();

    public abstract EventLoopGroup getWorkerGroup();

    protected Class<? extends ServerChannel> channelType() {
        return NioServerSocketChannel.class;
    }

    protected ChannelFactory<? extends ServerChannel> channelFactory() {
        if (channelType() == NioServerSocketChannel.class) {
            return new NioServerChannelFactory(false);
        }
        return null;
    }

    protected ServerBootstrap configureBootstrap(ServerBootstrap bootstrap) {
        bootstrap.group(getEventGroup(), getWorkerGroup());
        ChannelFactory<? extends ServerChannel> channelFactory = channelFactory();
        if (channelFactory == null) {
            try {
                bootstrap.channel(channelType());
            } catch (IllegalStateException ex) {
                // a bootstrap configurer already set it - ok
            }
        } else {
            try {
                bootstrap.channelFactory(channelFactory);
            } catch (IllegalStateException ex) {
                // a bootstrap configurer already set it - ok
            }
        }
        return bootstrap;
    }

    @Singleton
    static final class DefaultEventLoopFactory extends EventLoopFactory {

        private final EventLoopGroup events;
        private final EventLoopGroup workers;
        private final boolean debug;

        @Inject
        DefaultEventLoopFactory(@Named(EVENT_THREADS) Executor eventThreadFactory,
                @Named(EVENT_THREADS) ThreadCount eventThreadCount,
                @Named(WORKER_THREADS) Executor workerThreadFactory,
                @Named(WORKER_THREADS) ThreadCount workerThreadCount,
                Settings settings) {
            debug = settings.getBoolean("channel.debug", false);
            events = new NioEventLoopGroup(eventThreadCount.get(), eventThreadFactory);
            workers = new NioEventLoopGroup(workerThreadCount.get(), workerThreadFactory);
        }

        @Override
        protected ChannelFactory<? extends ServerChannel> channelFactory() {
            return new NioServerChannelFactory(debug);
        }

        @Override
        public EventLoopGroup getEventGroup() {
            return events;
        }

        @Override
        public EventLoopGroup getWorkerGroup() {
            return workers;
        }
    }
}
