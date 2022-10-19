/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.acteur.pubsub;

import com.mastfrog.marshallers.netty.NettyContentMarshallers;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.mastfrog.util.preconditions.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Implementation of PubSubBus.
 *
 * @author Tim Boudreau
 */
@Singleton
class Bus implements PubSubBus {

    private final ChannelRegistry<ChannelId> reg;
    private final NettyContentMarshallers marshallers;
    private final ByteBufAllocator alloc;
    private final ExecutorService threadPool;
    private final BusListener.Registry listeners;

    @Inject
    Bus(ShutdownHookRegistry shutdown, NettyContentMarshallers marshallers, ByteBufAllocator alloc, @Named("bus") ExecutorService threadPool,
            BusListener.Registry listeners) {
        this.reg = new ChannelRegistry<>(shutdown);
        this.marshallers = marshallers;
        this.alloc = alloc;
        this.threadPool = threadPool;
        this.listeners = listeners;
    }

    @Override
    public Future<Boolean> subscribe(Channel channel, ChannelId to) {
        listeners.onSubscribe(to, channel);
        channel.closeFuture().addListener((ChannelFuture f) -> {
            listeners.onUnsubscribe(to, channel);
        });
        return reg.register(to, channel);
    }

    @Override
    public Future<Boolean> unsubscribe(Channel channel, ChannelId from) {
        listeners.onUnsubscribe(from, channel);
        return reg.unsubscribe(from, channel);
    }

    public boolean hasSubscribers(ChannelId id) {
        return !reg.channels(id).isEmpty();
    }

    @Override
    public <T> ChannelPromise publish(T obj, Channel origin, Set<ChannelId> to) throws Exception {
        Checks.notEmpty("to", to);
        ByteBuf buf = alloc.buffer();
        marshallers.write(obj, buf);
        final BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buf);
        Set<Channel> channels = new HashSet<>(50);
        for (ChannelId id : to) {
            channels.addAll(reg.channels(id));
        }
        channels.remove(origin);
        ChannelPromise p = origin.newPromise();
        if (!channels.isEmpty()) {
            threadPool.submit(() -> {
                new CHF(channels.iterator(), frame, p).operationComplete(null);
            });
        } else {
            p.setSuccess();
        }
        listeners.onPublish(obj, to, origin);
        return p;
    }

    @Override
    public <T> ChannelPromise broadcast(T obj) throws Exception {
        return broadcast(obj, new EmbeddedChannel());
    }

    @Override
    public <T> ChannelPromise broadcast(T obj, Channel origin) throws Exception {
        ChannelPromise p = origin.newPromise();
        Set<Channel> all = new HashSet<>(reg.allChannels());
        all.remove(origin);
        if (all.isEmpty()) {
            return p.setSuccess();
        }
        ByteBuf buf = alloc.buffer();
        marshallers.write(obj, buf);
        final BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buf);
        threadPool.submit(() -> {
            new CHF(all.iterator(), frame, p).operationComplete(null);
        });
        return p;
    }

    static class CHF implements ChannelFutureListener {

        private final Iterator<Channel> channels;
        private final WebSocketFrame frame;
        private final ChannelPromise prom;

        CHF(Iterator<Channel> channels, WebSocketFrame frame, ChannelPromise prom) {
            this.channels = channels;
            this.frame = frame;
            this.prom = prom;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future != null) {
                if (!future.isSuccess()) {
                    prom.setFailure(future.cause());
                    return;
                }
            }
            if (!channels.hasNext() && !prom.isDone()) {
                prom.setSuccess();
                return;
            }
            Channel ch = null;
            while (channels.hasNext()) {
                ch = channels.next();
                if (ch.isWritable()) {
                    break;
                }
            }
            if (ch != null) {
                WebSocketFrame fr = frame.retainedDuplicate();
                future = ch.writeAndFlush(fr).addListener(this);
                if (channels.hasNext()) {
                    future.addListener(this);
                }
            }
            if (!channels.hasNext() && !prom.isDone()) {
                prom.setSuccess();
            }
        }
    }
}
