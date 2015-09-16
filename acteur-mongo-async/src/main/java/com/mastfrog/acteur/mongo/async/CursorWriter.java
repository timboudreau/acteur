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
import com.mongodb.async.SingleResultCallback;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Tim Boudreau
 */
final class CursorWriter<T> implements ChannelFutureListener, SingleResultCallback<List<T>> {

    private final AsyncBatchCursor<T> cursor;
    private final boolean closeConnection;
    private final List<Object> collectedResults = new LinkedList<>();
    private final ApplicationControl ctrl;
    private final AtomicBoolean first = new AtomicBoolean();
    private final ConstantBuffers constant;
    private AtomicReference<ChannelFuture> future = new AtomicReference<>();
    private volatile boolean done;
    private volatile boolean resultWritten;
    private final Codec codec;

    @Inject
    public CursorWriter(final AsyncBatchCursor<T> cursor, HttpEvent evt, ApplicationControl ctrl, ConstantBuffers constant, Codec codec) {
        this.cursor = cursor;
        this.closeConnection = !evt.isKeepAlive();
        this.ctrl = ctrl;
        this.constant = constant;
        this.codec = codec;
    }

    ChannelFuture future(ChannelFuture channel) {
        future.set(channel);
        return channel;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void operationComplete(ChannelFuture f) throws Exception {
        boolean done = this.done;
        if (!f.isSuccess() && f.cause() != null) {
            if (f.cause() != null) {
                ctrl.internalOnError(f.cause());
            }
            cursor.close();
            return;
        }
        // XXX use ComposeiteByteBuffer
        ByteBuf buf = f.channel().alloc().buffer();
        boolean first = this.first.compareAndSet(false, true);
        if (first) {
            buf.writeBytes(constant.open());
        }
        List<Object> results = new LinkedList<>();
        synchronized (this) {
            results.addAll(collectedResults);
            collectedResults.clear();
        }
        if (!results.isEmpty()) {
            if (resultWritten) {
                buf.writeBytes(constant.comma());
            }
            for (Iterator<Object> it = results.iterator(); it.hasNext();) {
                resultWritten = true;
                Object next = it.next();
                if (next instanceof ByteBuf) {
                    buf.writeBytes((ByteBuf) next);
                } else {
                    buf.writeBytes(codec.writeValueAsBytes(next));
                }
                if (it.hasNext()) {
                    buf.writeBytes(constant.comma());
                }
            }
        }
        if (results.isEmpty() && done) {
            buf.writeBytes(constant.close());
        }
        if (buf.writerIndex() > 0) {
            future(f.channel().writeAndFlush(new DefaultHttpContent(buf)));
        } else {
            future(f);
        }
        if (done) {
            future(f.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT));
        }
        if (done && closeConnection) {
            future(f.addListener(CLOSE));
        }
        if (!done) {
            f.addListener(fetchNext);
        }
    }

    @Override
    public void onResult(List<T> t, Throwable thrwbl) {
        if (t == null) {
            done = true;
        } else {
            synchronized (this) {
                collectedResults.addAll(t);
            }
        }
        ChannelFuture f = future.get();
        if (thrwbl != null) {
            ctrl.internalOnError(thrwbl);
            cursor.close();
            if (f != null) {
                f.channel().close();
            }
        } else if (f != null) {
            try {
                operationComplete(f);
            } catch (Exception ex) {
                ctrl.internalOnError(ex);
            }
        }
    }

    private final ChannelFutureListener fetchNext = new ChannelFutureListener() {
        @Override
        @SuppressWarnings("unchecked")
        public void operationComplete(ChannelFuture f) throws Exception {
            cursor.next(CursorWriter.this);
        }
    };
}
