/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteur.mongo.async;

import com.google.inject.Inject;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.util.Codec;
import com.mongodb.async.AsyncBatchCursor;
import io.netty.channel.ChannelFutureListener;

/**
 * Create a ChannelFutureListener that will write out the contents of a cursor
 * as the response, in small batches.
 *
 * @author Tim Boudreau
 */
public final class CursorWriterFactory {

    private final HttpEvent evt;
    private final ApplicationControl ctrl;
    private final ConstantBuffers constant;
    private final Codec codec;

    @Inject
    CursorWriterFactory(HttpEvent evt, ApplicationControl ctrl, ConstantBuffers constant, Codec codec) {
        this.evt = evt;
        this.ctrl = ctrl;
        this.constant = constant;
        this.codec = codec;
    }

    public <T> ChannelFutureListener create(AsyncBatchCursor<T> cursor) {
        return new CursorWriter<>(cursor, evt, ctrl, constant, codec);
    }
}
