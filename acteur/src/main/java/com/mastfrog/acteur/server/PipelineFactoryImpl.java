/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpConstants.SP;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import static io.netty.handler.codec.http.HttpUtil.getContentLength;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.util.List;
import java.util.Map;
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

    @Inject
    PipelineFactoryImpl(Provider<ChannelHandler> handler, Provider<ApplicationControl> app, Settings settings, PipelineDecorator decorator) {
        this.decorator = decorator;
        this.handler = handler;
        this.app = app;
        aggregateChunks = settings.getBoolean("aggregateChunks", DEFAULT_AGGREGATE_CHUNKS);
        httpCompression = settings.getBoolean(HTTP_COMPRESSION, true);
        maxContentLength = settings.getInt(MAX_CONTENT_LENGTH, 1048576);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        app.get().internalOnError(cause);
    }

//    static class LoggingChannelPipeline extends WrapperChannelPipeline {
//
//        public LoggingChannelPipeline(WrapperChannel channel, ChannelPipeline real) {
//            super(channel, real);
//        }
//
//        @Override
//        protected void onRemove(ChannelHandler h) {
//            new Exception("Handler removed: " + h).printStackTrace();
//            System.out.println("Handlers now: " + this);
//        }
//
//        @Override
//        protected void onAdd(ChannelHandler handler) {
//            System.out.println("Add handler " + handler);
//        }
//    }
//    
//    private SocketChannel wrap(SocketChannel orig) {
//        WrapperSocketChannel channel = new WrapperSocketChannel(orig);
//        WrapperChannelPipeline pipeline = new LoggingChannelPipeline(channel, orig.pipeline());
//        channel.setPipeline(pipeline);
//        return channel;
//    }
    final MessageBufEncoder messageBufEncoder = new MessageBufEncoder();

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
//        ch = wrap(ch);
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();
        decorator.onCreatePipeline(pipeline);
        ChannelHandler decoder = new HackHttpRequestDecoder();
        HttpResponseEncoder encoder = new HttpResponseEncoder();

        pipeline.addLast(PipelineDecorator.DECODER, decoder);
        // Uncomment the following line if you don't want to handle HttpChunks.
        pipeline.addLast(PipelineDecorator.ENCODER, encoder);
        if (aggregateChunks) {
            ChannelHandler aggregator = new HttpObjectAggregator(maxContentLength);
            pipeline.addLast(PipelineDecorator.AGGREGATOR, aggregator);
        }

        pipeline.addLast(PipelineDecorator.BYTES, messageBufEncoder);

        if (httpCompression) {
            pipeline.addLast(PipelineDecorator.COMPRESSOR, new SelectiveCompressor());
        }
        pipeline.addLast(PipelineDecorator.HANDLER, handler.get());
        decorator.onPipelineInitialized(pipeline);
    }

    @Sharable
    private static class MessageBufEncoder extends MessageToByteEncoder<ByteBuf> {

        MessageBufEncoder() {
            super(ByteBuf.class);
        }
        
        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
            out.writeBytes(msg);
        }
    }

    @Sharable
    private static class SelectiveCompressor extends HttpContentCompressor {

        @Override
        protected Result beginEncode(HttpResponse headers, CharSequence acceptEncoding) throws Exception {
            if (headers.headers().contains("X-Internal-Compress")) {
                headers.headers().remove("X-Internal-Compress");
                return null;
            }
            return super.beginEncode(headers, acceptEncoding);
        }
    }

    static class HackHttpRequestDecoder extends HttpRequestDecoder {

        // See https://github.com/netty/netty/issues/3247
        protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            try {
                while (in.isReadable()) {
                    int outSize = out.size();
                    int oldInputLength = in.readableBytes();
                    decode(ctx, in, out);

                    // Check if this handler was removed before continuing the loop.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See https://github.com/netty/netty/issues/1664
                    if (ctx.isRemoved()) {
                        break;
                    }

                    if (outSize == out.size()) {
                        if (oldInputLength == in.readableBytes()) {
                            break;
                        } else {
                            continue;
                        }
                    }

                    if (isSingleDecode()) {
                        break;
                    }
                }
            } catch (DecoderException e) {
                throw e;
            } catch (Throwable cause) {
                throw new DecoderException(cause);
            }
        }
    }
}
