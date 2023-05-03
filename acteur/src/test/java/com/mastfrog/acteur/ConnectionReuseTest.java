package com.mastfrog.acteur;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.header.entities.Connection;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.CONTENT_LENGTH;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_THRESHOLD;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_ENABLED;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.thread.OneThreadLatch;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
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
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Netty's HttpContentEncoder makes some assumptions that we work around - such
 * as that it can safely add a Content-Length: 0 header to responses that may be
 * answered with a series of ByteBufs. We work around those assumptions; this
 * test ensures that the workarounds are working.
 *
 * @author Tim Boudreau
 */
public class ConnectionReuseTest {

    private String compressedContent;
    private static final com.mastfrog.util.net.PortFinder FINDER
            = new com.mastfrog.util.net.PortFinder();

    @Test
    public void testMulti() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {
            // Test slamming through a mix of chunked, not chunked,
            // compressed and not compressed
            client.request("/ok");
            receiver.assertNoBody();
            receiver.assertHeader(HttpHeaderNames.CONTENT_LENGTH, "0");
            app.rethrowIfThrown();

            client.request("/notmodifiedchunky");
//        receiver.assertHeader(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED.toString());
//        receiver.assertNoHeader(HttpHeaderNames.CONTENT_LENGTH);
//        receiver.assertHeader(HttpHeaderNames.CONTENT_LENGTH, "0");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/ok");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/compressed");
            receiver.assertBody(compressedContent);
            app.rethrowIfThrown();

            client.request("/ok");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notsochunky");
            receiver.assertHasBody();
            app.rethrowIfThrown();

            client.request("/notmodified");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notsochunky");
            receiver.assertHasBody();
            app.rethrowIfThrown();

            client.request("/notmodifiedchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/chunky");
            receiver.assertHasBody();
            app.rethrowIfThrown();

            client.request("/notsochunky");
            receiver.assertHasBody();
            app.rethrowIfThrown();

            client.request("/chunky");
            receiver.assertHasBody();
            app.rethrowIfThrown();

//        client.request("/notchunky");
//        receiver.assertNoBody();
//        app.rethrowIfThrown();
            client.request("/notmodified");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/doesntexist");
            receiver.assertHasBody();
            app.rethrowIfThrown();

            client.request("/notmodifiedchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/chunky");
            receiver.assertHasBody();
            app.rethrowIfThrown();

            client.request("/compressed");
            receiver.assertBody(compressedContent);
            app.rethrowIfThrown();

            client.request("/okchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/compressed");
            receiver.assertBody(compressedContent);
            app.rethrowIfThrown();

            client.request("/notmodified");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/compressed");
            receiver.assertBody(compressedContent);
            app.rethrowIfThrown();

            client.request("/ok");
            receiver.assertNoBody();
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testHello() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {
            // Test that multiple un-chunked responses over a single connection can be made
            // without the HttpContentEncoder complaining
            client.request("/hello");
            receiver.assertBody("hello 1");
            app.rethrowIfThrown();

            client.request("/hello");
            receiver.assertBody("hello 2");
            app.rethrowIfThrown();

            client.request("/hello");
            receiver.assertBody("hello 3");
            app.rethrowIfThrown();

            client.request("/hello");
            receiver.assertBody("hello 4");
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testChunky() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {
            // --------------------------------
            // Test that chunked responses work correctly
            client.request("/chunky");
            receiver.assertBody("chunky 1");
            app.rethrowIfThrown();

            client.request("/chunky");
            receiver.assertBody("chunky 2");
            app.rethrowIfThrown();

            client.request("/chunky");
            receiver.assertBody("chunky 3");
            app.rethrowIfThrown();

            client.request("/chunky");
            receiver.assertBody("chunky 4");
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testNotChunky() throws Throwable {
        if (true) {
            return;
        }
        withRunningServer((port, ctrl, app, receiver, client) -> {

            // --------------------------------
            // Test that throwing raw byte bufs down the socket works correctly
            client.request("/notchunky");
            receiver.assertBody("notchunky 1");
            app.rethrowIfThrown();

            client.request("/notchunky");
            receiver.assertBody("notchunky 2");
            app.rethrowIfThrown();

            client.request("/notchunky");
            receiver.assertBody("notchunky 3");
            app.rethrowIfThrown();

            client.request("/notchunky");
            receiver.assertBody("notchunky 4");
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testCompressed() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {

            // --------------------------------
            // Test that compressed chunks work correctly
            client.request("/compressed");
            receiver.assertBody(compressedContent);
//        receiver.assertHeader("X-Compressed", "1");
            app.rethrowIfThrown();

            client.request("/compressed");
            receiver.assertBody(compressedContent);
//        receiver.assertHeader("X-Compressed", "1");
            app.rethrowIfThrown();

            client.request("/compressed");
            receiver.assertBody(compressedContent);
//        receiver.assertHeader("X-Compressed", "1");
            app.rethrowIfThrown();

            client.request("/compressed");
            receiver.assertBody(compressedContent);
//        receiver.assertHeader("X-Compressed", "1");
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testOk() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {
            // --------------------------------
            // Test that compressed chunks work correctly
            client.request("/ok");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/ok");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/ok");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/ok");
            receiver.assertNoBody();
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testNotModified() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {
            // --------------------------------
            // Test that compressed chunks work correctly
            client.request("/notmodified");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notmodified");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notmodified");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notmodified");
            receiver.assertNoBody();
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testOkChunky() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {
            // --------------------------------
            // Test that compressed chunks work correctly
            client.request("/okchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/okchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/okchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/okchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();
        });
    }

    @Test
    public void testNotModifiedChunky() throws Throwable {
        withRunningServer((port, ctrl, app, receiver, client) -> {
            // --------------------------------
            // Test that compressed chunks work correctly
            client.request("/notmodifiedchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notmodifiedchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notmodifiedchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();

            client.request("/notmodifiedchunky");
            receiver.assertNoBody();
            app.rethrowIfThrown();
        });
    }

    interface WithRunningServer {

        void go(int port, ServerControl ctrl, ReuseApp app, Receiver receiver, TinyHttpClient client) throws Throwable;
    }

    static final ExecutorService SHUTDOWN = Executors.newSingleThreadExecutor();

    static void withRunningServer(WithRunningServer wr) throws Exception, Throwable {
        // Provide a completely separate environment for each test method,
        // so calls cannot interfere with each other - the previous iteration relied
        // on static counters, and so would fail unexpectedly depending on the
        // parallel test settings and number of cores.
        ThrowingRunnable all = ThrowingRunnable.oneShot(true);
        try {
            int port = FINDER.findAvailableServerPort();
            Settings settings = Settings.builder().add("port", port)
                    .add("neverKeepAlive", false)
                    //                .add("channel.debug", true)
                    .add(SETTINGS_KEY_CORS_ENABLED, false)
                    .add(HTTP_COMPRESSION_THRESHOLD, 5)
                    .build();
            ServerModule<ReuseApp> sm = new ServerModule<>(ReuseApp.class, 4, 2, 1);
            Dependencies deps = new Dependencies(settings, sm, new SilentRequestLogger(), new AbstractModule() {
                @Override
                protected void configure() {
                    bind(ErrorInterceptor.class).to(EI.class);
                    bind(AtomicInteger.class).annotatedWith(Names.named("count")).toInstance(new AtomicInteger());
                    bind(AtomicInteger.class).annotatedWith(Names.named("nc-count")).toInstance(new AtomicInteger());
                }
            });
            ServerControl ctrl = deps.getInstance(Server.class).start(port);
            ReuseApp app = (ReuseApp) deps.getInstance(Application.class);
            all.andAlways(() -> ctrl.shutdown(true));
            all.andAlways(deps::shutdown);
            Receiver receiver = new Receiver();
            TinyHttpClient client = new TinyHttpClient(receiver, port);
            all.andAlways(client::shutdown);
            wr.go(port, ctrl, app, receiver, client);
        } finally {
            // The slowest part of the test is waiting for executors to terminate,
            // so shuffle that off to a background thread.
            SHUTDOWN.submit(all.toNonThrowing());
        }
    }

    @BeforeEach
    public void startup() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(i).append(':').append("abcdefghijklmnopqrstuvwxyz012345678910");
        }
        sb.append('\n');
        compressedContent = sb.toString();
    }

    @AfterAll
    public static void shutdown() {
        SHUTDOWN.shutdown();
    }

    static final class Receiver implements ThrowingBiConsumer<HttpResponse, HttpContent> {

        volatile HttpContent lastContent;
        volatile int rcount;
        volatile HttpResponse resp;

        String headersString() {
            StringBuilder sb = new StringBuilder();
            if (resp != null) {
                for (Map.Entry<String, String> e : resp.headers().entries()) {
                    sb.append('\n').append(e.getKey()).append(": ").append(e.getValue());
                }
            }
            return sb.toString();
        }

        HttpContent awaitContent() throws InterruptedException {
            for (int i = 0; lastContent == null && i < 300; i++) {
                if (lastContent != null) {
                    break;
                }
                synchronized (this) {
                    if (lastContent == null) {
//                        System.out.println("loop wait " + i);
                        wait(50);
                    } else {
                        return lastContent;
                    }
                }
            }
            return lastContent;
        }

        void assertNoHeader(CharSequence name) {
            assertNotNull(resp, "No response");
            String found = resp.headers().get(name);
            assertNull("Unexpected header " + name + " in " + headersString(), found);
        }

        void assertHeader(CharSequence name, String val) {
            assertNotNull(resp, "No response");
            String found = resp.headers().get(name);
            assertNotNull("No " + name + " header present in " + headersString(), found);
            assertEquals(val, found);
        }

        void assertNoBody() throws InterruptedException {
            HttpContent lastContent = awaitContent();
            try {
                assertNotNull(lastContent, "No response received while waiting for empty response");
                ByteBuf content = lastContent.content();
                assertTrue(content.readableBytes() == 0, content.readableBytes() + " bytes available: " + content.toString(UTF_8));
            } finally {
                synchronized (this) {
                    this.lastContent = null;
                }
            }
        }

        void assertHasBody() throws InterruptedException {
            HttpContent lastContent = awaitContent();
            try {
                assertNotNull(lastContent, "No response received while waiting for empty response");
                ByteBuf content = lastContent.content();
                assertNotNull(content);
                assertTrue(content.readableBytes() != 0);
            } finally {
                synchronized (this) {
                    this.lastContent = null;
                }
            }

        }

        void assertBody(String body) throws InterruptedException {
            HttpContent lastContent = awaitContent();
            try {
                assertNotNull(lastContent, "No response received while waiting for '" + body + "'");
                ByteBuf content = lastContent.content();
                CharSequence seq = content.readCharSequence(content.readableBytes(), UTF_8);
                assertNotNull(seq, "No body");
                if (!Strings.contentEqualsIgnoreCase(body, seq)) {
                    int max = Math.min(body.length(), seq.length());
                    String diff = "No character differences through character " + max;
                    for (int i = 0; i < max; i++) {
                        char a = body.charAt(i);
                        char b = seq.charAt(i);
                        if (a != b) {
                            diff = "First character difference at position " + i + " expected '" + a + "' got '" + b + "'";
                        }
                    }
                    if (body.length() != seq.length()) {
                        diff += "; lengths differ - expected " + body.length() + " got " + seq.length();
                    }
                    fail(diff + "; unexpected response body expected '" + body + "' got '" + seq + "'");
                }

            } finally {
                synchronized (this) {
                    this.lastContent = null;
                }
            }
        }

        @Override
        public void accept(HttpResponse resp, HttpContent obj) throws Exception {
            if (resp != null) {
                this.resp = resp;
//                System.out.println(headersString());
                if (resp instanceof FullHttpResponse) {
                    obj = new DefaultLastHttpContent(((FullHttpResponse) resp).content().duplicate().retain());
                }
            }
            if (obj == null) {
                return;
            }
            synchronized (this) {
                rcount++;
                if (lastContent == null) {
                    lastContent = obj;
                } else {
                    CompositeByteBuf comp = Unpooled.compositeBuffer(2);
                    ByteBuf a = lastContent.content();
                    a.readerIndex(0);
                    ByteBuf b = obj.content();
                    b.readerIndex(0);
                    comp.addComponent(a);
                    comp.addComponent(b);
                    comp.writerIndex(a.writerIndex() + b.writerIndex());
                    lastContent = new DefaultHttpContent(comp);
                }
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

        TinyHttpClient(ThrowingBiConsumer<HttpResponse, HttpContent> consumer, int port) {
            bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class).handler(new HttpUploadClientInitializer(consumer));
            this.port = port;
        }

        @SuppressWarnings("deprecation")
        void shutdown() {
            try {
                group.shutdownGracefully().get();
            } catch (InterruptedException | ExecutionException ex) {
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

        static final AttributeKey<OneThreadLatch> latchKey = AttributeKey.valueOf(OneThreadLatch.class, "client");

        public OneThreadLatch request(String path) throws InterruptedException {
            if (thrown != null) {
                Exceptions.chuck(thrown);
            }
//            System.out.println("\n\n****************\nrequest " + path);
            OneThreadLatch latch = new OneThreadLatch();
            if (channel == null) {
//                System.out.println("open channel");
                channel = bootstrap.connect("localhost", port).sync().channel();
                channel.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        if (f.cause() != null) {
                            f.cause().printStackTrace();
                        }
                        latch.releaseAll();
//                        System.out.println("Channel closed");
                    }
                });
            }
            channel.attr(latchKey).set(latch);

//            assertTrue("Keep alive failed.", channel.isOpen());
            assertTrue(channel.isWritable(), "Keep alive failed.");
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
                        latch.releaseAll();
                    }
//                    System.out.println("message flushed");
                }
            });
            latch.await(500, TimeUnit.MILLISECONDS);
            return latch;
        }

        class HttpUploadClientInitializer extends ChannelInitializer<SocketChannel> {

            final ThrowingBiConsumer<HttpResponse, HttpContent> consumer;

            HttpUploadClientInitializer(ThrowingBiConsumer<HttpResponse, HttpContent> consumer) {
                this.consumer = consumer;
            }

            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("codec", new HttpClientCodec());
                pipeline.addLast("inflater", new HttpContentDecompressor());
                pipeline.addLast("agg", new HttpObjectAggregator(16_384));
                pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
                pipeline.addLast("handler", new HttpClientHandler(consumer));
//                pipeline.addLast("deflater", new HttpContentCompressor());
            }

            volatile HttpHeaders lastHeaders;

            class HttpClientHandler extends SimpleChannelInboundHandler<Object> {

                private boolean readingChunks;
                private final ThrowingBiConsumer<HttpResponse, HttpContent> consumer;
                HttpResponse lastResponse;

                HttpClientHandler(ThrowingBiConsumer<HttpResponse, HttpContent> consumer) {
                    this.consumer = consumer;
                }

                @Override
                public boolean acceptInboundMessage(Object msg) throws Exception {
                    return true;
                }

                @Override
                public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg instanceof HttpResponse) {
                        HttpResponse response = (HttpResponse) msg;
                        lastResponse = response;
                        lastHeaders = response.headers();
                        if (response.status().code() == 200 && HttpUtil.isTransferEncodingChunked(response)) {
                            readingChunks = true;
                        }
                        if (msg instanceof FullHttpResponse) {
                            msg = new DefaultLastHttpContent(((FullHttpResponse) msg).content());
                        }
                    }
                    if (msg instanceof HttpContent) {
                        HttpContent chunk = (HttpContent) msg;
                        if (chunk instanceof LastHttpContent) {
                            readingChunks = false;
                            consumer.accept(lastResponse, chunk.duplicate());
                            OneThreadLatch latch = ctx.channel().attr(latchKey).get();
                            latch.releaseAll();
                        } else {
                            consumer.accept(lastResponse, chunk.duplicate());
                        }
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    thrown = cause;
                    cause.printStackTrace();
                    ctx.channel().close();
                }
            }
        }
    }

    @Singleton
    static final class EI implements ErrorInterceptor {

        private final Application app;

        @Inject
        EI(Application app) {
            this.app = app;
        }

        @Override
        public void onError(Throwable thrown) {
            ((ReuseApp) app).onError(thrown);
        }
    }

    @Singleton
    static final class ReuseApp extends Application {

        Throwable lastThrown;

        ReuseApp() {
            add(ReusePage.class);
            add(ChunkyPage.class);
            add(NotChunkyPage.class);
            add(NotSoChunkyPage.class);
            add(CompressableChunkyPage.class);
            add(NotModified.class);
            add(OkEmpty.class);
            add(NotModifiedChunky.class);
            add(OkEmptyChunky.class);
            add(NotSoChunkyPage.class);
        }

        void rethrowIfThrown() {
            Throwable th = lastThrown;
            lastThrown = null;
            if (th != null) {
                Exceptions.chuck(new IOException("Server-side exception", th));
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                Logger.getLogger(ConnectionReuseTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onError(Throwable err) {
            lastThrown = err;
            err.printStackTrace();
        }

        @Methods(GET)
        @Path("/notmodified")
        static final class NotModified extends Page {

            NotModified() {
                add(NotModifiedActeur.class);
            }

            static final class NotModifiedActeur extends Acteur {

                NotModifiedActeur() {
                    reply(NOT_MODIFIED);
                }
            }
        }

        @Methods(GET)
        @Path("/ok")
        static final class OkEmpty extends Page {

            OkEmpty() {
                add(OkActeur.class);
            }

            static final class OkActeur extends Acteur {

                OkActeur() {
                    add(Headers.CONNECTION, Connection.keep_alive);
                    ok();
                }
            }
        }

        @Methods(GET)
        @Path("/notmodifiedchunky")
        static final class NotModifiedChunky extends Page {

            NotModifiedChunky() {
                add(NotModifiedChunkyActeur.class);
            }

            static final class NotModifiedChunkyActeur extends Acteur {

                NotModifiedChunkyActeur() {
                    add(Headers.CONNECTION, Connection.keep_alive);
                    setChunked(true);
                    reply(NOT_MODIFIED);
                }
            }
        }

        @Methods(GET)
        @Path("/okchunky")
        static final class OkEmptyChunky extends Page {

            OkEmptyChunky() {
                add(OkChunkyActeur.class);
            }

            static final class OkChunkyActeur extends Acteur {

                OkChunkyActeur() {
                    add(Headers.CONNECTION, Connection.keep_alive);
                    setChunked(true);
                    ok();
                }
            }
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

            int loops;
            private final AtomicInteger count;

            @Inject
            ChunkyReuseActeur(@Named("count") AtomicInteger count) {
                this.count = count;
                add(Headers.CONNECTION, Connection.keep_alive);
                setChunked(true);
                setResponseBodyWriter(this);
                ok();
            }

            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (f.cause() != null) {
                    f.cause().printStackTrace();
                    return;
                }
                Channel channel = f.channel();
                loops++;
                switch (loops) {
                    case 1:
                        ByteBuf buf = channel.alloc().buffer(7);
                        buf.writeCharSequence("chunky " + count.incrementAndGet(), UTF_8);
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

            @Inject
            NotChunkyReuseActeur(@Named("nc-count") AtomicInteger count) {
                add(Headers.CONNECTION, Connection.keep_alive);
                setChunked(false);
                bytes = ("notchunky " + count.incrementAndGet()).getBytes(UTF_8);
                add(CONTENT_LENGTH, bytes.length);
                setResponseBodyWriter(this);
                ok();
            }

            @SuppressWarnings("SwitchStatementWithTooFewBranches")
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
    @Path("/notsochunky")
    static final class NotSoChunkyPage extends Page {

        NotSoChunkyPage() {
            add(NotSoChunkyReuseActeur.class);
        }

        static final class NotSoChunkyReuseActeur extends Acteur {

            static int count;

            int loops;
            byte[] bytes;

            NotSoChunkyReuseActeur() {
                add(Headers.CONNECTION, Connection.keep_alive);
                setChunked(false);
                ok("notsochunky " + ++count);
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
                    if (loops == 2) {
                        return;
                    }
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
