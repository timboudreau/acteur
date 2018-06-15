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
package com.mastfrog.acteur;

import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.CONTENT_LENGTH;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_THRESHOLD;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_ENABLED;
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Strings;
import com.mastfrog.util.function.ThrowingConsumer;
import com.mastfrog.util.thread.ResettableCountDownLatch;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedWriteHandler;
import static io.netty.util.CharsetUtil.UTF_8;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Netty's HttpContentEncoder makes some assumptions that we work around - such as that it can
 * safely add a Content-Length: 0 header to responses that may be answered with a series of
 * ByteBufs.  We work around those assumptions;  this test ensures that the workarounds are working.
 *
 * @author Tim Boudreau
 */
public class ConnectionReuseTest {

    private TinyHttpClient client;
    private Dependencies deps;
    private ServerControl ctrl;
    private Receiver receiver = new Receiver();
    private ReuseApp app;
    private String compressedContent;

    @Test
    public void testIt() throws Throwable {
        // Test that multiple un-chunked responses over a single connection can be made
        // without the HttpContentEncoder complaining
        client.request("/hello");
        receiver.assertBody("hello 1");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/hello");
        receiver.assertBody("hello 2");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/hello");
        receiver.assertBody("hello 3");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/hello");
        receiver.assertBody("hello 4");
        client.waitForDone();
        app.rethrowIfThrown();

        // --------------------------------
        // Test that chunked responses work correctly
        client.request("/chunky");
        receiver.assertBody("chunky 1");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/chunky");
        receiver.assertBody("chunky 2");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/chunky");
        receiver.assertBody("chunky 3");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/chunky");
        receiver.assertBody("chunky 4");
        client.waitForDone();
        app.rethrowIfThrown();

        // --------------------------------
        // Test that throwing raw byte bufs down the socket works correctly
        client.request("/notchunky");
        receiver.assertBody("notchunky 1");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/notchunky");
        receiver.assertBody("notchunky 2");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/notchunky");
        receiver.assertBody("notchunky 3");
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/notchunky");
        receiver.assertBody("notchunky 4");
        client.waitForDone();
        app.rethrowIfThrown();

        // --------------------------------
        // Test that compressed chunks work correctly
        client.request("/compressed");
        receiver.assertBody(compressedContent);
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/compressed");
        receiver.assertBody(compressedContent);
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/compressed");
        receiver.assertBody(compressedContent);
        client.waitForDone();
        app.rethrowIfThrown();

        client.request("/compressed");
        receiver.assertBody(compressedContent);
        client.waitForDone();
        app.rethrowIfThrown();
    }

