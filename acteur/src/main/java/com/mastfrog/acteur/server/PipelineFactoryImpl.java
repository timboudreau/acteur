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
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION;
import static com.mastfrog.acteur.server.ServerModule.MAX_CONTENT_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_SSL_ENABLED;
import static com.mastfrog.acteur.server.ServerModule.SSL_ATTRIBUTE_KEY;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.settings.Settings;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.AttributeKey;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Sharable
class PipelineFactoryImpl extends ChannelInitializer<SocketChannel> {

    static final boolean DEFAULT_AGGREGATE_CHUNKS = true;

    private final Provider<ChannelHandler> handler;
    private final boolean aggregateChunks;
    private final int maxContentLength;
    private final boolean httpCompression;
    private final Provider<ApplicationControl> app;
    private final PipelineDecorator decorator;
    private final ActeurSslConfig sslConfigProvider;
    boolean useSsl;

    @Inject
    PipelineFactoryImpl(Provider<ChannelHandler> handler, 
            Provider<ApplicationControl> app, Settings settings, 
            PipelineDecorator decorator, ActeurSslConfig sslConfigProvider) {
        this.decorator = decorator;
        this.handler = handler;
        this.app = app;
        this.sslConfigProvider = sslConfigProvider;
        aggregateChunks = settings.getBoolean("aggregateChunks", DEFAULT_AGGREGATE_CHUNKS);
        httpCompression = settings.getBoolean(HTTP_COMPRESSION, true);
        maxContentLength = settings.getInt(MAX_CONTENT_LENGTH, 1048576);
        useSsl = settings.getBoolean(SETTINGS_KEY_SSL_ENABLED, false);
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

        ChannelHandler decoder = new HttpRequestDecoder();
        ChannelHandler encoder = new HttpResponseEncoder();

        pipeline.addLast(PipelineDecorator.DECODER, decoder);
        pipeline.addLast(PipelineDecorator.ENCODER, encoder);
        if (aggregateChunks) {
            ChannelHandler aggregator = new HttpObjectAggregator(maxContentLength);
            pipeline.addLast(PipelineDecorator.AGGREGATOR, aggregator);
        }
        if (httpCompression) {
            ChannelHandler compressor = new SelectiveCompressor();
            pipeline.addLast(PipelineDecorator.COMPRESSOR, compressor);
        }
        pipeline.addLast(PipelineDecorator.HANDLER, handler.get());
        decorator.onPipelineInitialized(pipeline);
    }

    private static class SelectiveCompressor extends HttpContentCompressor {

        @Override
        protected Result beginEncode(HttpResponse headers, String acceptEncoding) throws Exception {
            if (headers.headers().contains("X-Internal-Compress")) {
                headers.headers().remove("X-Internal-Compress");
                return null;
            }
            return super.beginEncode(headers, acceptEncoding);
        }
    }
}
