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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Application;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 *
 * @author Tim Boudreau
 */
@ChannelHandler.Sharable
@Singleton
final class UpstreamHandlerImpl extends ChannelInboundMessageHandlerAdapter<Object>{

    private final Application application;
    private final PathFactory paths;
    @Inject(optional = true)
    @Named("neverKeepAlive")
    private boolean neverKeepAlive;
    private @Inject(optional = true)
    @Named("aggregateChunks")
    boolean aggregateChunks = PipelineFactoryImpl.DEFAULT_AGGREGATE_CHUNKS;
    private final Provider<ObjectMapper> mapper;


    @Inject
    UpstreamHandlerImpl(Application application, PathFactory paths, Provider<ObjectMapper> mapper) {
        this.application = application;
        this.paths = paths;
        this.mapper = mapper;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        application.onError(cause);
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        // HttpContent$2 - ?
        if (msg instanceof FullHttpRequest) {
            ((FullHttpRequest) msg).retain();
        }
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            
            if (!aggregateChunks && HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }
            
            EventImpl evt = new EventImpl(request, ctx.channel().remoteAddress(), ctx.channel(), paths, mapper);
            evt.setNeverKeepAlive(neverKeepAlive);
            application.onEvent(evt, ctx.channel());
        } else {
            System.out.println("Don't know how to process " + msg + " " + msg.getClass().getName());
        }
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

}
