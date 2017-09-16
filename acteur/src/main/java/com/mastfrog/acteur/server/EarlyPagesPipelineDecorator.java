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
package com.mastfrog.acteur.server;

import com.google.inject.Singleton;
import com.mastfrog.acteur.Application;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
@Sharable
class EarlyPagesPipelineDecorator implements PipelineDecorator {

    private final Handler handler;

    @Inject
    EarlyPagesPipelineDecorator(Application app, ChannelHandler upstream) {
        this.handler = app.hasEarlyPages() ? new Handler(app, (UpstreamHandlerImpl) upstream) : null;
    }

    @Override
    public void onCreatePipeline(ChannelPipeline pipeline) {
        if (handler != null) {
            pipeline.addAfter(DECODER, PRE_CONTENT_PAGE_HANDLER, handler);
            pipeline.remove(AGGREGATOR);
            // XXX HttpContentEncoder is in the wrong state for an early response, and complains
            // that we have not yet received a request. So it's this or manually set its state
            // via reflection, which is nastier.
            pipeline.remove(COMPRESSOR);
        }
    }

    @Override
    public void onPipelineInitialized(ChannelPipeline pipeline) {
        // do nothing
    }

    @Singleton
    @Sharable
    static final class Handler extends SimpleChannelInboundHandler<HttpRequest> {

        private final Application application;
        private final UpstreamHandlerImpl upstream;

        private Handler(Application app, UpstreamHandlerImpl upstreamHandlerImpl) {
            this.application = app;
            this.upstream = upstreamHandlerImpl;
        }

        @Override
        public boolean acceptInboundMessage(Object msg) throws Exception {
            if (msg instanceof HttpRequest) {
                HttpRequest r = (HttpRequest) msg;
                return application.isEarlyPageMatch(r);
            }
            return false;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
            upstream.handleHttpRequest(ctx, msg, true);
        }
    }
}
