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
package com.mastfrog.acteur.annotations;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ChunkHandler;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.server.PipelineDecorator;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.giulius.Dependencies;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Description("Installs the chunk handler for unprocessed HTTP content")
public class InstallChunkHandler extends Acteur {

    @Inject
    InstallChunkHandler(Page page, Deferral defer, HttpEvent evt, Dependencies deps, ApplicationControl ctrl) {
        Object[] inject = new Object[0];
        if (Method.POST.name().equals(evt.method().name()) || Method.PUT.name().equals(evt.method().name()) || HttpMethod.PATCH.name().equals(evt.method().name())) {

            ChannelHandlerContext ctx = evt.ctx();

            Early early = page.getClass().getAnnotation(Early.class);
            if (early == null) {
                ctrl.internalOnError(new IllegalStateException("Should not be instantiated for a page without the "
                        + "@Early annotation.  Stale sources?"));
            }

            assert early != null;
            if (early.value() != ChunkHandler.class) {
                ChunkHandler ch = deps.getInstance(early.value());
                ctx.pipeline().addAfter(PipelineDecorator.PRE_CONTENT_PAGE_HANDLER, ch.getClass().getSimpleName(), ch);
                // Set the resumer in deferred code to ensure it can't be called before we have exited this acteur constructor
                defer.defer(ch::setResumer);
            }

            if (early.send100continue()) {
                send100Continue(ctx);
            }
        }
        next(inject);
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
        ctx.writeAndFlush(response);
    }
}
