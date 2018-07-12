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

import com.google.inject.Provider;
import com.mastfrog.acteur.Application;
import static com.mastfrog.acteur.server.ServerModule.DEFAULT_COMPRESSION_LEVEL;
import static com.mastfrog.acteur.server.ServerModule.DEFAULT_COMPRESSION_MEMORY_LEVEL;
import static com.mastfrog.acteur.server.ServerModule.DEFAULT_COMPRESSION_THRESHOLD;
import static com.mastfrog.acteur.server.ServerModule.DEFAULT_COMPRESSION_WINDOW_BITS;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_CHECK_RESPONSE_CONTENT_TYPE;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_LEVEL;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_MEMORY_LEVEL;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_THRESHOLD;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_WINDOW_BITS;
import static com.mastfrog.acteur.server.ServerModule.MAX_CONTENT_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_MAX_CHUNK_SIZE;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_MAX_HEADER_BUFFER_SIZE;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_MAX_REQUEST_LINE_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_SSL_ENABLED;
import static com.mastfrog.acteur.server.ServerModule.SSL_ATTRIBUTE_KEY;
import static com.mastfrog.acteur.server.ServerModule.X_INTERNAL_COMPRESS;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.thread.AutoCloseThreadLocal;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class PipelineFactoryImpl extends ChannelInitializer<SocketChannel> {

    static final boolean DEFAULT_AGGREGATE_CHUNKS = true;
    static final int DEFAULT_MAX_CONTENT_LENGTH = 1048576;

    private final Provider<ChannelHandler> handler;
    private final boolean aggregateChunks;
    private final int maxContentLength;
    private final boolean httpCompression;
    private final Provider<ApplicationControl> app;
    private final PipelineDecorator decorator;
    private final ActeurSslConfig sslConfigProvider;
    boolean useSsl;
    private final EarlyPagesPipelineDecorator earlyPages;
    private final Application application;
    private final int maxInitialLineLength;
    private final int maxHeadersSize;
    private final int maxChunkSize;
    private final int compressionLevel;
    private final int compressionWindowBits;
    private final int compressionMemLevel;
    private final int compressionThreshold;
    private final boolean compressionCheckContentType;
    private final boolean compressionDebug;
    
    @Inject
    PipelineFactoryImpl(Provider<ChannelHandler> handler,
            Provider<ApplicationControl> app, Settings settings,
            PipelineDecorator decorator, ActeurSslConfig sslConfigProvider,
            EarlyPagesPipelineDecorator earlyPages,
            Application application) {
        this.decorator = decorator;
        this.handler = handler;
        this.app = app;
        this.sslConfigProvider = sslConfigProvider;
        aggregateChunks = settings.getBoolean("aggregateChunks", DEFAULT_AGGREGATE_CHUNKS);
        compressionDebug = settings.getBoolean("http.compression.debug", false);
        httpCompression = settings.getBoolean(HTTP_COMPRESSION, true);
        maxContentLength = settings.getInt(MAX_CONTENT_LENGTH, DEFAULT_MAX_CONTENT_LENGTH);
        useSsl = settings.getBoolean(SETTINGS_KEY_SSL_ENABLED, false);
        // using the same defaults as the default http codec constructor
        maxInitialLineLength = settings.getInt(SETTINGS_KEY_MAX_REQUEST_LINE_LENGTH, 4096);
        maxHeadersSize = settings.getInt(SETTINGS_KEY_MAX_HEADER_BUFFER_SIZE, 8192);
        maxChunkSize = settings.getInt(SETTINGS_KEY_MAX_CHUNK_SIZE, 8192);
        this.earlyPages = earlyPages;
        this.application = application;

        compressionLevel = fetchIntFromSettingsWithRangeCheck(HTTP_COMPRESSION_LEVEL, settings, 0, 9, DEFAULT_COMPRESSION_LEVEL);
        compressionWindowBits = fetchIntFromSettingsWithRangeCheck(HTTP_COMPRESSION_WINDOW_BITS, settings, 9, 16, DEFAULT_COMPRESSION_WINDOW_BITS);
        compressionMemLevel = fetchIntFromSettingsWithRangeCheck(HTTP_COMPRESSION_MEMORY_LEVEL, settings, 1, 9, DEFAULT_COMPRESSION_MEMORY_LEVEL);
        compressionCheckContentType = settings.getBoolean(HTTP_COMPRESSION_CHECK_RESPONSE_CONTENT_TYPE, false);
        compressionThreshold = settings.getInt(HTTP_COMPRESSION_THRESHOLD, DEFAULT_COMPRESSION_THRESHOLD);
        if (compressionThreshold < 0) {
            throw new ConfigurationError(HTTP_COMPRESSION_THRESHOLD + " may not be < 0 but is " + compressionThreshold);
        }
    }

    private static int fetchIntFromSettingsWithRangeCheck(String name, Settings settings, int min, int max, int def) {
        int val = settings.getInt(name, def);
        if (val < min) {
            throw new ConfigurationError(name + " must be between " + min + " and " + max + " but is set to " + val
                    + " in settings.  Check your configuration.");
        }
        return val;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        app.get().internalOnError(cause);
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();
        ch.attr(SSL_ATTRIBUTE_KEY).set(useSsl);
        if (useSsl) {
            decorator.onBeforeInstallSslHandler(pipeline);
            pipeline.addLast(PipelineDecorator.SSL_HANDLER, sslConfigProvider.get().newHandler(ch.alloc()));
        }
        decorator.onCreatePipeline(pipeline);

        ChannelHandler decoder = new HttpRequestDecoder(maxInitialLineLength, maxHeadersSize, maxChunkSize);
        boolean hasEarly = application.hasEarlyPages();
        ChannelHandler encoder = new HackHttpResponseEncoder();

        pipeline.addLast(PipelineDecorator.DECODER, decoder);
        pipeline.addLast(PipelineDecorator.ENCODER, encoder);
        if (aggregateChunks) {
            ChannelHandler aggregator = hasEarly ? new SelectiveAggregator(maxContentLength, application)
                    : new HttpObjectAggregator(maxContentLength);
            pipeline.addLast(PipelineDecorator.AGGREGATOR, aggregator);
        }
        if (httpCompression) {
            ChannelHandler compressor = new SelectiveCompressor(compressionLevel, compressionWindowBits,
                    compressionMemLevel, compressionThreshold, compressionCheckContentType,
                    compressionDebug);
            pipeline.addLast(PipelineDecorator.COMPRESSOR, compressor);
        }
        pipeline.addLast(PipelineDecorator.HANDLER, handler.get());

        earlyPages.onCreatePipeline(pipeline);
        decorator.onPipelineInitialized(pipeline);
    }

    static final class HackHttpResponseEncoder extends HttpResponseEncoder {

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            if (msg instanceof ByteBuf && ((ByteBuf) msg).readableBytes() > 0) {
                out.add(((ByteBuf) msg).retain());
                return;
            } else if (msg instanceof ByteBuf && ((ByteBuf) msg).readableBytes() == 0) {
                return;
            }
            super.encode(ctx, msg, out); //To change body of generated methods, choose Tools | Templates.
        }
    }

    static final boolean ACTEUR_DEBUG = Boolean.getBoolean("acteur.debug");
    static final class SelectiveCompressor extends HttpContentCompressor {

        private final int compressionThreshold;
        private final boolean compressionCheckContentType;
        private final boolean debug;

        SelectiveCompressor(int compressionLevel, int windowBits, int memLevel, int compressionThreshold,
                boolean compressionCheckContentType, boolean compressionDebug) {
            super(compressionLevel, windowBits, memLevel);
            this.compressionThreshold = compressionThreshold;
            this.compressionCheckContentType = compressionCheckContentType;
            this.debug = compressionDebug;
        }

        @Override
        protected ZlibWrapper determineWrapper(String acceptEncoding) {
            ZlibWrapper result = super.determineWrapper(acceptEncoding);
            if (debug) {
                if (result != null) {
                    System.out.println("Using ZlibWrapper " + result.name() + " for " + acceptEncoding);
                } else {
                    System.out.println("Did not find ZlibWrapper for " + acceptEncoding + " - will not compress");
                }
            }
            return result;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
            if (debug) {
                System.out.println("Encode " + msg);
            }
            super.encode(ctx, msg, out); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean acceptInboundMessage(Object msg) throws Exception {
            return super.acceptInboundMessage(msg); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected Result beginEncode(HttpResponse headers, String acceptEncoding) throws Exception {
            final HttpHeaders hdrs = headers.headers();
            Integer contentLength = hdrs.getInt(CONTENT_LENGTH);

            if (debug) {
                System.out.println("beginEncode " + acceptEncoding + " of " + headers);
            }

            // Note we cannot test for content length in chunked responses - they get the default
            // behavior of HttpContentCompressor
            if (contentLength != null) {
                if (contentLength < compressionThreshold) {
                    if (debug) {
                        System.out.println("Content length below threshold, not compressing.");
                    }
                    return null;
                }
            }
            if (hdrs.contains(X_INTERNAL_COMPRESS)) {
                if (debug) {
                    System.out.println("Found X-Internal-Compress header, not compressing.");
                }
                hdrs.remove(X_INTERNAL_COMPRESS);
                return null;
            }
            if (compressionCheckContentType) {
                String contentType = hdrs.get(HttpHeaderNames.CONTENT_TYPE);
                if (contentType != null) {
                    if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")){
                        if (debug) {
                            System.out.println("Media content, not compressing.");
                        }
                        return null;
                    }
                }
            }

            Result result = super.beginEncode(headers, acceptEncoding);
            if (result != null) {
                if (ACTEUR_DEBUG) {
                    // Ensures responses indicate if they were compressed by this compressor, even if
                    // they are received by a decoder that transparently decompresses them
                    hdrs.add(COMPRESS_DEBUG_HEADER, TRUE);
                }
            } else if (debug) {
                System.out.println("Compressor returned null, not compressing.");
            }
            return result;
        }
    }

    private static final AsciiString COMPRESS_DEBUG_HEADER = AsciiString.of("X-Compressed");
    private static final AsciiString TRUE = AsciiString.of("1");
    static final AttributeKey<Boolean> EARLY_KEY = AttributeKey.newInstance(SelectiveAggregator.class.getSimpleName());

    static final class SelectiveAggregator extends HttpObjectAggregator {

        static final AutoCloseThreadLocal<ChannelHandlerContext> localCtx = new AutoCloseThreadLocal<>();
        private final Application app;
        private final boolean hasEarlyPages;

        public SelectiveAggregator(int maxContentLength, Application app) {
            super(maxContentLength);
            this.app = app;
            hasEarlyPages = app.hasEarlyPages();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!hasEarlyPages) {
                super.channelRead(ctx, msg);
                return;
            }
            try (QuietAutoCloseable cl = localCtx.set(ctx)) {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public boolean acceptInboundMessage(Object msg) throws Exception {
            if (!hasEarlyPages) {
                return super.acceptInboundMessage(msg);
            }
            if (super.acceptInboundMessage(msg)) {
                ChannelHandlerContext ctx = localCtx.get();
                assert ctx != null : "No context";
                Boolean e = ctx.channel().attr(EARLY_KEY).get();
                if (e != null && e) {
                    return false;
                }
                if (msg instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) msg;
                    if (app.isEarlyPageMatch(req)) {
                        ctx.channel().attr(EARLY_KEY).set(true);
                        return false;
                    } else {
                        ctx.channel().attr(EARLY_KEY).set(false);
                    }
                } else {
                    Boolean early = ctx.channel().attr(EARLY_KEY).get();
                    if (early != null && early) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }
}