    @Before
    public void startup() throws IOException {
        int port = new com.mastfrog.util.net.PortFinder().findAvailableServerPort();
        Settings settings = Settings.builder().add("port", port)
                .add("neverKeepAlive", false)
                .add(SETTINGS_KEY_CORS_ENABLED, false)
                .add(HTTP_COMPRESSION_THRESHOLD, 1)
                .build();
        ServerModule<ReuseApp> sm = new ServerModule<>(ReuseApp.class, 4, 2, 1);
        deps = new Dependencies(settings, sm);
        ctrl = deps.getInstance(Server.class).start(port);
        app = (ReuseApp) deps.getInstance(Application.class);

        client = new TinyHttpClient(receiver, port);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(i).append(':').append("abcdefghijklmnopqrstuvwxyz012345678910");
        }
        sb.append('\n');
        compressedContent = sb.toString();
    }

    @After
    public void shutdown() throws InterruptedException {
        try {
            if (client != null) {
                client.shutdown();
            }
        } finally {
            try {
                ctrl.shutdown(true);
            } finally {
                deps.shutdown();
            }
        }
    }

    static final class Receiver implements ThrowingConsumer<HttpContent> {

        volatile HttpContent lastResponse;
        volatile int rcount;

        void assertBody(String body) throws InterruptedException {
            for (int i = 0; lastResponse == null && i < 30; i++) {
                synchronized (this) {
                    if (lastResponse == null) {
//                        System.out.println("loop wait " + i);
                        wait(100);
                    }
                }
            }
            try {
                assertNotNull("No response received while waiting for '" + body + "'", lastResponse);
                ByteBuf content = lastResponse.content();
                CharSequence seq = content.readCharSequence(content.readableBytes(), UTF_8);
                assertTrue("Unexpected response body expected '" + body + "' got '" + seq + "'", Strings.contentEqualsIgnoreCase(body, seq));
            } finally {
                lastResponse = null;
            }
        }

        @Override
        public void apply(HttpContent obj) throws Exception {
            lastResponse = obj;
            rcount++;
            synchronized (this) {
                notifyAll();
            }
        }

    }

    // We use a raw netty http client here because we do not want response aggregation
    // or connection reuse handled transparently for us, but to guarantee the behavior
    // we need to test, which is making multiple requests of different types over a single
    // connection
    static final class TinyHttpClient {

        private final EventLoopGroup group = new NioEventLoopGroup();
        private final Set<Channel> channels = new HashSet<>();
        private final Bootstrap bootstrap;
        private final int port;
        private final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);

        TinyHttpClient(ThrowingConsumer<HttpContent> consumer, int port) {
            bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class).handler(new HttpUploadClientInitializer(consumer, latch));
            this.port = port;
        }

        void waitForDone() throws InterruptedException {
            latch.await(200, TimeUnit.MILLISECONDS);
        }

        @SuppressWarnings("deprecation")
        void shutdown() {
            try {
                group.shutdownGracefully().get();
            } catch (Exception ex) {
                ex.printStackTrace();
                group.shutdownNow();
            } finally {
                for (Channel ch : channels) {
                    if (ch.isOpen()) {
                        ch.close();
                    }
                }
            }
        }

        private Channel channel;
        private Throwable thrown;

        public void request(String path) throws InterruptedException {
            if (thrown != null) {
                Exceptions.chuck(thrown);
            }
            latch.reset(1);
            if (channel == null) {
//                System.out.println("open channel");
                channel = bootstrap.connect("localhost", port).sync().channel();
                channel.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        if (f.cause() != null) {
                            f.cause().printStackTrace();
                        }
//                        System.out.println("Channel closed");
                    }
                });
            }
            assertTrue("Keep alive failed.", channel.isOpen());
            HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
            HttpHeaders headers = request.headers();
            headers.set(HttpHeaderNames.HOST, "localhost");
            headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            headers.set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP + "," + HttpHeaderValues.DEFLATE);
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    if (f.cause() != null) {
                        f.cause().printStackTrace();
                        thrown = f.cause();
                    }
