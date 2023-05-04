/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import com.mastfrog.acteurbase.Deferral.Resumer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Subclasses of this can be used from the &#064;Early annotation to provide an
 * object that will process inbound HTTP chunks.
 *
 * @author Tim Boudreau
 */
public abstract class ChunkHandler extends SimpleChannelInboundHandler<Object> {

    private boolean resumed;
    private Resumer resumer;
    private Object[] context = new Object[0];

    public synchronized void setResumer(Resumer resumer) {
        this.resumer = resumer;
        if (resumed) {
            resumer.resume(context);
        }
    }

    protected final synchronized void resume(Object... ctx) {
        resumed = true;
        if (resumer != null) {
            resumer.resume(context);
        } else {
            this.context = ctx;
        }
    }

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            HttpHeaders headers = content instanceof LastHttpContent ? ((LastHttpContent) content).trailingHeaders() : null;
            onContent(ctx, content.content(), headers, content instanceof LastHttpContent);
        } else if (msg instanceof FullHttpRequest) {
            FullHttpRequest full = (FullHttpRequest) msg;
            onContent(ctx, full.content(), full.headers(), true);
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msg instanceof HttpContent || msg instanceof FullHttpRequest;
    }

    /**
     * Called with each chunk of content as it arrives. Once the final content
     * is processed and you want to send the response, call resume() with any
     * objects you want to add for injection into susbsequent acteurs.
     *
     * @param ctx The context
     * @param chunk The chunk
     * @param trailersOrHeaders Trailers in the case of a LastHttpContent,
     * headers in the case of a FullHttpRequest (non chunked)
     * @param done If this is the last one
     * @throws Exception If something goes wrong
     */
    protected abstract void onContent(ChannelHandlerContext ctx, ByteBuf chunk, HttpHeaders trailersOrHeaders, boolean done) throws Exception;
}
