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
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION;
import static com.mastfrog.acteur.server.ServerModule.MAX_CONTENT_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_MAX_CHUNK_SIZE;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_MAX_HEADER_BUFFER_SIZE;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_MAX_REQUEST_LINE_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_SSL_ENABLED;
import static com.mastfrog.acteur.server.ServerModule.SSL_ATTRIBUTE_KEY;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.thread.AutoCloseThreadLocal;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
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
        httpCompression = settings.getBoolean(HTTP_COMPRESSION, true);
        maxContentLength = settings.getInt(MAX_CONTENT_LENGTH, DEFAULT_MAX_CONTENT_LENGTH);
        useSsl = settings.getBoolean(SETTINGS_KEY_SSL_ENABLED, false);
        // using the same defaults as the default http codec constructor
        maxInitialLineLength = settings.getInt(SETTINGS_KEY_MAX_REQUEST_LINE_LENGTH, 4096);
        maxHeadersSize = settings.getInt(SETTINGS_KEY_MAX_HEADER_BUFFER_SIZE, 8192);
        maxChunkSize = settings.getInt(SETTINGS_KEY_MAX_CHUNK_SIZE, 8192);
        this.earlyPages = earlyPages;
        this.application = application;
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
        ChannelHandler encoder = application.hasEarlyPages() ? new HackHttpResponseEncoder() : new HttpResponseEncoder();

        pipeline.addLast(PipelineDecorator.DECODER, decoder);
        pipeline.addLast(PipelineDecorator.ENCODER, encoder);
        if (aggregateChunks) {
            ChannelHandler aggregator = new SelectiveAggregator(maxContentLength, application);
            pipeline.addLast(PipelineDecorator.AGGREGATOR, aggregator);
        }
        if (httpCompression) {
            ChannelHandler compressor = new SelectiveCompressor();
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
            }
            super.encode(ctx, msg, out); //To change body of generated methods, choose Tools | Templates.
        }

    }

    static final class SelectiveCompressor extends HttpContentCompressor {

        @Override
        protected Result beginEncode(HttpResponse headers, String acceptEncoding) throws Exception {
            if (headers.headers().contains("X-Internal-Compress")) {
                headers.headers().remove("X-Internal-Compress");
                return null;
            }
            if (headers.headers().contains(HttpHeaderNames.CONTENT_LENGTH) && headers.headers().getInt(CONTENT_LENGTH, 0) == 0) {
                return null;
            }
            return super.beginEncode(headers, acceptEncoding);
        }
    }

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
