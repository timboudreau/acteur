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
import io.netty.buffer.CompositeByteBuf;
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
        this.closeConnection = !evt.requestsConnectionStayOpen();
        this.ctrl = ctrl;
        this.constant = constant;
        this.codec = codec;
    }

    ChannelFuture future(ChannelFuture channel) {
        future.set(channel);
        return channel;
    }

    private void addBuf(CompositeByteBuf buf, ByteBuf toAdd) {
        int writerIndex = buf.writerIndex();
        buf.addComponent(toAdd.retain());
        buf.writerIndex(writerIndex + toAdd.readableBytes());
    }

    private boolean checkFailure(ChannelFuture f) {
        if (!f.isSuccess()) {
            try {
                cursor.close();
                f.channel().close();
                return true;
            } finally {
                if (f.cause() != null) {
                    ctrl.internalOnError(f.cause());
                }
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void operationComplete(ChannelFuture f) throws Exception {
        boolean done = this.done;
        if (checkFailure(f)) {
            return;
        }
        List<Object> results = new LinkedList<>();
        synchronized (this) {
            results.addAll(collectedResults);
            collectedResults.clear();
        }
        // Buffer count will be size + a comma for each, plus potential open and close marks and a leading comma
        int maxComponents = (results.size() * 2) + 5;
        // Use a composite byte buf for zero copy if using ByteBufCodec (which shares the
        // allocator with the application, so they are drawn from the same pool)
        CompositeByteBuf buf = f.channel().alloc().compositeBuffer(maxComponents);
        boolean first = this.first.compareAndSet(false, true);
        if (first) {
            addBuf(buf, constant.open());
        }
        if (!results.isEmpty()) {
            if (resultWritten) {
                addBuf(buf, constant.comma());
            }
            for (Iterator<Object> it = results.iterator(); it.hasNext();) {
                resultWritten = true;
                Object next = it.next();
                if (next instanceof ByteBuf) {
                    addBuf(buf, (ByteBuf) next);
                } else {
                    addBuf(buf, f.channel().alloc().buffer().writeBytes(codec.writeValueAsBytes(next)));
                }
                if (it.hasNext()) {
                    addBuf(buf, constant.comma());
                }
            }
        }
        if (results.isEmpty() && done) {
            addBuf(buf, constant.close());
        }
        if (buf.writerIndex() > 0) {
            f = future(f.channel().writeAndFlush(new DefaultHttpContent(buf)));
        } else {
            f = future(f);
        }
        if (done) {
            f.addListener((ChannelFutureListener) (ChannelFuture future1) -> {
                if (checkFailure(future1)) {
                    return;
                }
                future1 = future(future1.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT));
                if (closeConnection) {
                    future(future1.addListener(CLOSE));
                }
            });
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
