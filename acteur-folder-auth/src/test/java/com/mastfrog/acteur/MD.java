package com.mastfrog.acteur;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.mastfrog.acteur.Event;
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
import io.netty.channel.MessageList;
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

    @Override
    protected void configure() {
        bind(MD.class).toInstance(this);
    }

    @Override
    public Optional<Integer> getIntParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            int ival = Integer.parseInt(val);
            return Optional.of(ival);
        }
        return Optional.absent();
    }

    @Override
    public Optional<Long> getLongParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            long lval = Long.parseLong(val);
            return Optional.of(lval);
        }
        return Optional.absent();
    }

    @Override
    public Channel getChannel() {
        return new Channel() {
            @Override
            public Integer id() {
                return 1;
            }

            @Override
            public EventLoop eventLoop() {
                return null;
            }

            @Override
            public Channel parent() {
                return this;
            }

            @Override
            public ChannelConfig config() {
                return null;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isRegistered() {
                return true;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public ChannelMetadata metadata() {
                return null;
            }
            private ByteBuf out = Unpooled.buffer();

            public ByteBuf outboundByteBuffer() {
                return out;
            }

            @Override
            public SocketAddress localAddress() {
                return new InetSocketAddress(1);
            }

            @Override
            public SocketAddress remoteAddress() {
                return new InetSocketAddress(2);
            }

            @Override
            public ChannelFuture closeFuture() {
                final Channel t = this;
                return new ChannelFuture() {
                    @Override
                    public Channel channel() {
                        return t;
                    }

                    @Override
                    public boolean isDone() {
                        return true;
                    }

                    @Override
                    public boolean isSuccess() {
                        return true;
                    }

                    @Override
                    public Throwable cause() {
                        return null;
                    }

                    @Override
                    public ChannelFuture sync() throws InterruptedException {
                        return this;
                    }

                    @Override
                    public ChannelFuture syncUninterruptibly() {
                        return this;
                    }

                    @Override
                    public ChannelFuture await() throws InterruptedException {
                        return this;
                    }

                    @Override
                    public ChannelFuture awaitUninterruptibly() {
                        return this;
                    }

                    @Override
                    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
                        return true;
                    }

                    @Override
                    public boolean await(long timeoutMillis) throws InterruptedException {
                        return true;
                    }

                    @Override
                    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
                        return true;
                    }

                    @Override
                    public boolean awaitUninterruptibly(long timeoutMillis) {
                        return true;
                    }

                    @Override
                    public Void getNow() {
                        return null;
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return true;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public Void get() throws InterruptedException, ExecutionException {
                        return null;
                    }

                    @Override
                    public Void get(long l, TimeUnit tu) throws InterruptedException, ExecutionException, TimeoutException {
                        return null;
                    }

                    @Override
                    public ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
                        try {
                            GenericFutureListener f = listener;
                            f.operationComplete(this);
                        } catch (Exception ex) {
                            return Exceptions.chuck(ex);
                        }
                        return this;
                    }

                    @Override
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

                    @Override
                    public ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
                        return this;
                    }

                    @Override
                    public ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
                        return this;
                    }

                    @Override
                    public boolean isCancellable() {
                        return false;
                    }
                };
            }

            @Override
            public Channel.Unsafe unsafe() {
                return null;
            }

            @Override
            public <T> Attribute<T> attr(AttributeKey<T> key) {
                return null;
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
                return closeFuture();
            }

            @Override
            public ChannelFuture disconnect() {
                return closeFuture();
            }

            @Override
            public ChannelFuture close() {
                return closeFuture();
            }

            @Override
            public ChannelFuture deregister() {
                return closeFuture();
            }

            public ChannelFuture flush() {
                return closeFuture();
            }

            @Override
            public ChannelFuture write(Object message) {
                return closeFuture();
            }

            public ChannelFuture sendFile(FileRegion region) {
                return closeFuture();
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture disconnect(ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture close(ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture deregister(ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public void read() {
            }

            public ChannelFuture flush(ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture write(Object message, ChannelPromise promise) {
                return closeFuture();
            }

            public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelPipeline pipeline() {
                return null;
            }

            @Override
            public ByteBufAllocator alloc() {
                return null;
            }

            @Override
            public ChannelPromise newPromise() {
                return null;
            }

            @Override
            public ChannelFuture newSucceededFuture() {
                return closeFuture();
            }

            @Override
            public ChannelFuture newFailedFuture(Throwable cause) {
                return closeFuture();
            }

            @Override
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

            @Override
            public ChannelFuture write(MessageList<?> msgs) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ChannelFuture write(MessageList<?> msgs, ChannelPromise promise) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        };
    }

    @Override
    public HttpMessage getRequest() {
        return new DefaultHttpMessage(HttpVersion.HTTP_1_0) {
        };
    }

    @Override
    public Method getMethod() {
        return Method.GET;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return new InetSocketAddress(2);
    }

    @Override
    public String getHeader(String nm) {
        return null;
    }

    @Override
    public String getParameter(String param) {
        return null;
    }

    @Override
    public Path getPath() {
        return Path.parse("testTag");
    }

    @Override
    public <T> T getHeader(HeaderValueType<T> value) {
        return null;
    }

    @Override
    public Map<String, String> getParametersAsMap() {
        return Collections.emptyMap();
    }

    @Override
    public <T> T getParametersAs(Class<T> type) {
        return null;
    }

    @Override
    public <T> T getContentAsJSON(Class<T> type) throws IOException {
        return null;
    }

    @Override
    public ByteBuf getContent() throws IOException {
        return Unpooled.buffer();
    }

    @Override
    public boolean isKeepAlive() {
        return false;
    }

    @Override
    public OutputStream getContentAsStream() throws IOException {
        return new ByteArrayOutputStream(0);
    }
}