//                    System.out.println("message flushed");
                }
            });
        }

        class HttpUploadClientInitializer extends ChannelInitializer<SocketChannel> {

            final ThrowingConsumer<HttpContent> consumer;
            private final ResettableCountDownLatch latch;

            public HttpUploadClientInitializer(ThrowingConsumer<HttpContent> consumer, ResettableCountDownLatch latch) {
                this.consumer = consumer;
                this.latch = latch;
            }

            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("codec", new HttpClientCodec());
                pipeline.addLast("inflater", new HttpContentDecompressor());
                pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
                pipeline.addLast("handler", new HttpClientHandler(consumer, latch));
//                pipeline.addLast("deflater", new HttpContentCompressor());
            }

            volatile HttpHeaders lastHeaders;

            class HttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

                private boolean readingChunks;
                private final ThrowingConsumer<HttpContent> consumer;
                private final ResettableCountDownLatch latch;

                public HttpClientHandler(ThrowingConsumer<HttpContent> consumer, ResettableCountDownLatch latch) {
                    this.consumer = consumer;
                    this.latch = latch;
                }

                @Override
                public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                    if (msg instanceof HttpResponse) {
                        HttpResponse response = (HttpResponse) msg;
                        lastHeaders = response.headers();
                        if (response.status().code() == 200 && HttpUtil.isTransferEncodingChunked(response)) {
                            readingChunks = true;
                        }
                    }
                    if (msg instanceof HttpContent) {
                        HttpContent chunk = (HttpContent) msg;
                        if (chunk instanceof LastHttpContent) {
                            readingChunks = false;
                            if (chunk.content().readableBytes() > 0) {
                                consumer.apply(chunk.copy());
                            }
                            latch.countDown();
                        } else {
                            consumer.apply(chunk.copy());
                        }
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    thrown = cause;
//                    cause.printStackTrace();
                    ctx.channel().close();
                }
            }
        }
    }

    static final class ReuseApp extends Application {

        private Throwable lastThrown;

        ReuseApp() {
            add(ReusePage.class);
            add(ChunkyPage.class);
            add(NotChunkyPage.class);
            add(CompressableChunkyPage.class);
        }

        void rethrowIfThrown() {
            Throwable th = lastThrown;
            lastThrown = null;
            if (th != null) {
                Exceptions.chuck(new IOException("Server-side exception", th));
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onError(Throwable err) {
            lastThrown = err;
            err.printStackTrace();
        }

        @Methods(GET)
        @Path("/hello")
        static final class ReusePage extends Page {

            ReusePage() {
                add(ReuseActeur.class);
            }

            static final class ReuseActeur extends Acteur {

                static int count = 0;

                ReuseActeur() {
                    ok("hello " + ++count);
                    add(Headers.CONNECTION, Connection.keep_alive);
                }
            }
        }
    }

    @Methods(GET)
    @Path("/chunky")
    static final class ChunkyPage extends Page {

        ChunkyPage() {
            add(ChunkyReuseActeur.class);
        }

        static final class ChunkyReuseActeur extends Acteur implements ChannelFutureListener {

            static int count;

            int loops;

            ChunkyReuseActeur() {
                add(Headers.CONNECTION, Connection.keep_alive);
                setChunked(true);
                setResponseBodyWriter(this);
                ok();
            }

            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (f.cause() != null) {
                    f.cause().printStackTrace();
                }
                Channel channel = f.channel();
                loops++;
                switch (loops) {
                    case 1:
                        ByteBuf buf = channel.alloc().buffer(7);
                        buf.writeCharSequence("chunky " + ++count, UTF_8);
                        DefaultHttpContent ct = new DefaultHttpContent(buf);
                        channel.writeAndFlush(ct).addListener(this);
                        break;
                    case 2:
                        channel.writeAndFlush(new DefaultLastHttpContent()).addListener(this);
                        break;
                    default:
//                            System.out.println("server channel idle on " + loops);
                        break;
                }
            }
        }
    }

    @Methods(GET)
    @Path("/notchunky")
    static final class NotChunkyPage extends Page {

        NotChunkyPage() {
            add(NotChunkyReuseActeur.class);
        }

        static final class NotChunkyReuseActeur extends Acteur implements ChannelFutureListener {

            static int count;

            int loops;
            byte[] bytes;

            NotChunkyReuseActeur() {
                add(Headers.CONNECTION, Connection.keep_alive);
                setChunked(false);
                bytes = ("notchunky " + ++count).getBytes(UTF_8);
                add(CONTENT_LENGTH, bytes.length);
                setResponseBodyWriter(this);
                ok();
            }

            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (f.cause() != null) {
                    f.cause().printStackTrace();
                }
                Channel channel = f.channel();
                loops++;
                switch (loops) {
                    case 1:
                        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                        channel.writeAndFlush(buf).addListener(this);
//                        System.out.println("FLUSH NOTCHUNKY " + loops + " - " + new String(bytes, UTF_8));
                        break;
                    default:
//                        System.out.println("server channel idle on " + loops);
                        break;
                }
            }
        }
    }

    @Methods(GET)
    @Path("/compressed")
    static final class CompressableChunkyPage extends Page {

        CompressableChunkyPage() {
            add(CompressableChunkyReuseActeur.class);
        }

        static final class CompressableChunkyReuseActeur extends Acteur implements ChannelFutureListener {

            static int count;

            int loops;

            CompressableChunkyReuseActeur() {
                add(Headers.CONNECTION, Connection.keep_alive);
                setChunked(true);
                setResponseBodyWriter(this);
                ok();
            }

            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (f.cause() != null) {
                    f.cause().printStackTrace();
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 100; i++) {
                    sb.append(i).append(':').append("abcdefghijklmnopqrstuvwxyz012345678910");
                }
                sb.append('\n');
                Channel channel = f.channel();
                loops++;
                switch (loops) {
                    case 1:
                        ByteBuf buf = channel.alloc().buffer(sb.length());
                        buf.writeCharSequence(sb.toString(), UTF_8);
                        DefaultHttpContent ct = new DefaultHttpContent(buf);
                        channel.writeAndFlush(ct).addListener(this);
                        break;
                    case 2:
                        channel.writeAndFlush(new DefaultLastHttpContent()).addListener(this);
                        break;
                    default:
//                            System.out.println("server channel idle on " + loops);
                        break;
                }
            }
        }
    }
}
