package com.mastfrog.acteur;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.mastfrog.acteur.util.HeaderValueType;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.url.Path;
import com.mastfrog.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.DefaultHttpMessage;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Tim Boudreau
 */
class MD extends AbstractModule implements Event {

    protected void configure() {
        bind(MD.class).toInstance(this);
    }

    public Optional<Integer> getIntParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            int ival = Integer.parseInt(val);
            return Optional.of(ival);
        }
        return Optional.absent();
    }

    public Optional<Long> getLongParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            long lval = Long.parseLong(val);
            return Optional.of(lval);
        }
        return Optional.absent();
    }

    public Channel getChannel() {
        return new Channel() {
            public Integer id() {
                return 1;
            }

            public EventLoop eventLoop() {
                return null;
            }

            public Channel parent() {
                return this;
            }

            public ChannelConfig config() {
                return null;
            }

            public boolean isOpen() {
                return true;
            }

            public boolean isRegistered() {
                return true;
            }

            public ChannelFuture deregister(ChannelPromise p) {
                // dealing with netty version skew
                return null;
            }

            public boolean isActive() {
                return true;
            }

            public ChannelMetadata metadata() {
                return null;
            }
            private ByteBuf out = Unpooled.buffer();

            public ByteBuf outboundByteBuffer() {
                return out;
            }

            public SocketAddress localAddress() {
                return new InetSocketAddress(1);
            }

            public SocketAddress remoteAddress() {
                return new InetSocketAddress(2);
            }

            public ChannelFuture closeFuture() {
                final Channel t = this;
                return new ChannelFuture() {
                    public Channel channel() {
                        return t;
                    }

                    public boolean isDone() {
                        return true;
                    }

                    public boolean isSuccess() {
                        return true;
                    }

                    public Throwable cause() {
                        return null;
                    }

                    public ChannelFuture sync() throws InterruptedException {
                        return this;
                    }

                    public ChannelFuture syncUninterruptibly() {
                        return this;
                    }

                    public ChannelFuture await() throws InterruptedException {
                        return this;
                    }

                    public ChannelFuture awaitUninterruptibly() {
                        return this;
                    }

                    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
                        return true;
                    }

                    public boolean await(long timeoutMillis) throws InterruptedException {
                        return true;
                    }

                    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
                        return true;
                    }

                    public boolean awaitUninterruptibly(long timeoutMillis) {
                        return true;
                    }

                    public Void getNow() {
                        return null;
                    }

                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return true;
                    }

                    public boolean isCancelled() {
                        return false;
                    }

                    public Void get() throws InterruptedException, ExecutionException {
                        return null;
                    }

                    public Void get(long l, TimeUnit tu) throws InterruptedException, ExecutionException, TimeoutException {
                        return null;
                    }

                    public ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
                        try {
                            GenericFutureListener f = listener;
                            f.operationComplete(this);
                        } catch (Exception ex) {
                            return Exceptions.chuck(ex);
                        }
                        return this;
                    }

                    public ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
                        for (GenericFutureListener<? extends Future<? super Void>> l : listeners) {
                            try {
                                GenericFutureListener f = l;
                                f.operationComplete(this);
                            } catch (Exception ex) {
                                return Exceptions.chuck(ex);
                            }
                        }
                        return this;
                    }

                    public ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
                        return this;
                    }

                    public ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
                        return this;
                    }

                    public boolean isCancellable() {
                        return false;
                    }
                };
            }

            public Channel.Unsafe unsafe() {
                return null;
            }

            public <T> Attribute<T> attr(AttributeKey<T> key) {
                return null;
            }

            public ChannelFuture bind(SocketAddress localAddress) {
                return closeFuture();
            }

            public ChannelFuture connect(SocketAddress remoteAddress) {
                return closeFuture();
            }

            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
                return closeFuture();
            }

            public ChannelFuture disconnect() {
                return closeFuture();
            }

            public ChannelFuture close() {
                return closeFuture();
            }

            public Channel flush() {
                return this;
            }

            public ChannelFuture write(Object message) {
                return closeFuture();
            }

            public ChannelFuture sendFile(FileRegion region) {
                return closeFuture();
            }

            public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture disconnect(ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture close(ChannelPromise promise) {
                return closeFuture();
            }

            public Channel read() {
                return this;
            }

            public ChannelFuture flush(ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture write(Object message, ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelPipeline pipeline() {
                return null;
            }

            public ByteBufAllocator alloc() {
                return null;
            }

            public ChannelPromise newPromise() {
                return null;
            }

            public ChannelFuture newSucceededFuture() {
                return closeFuture();
            }

            public ChannelFuture newFailedFuture(Throwable cause) {
                return closeFuture();
            }

            public int compareTo(Channel t) {
                return 0;
            }

            public boolean isWritable() {
                return true;
            }

            public ChannelProgressivePromise newProgressivePromise() {
                throw new UnsupportedOperationException();
            }

            public ChannelPromise voidPromise() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture writeAndFlush(Object msg) {
                return closeFuture();
            }

            public ChannelFuture deregister() {
                return closeFuture();
            }
        };
    }

    public HttpMessage getRequest() {
        return new DefaultHttpMessage(HttpVersion.HTTP_1_0) {
        };
    }

    public Method getMethod() {
        return Method.GET;
    }

    public SocketAddress getRemoteAddress() {
        return new InetSocketAddress(2);
    }

    public void deregister(ChannelPromise p) {

    }

    public String getHeader(String nm) {
        return null;
    }

    public String getParameter(String param) {
        return null;
    }

    public Path getPath() {
        return Path.parse("testTag");
    }

    public <T> T getHeader(HeaderValueType<T> value) {
        return null;
    }

    public Map<String, String> getParametersAsMap() {
        return Collections.emptyMap();
    }

    public <T> T getParametersAs(Class<T> type) {
        return null;
    }

    public <T> T getContentAsJSON(Class<T> type) throws IOException {
        return null;
    }

    public ByteBuf getContent() throws IOException {
        return Unpooled.buffer();
    }

    public boolean isKeepAlive() {
        return false;
    }

    public OutputStream getContentAsStream() throws IOException {
        return new ByteArrayOutputStream(0);
    }
}
