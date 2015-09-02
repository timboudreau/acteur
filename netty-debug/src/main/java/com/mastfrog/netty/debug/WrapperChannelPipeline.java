/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.netty.debug;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutorGroup;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class WrapperChannelPipeline implements ChannelPipeline {

    private final WrapperChannel channel;
    private final ChannelPipeline real;

    public WrapperChannelPipeline(WrapperChannel channel, ChannelPipeline real) {
        this.channel = channel;
        this.real = real;
    }

    protected void onAdd(ChannelHandler handler) {

    }

    protected <T extends ChannelHandler> T handlerAdded(T handler) {
        if (handler != null) {
            onAdd(handler);
        }
        return handler;
    }

    @Override
    public ChannelPipeline addFirst(String name, ChannelHandler handler) {
        real.addFirst(name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        real.addFirst(group, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandlerInvoker invoker, String name, ChannelHandler handler) {
        real.addFirst(invoker, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        real.addLast(name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        real.addLast(group, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandlerInvoker invoker, String name, ChannelHandler handler) {
        real.addLast(invoker, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        real.addBefore(baseName, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        real.addBefore(group, baseName, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addBefore(ChannelHandlerInvoker invoker, String baseName, String name, ChannelHandler handler) {
        real.addBefore(invoker, baseName, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        real.addAfter(baseName, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        real.addAfter(group, baseName, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addAfter(ChannelHandlerInvoker invoker, String baseName, String name, ChannelHandler handler) {
        real.addAfter(invoker, baseName, name, handler);
        handlerAdded(handler);
        return this;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler... handlers) {
        real.addFirst(handlers);
        for (ChannelHandler handler : handlers) {
            handlerAdded(handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers) {
        real.addFirst(group, handlers);
        for (ChannelHandler handler : handlers) {
            handlerAdded(handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandlerInvoker invoker, ChannelHandler... handlers) {
        real.addFirst(invoker, handlers);
        for (ChannelHandler handler : handlers) {
            handlerAdded(handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        real.addLast(handlers);
        for (ChannelHandler handler : handlers) {
            handlerAdded(handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers) {
        real.addLast(group, handlers);
        for (ChannelHandler handler : handlers) {
            handlerAdded(handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandlerInvoker invoker, ChannelHandler... handlers) {
        real.addLast(invoker, handlers);
        for (ChannelHandler handler : handlers) {
            handlerAdded(handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        real.remove(handler);
        handlerRemoved(handler);
        return this;
    }

    @Override
    public ChannelHandler remove(String name) {
        return handlerRemoved(real.remove(name));
    }

    @Override
    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return handlerRemoved(real.remove(handlerType));
    }

    protected <T extends ChannelHandler> T handlerRemoved(T handler) {
        if (handler != null) {
            onRemove(handler);
        }
        return handler;
    }

    protected void onRemove(ChannelHandler h) {
        //do nothing
    }

    @Override
    public ChannelHandler removeFirst() {
        return handlerRemoved(real.removeFirst());
    }

    @Override
    public ChannelHandler removeLast() {
        return handlerRemoved(real.removeLast());
    }

    @Override
    public ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        real.replace(oldHandler, newName, newHandler);
        handlerRemoved(oldHandler);
        return this;
    }

    @Override
    public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return handlerRemoved(real.replace(oldName, newName, newHandler));
    }

    @Override
    public <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return handlerRemoved(real.replace(oldHandlerType, newName, newHandler));
    }

    @Override
    public ChannelHandler first() {
        return real.first();
    }

    @Override
    public ChannelHandlerContext firstContext() {
        return real.firstContext();
    }

    @Override
    public ChannelHandler last() {
        return real.last();
    }

    @Override
    public ChannelHandlerContext lastContext() {
        return real.lastContext();
    }

    @Override
    public ChannelHandler get(String name) {
        return real.get(name);
    }

    @Override
    public <T extends ChannelHandler> T get(Class<T> handlerType) {
        return real.get(handlerType);
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        return real.context(handler);
    }

    @Override
    public ChannelHandlerContext context(String name) {
        return real.context(name);
    }

    @Override
    public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        return real.context(handlerType);
    }

    @Override
    public Channel channel() {
        return this.channel;
    }

    @Override
    public List<String> names() {
        return real.names();
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        return real.toMap();
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        real.fireChannelRegistered();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
        real.fireChannelUnregistered();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        real.fireChannelActive();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        real.fireChannelInactive();
        return this;
    }

    @Override
    public ChannelPipeline fireExceptionCaught(Throwable cause) {
        real.fireExceptionCaught(cause);
        return this;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered(Object event) {
        real.fireUserEventTriggered(event);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelRead(Object msg) {
        real.fireChannelRead(msg);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
        real.fireChannelReadComplete();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelWritabilityChanged() {
        real.fireChannelWritabilityChanged();
        return this;
    }

    private ChannelFuture wrap(ChannelFuture what) {
        return new WrapChannelFuture(channel, what);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return wrap(real.bind(localAddress));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return wrap(real.connect(remoteAddress));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return wrap(real.connect(remoteAddress, localAddress));
    }

    @Override
    public ChannelFuture disconnect() {
        return wrap(real.disconnect());
    }

    @Override
    public ChannelFuture close() {
        return wrap(real.close());
    }

    @Override
    public ChannelFuture deregister() {
        return wrap(real.deregister());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return wrap(real.bind(localAddress, promise));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return wrap(real.connect(remoteAddress, promise));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return wrap(real.connect(remoteAddress, localAddress, promise));
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return wrap(real.disconnect(promise));
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return wrap(real.close(promise));
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return wrap(real.deregister(promise));
    }

    @Override
    public ChannelPipeline read() {
        real.read();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return wrap(real.write(msg));
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return wrap(real.write(msg, promise));
    }

    @Override
    public ChannelPipeline flush() {
        real.flush();
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return wrap(real.writeAndFlush(msg, promise));
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return wrap(real.writeAndFlush(msg));
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return real.iterator();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Wrapper over " + real + " with: ");
        boolean first = true;
        for (Map.Entry<String, ChannelHandler> e : this) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append(" - ").append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
