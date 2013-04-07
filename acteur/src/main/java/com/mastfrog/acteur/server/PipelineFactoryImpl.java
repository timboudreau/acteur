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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Application;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.http.HttpContentCompressor;

//@Singleton
class PipelineFactoryImpl extends ChannelInitializer<SocketChannel> {
    static final boolean DEFAULT_AGGREGATE_CHUNKS = true;

    private final Provider<ChannelHandler> handler;
    private @Inject(optional = true)
    @Named("aggregateChunks")
    boolean aggregateChunks = DEFAULT_AGGREGATE_CHUNKS;
    private @Inject(optional = true)
    @Named("maxContentLength")
    int maxContentLength = 1048576;
    @Named("httpCompression")
    boolean httpCompression = true;
    private final Provider<Application> app;

    @Inject
    PipelineFactoryImpl(Provider<ChannelHandler> handler, Provider<Application> app) {
        this.handler = handler;
        this.app = app;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        app.get().onError(cause);
    }
    
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        if (maxContentLength == 0) {
            maxContentLength = 1048576;
        }
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("decoder", new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        if (aggregateChunks) {
            pipeline.addLast("aggregator", new HttpObjectAggregator(maxContentLength));
        }
        pipeline.addLast("bytes", new MessageBufEncoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());

        // Remove the following line if you don't want automatic content compression.
        if (httpCompression) {
            pipeline.addLast("deflater", new HttpContentCompressor());
        }
        pipeline.addLast("handler", handler.get());
    }

    private static class MessageBufEncoder extends MessageToByteEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
            out.writeBytes(msg);
        }

        @Override
        public MessageBuf<ByteBuf> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }
        
        @Override
        public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
//            System.out.println("Free outbound buffer " + ctx);
            // PENDING:  Find the MessageBuf we allocated and decrement its 
            // reference count
        }
    }
}
