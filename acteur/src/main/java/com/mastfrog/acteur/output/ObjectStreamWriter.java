/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.acteur.output;

import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.util.codec.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
final class ObjectStreamWriter implements ChannelFutureListener {

    private final Iterator<?> iter;
    private final ByteBufAllocator alloc;
    private final Codec codec;
    private final ArrayFormatting settings;
    private final Charset charset;
    private final ApplicationControl ctrl;
    private final boolean closeConnection;
    private int emitCount;

    @Inject
    ObjectStreamWriter(Stream stream,
            ArrayFormatting settings, Codec codec, Closables clos,
            ByteBufAllocator alloc, Charset charset, ApplicationControl ctrl,
            HttpEvent evt) {
        this.iter = stream.iterator();
        this.settings = settings;
        this.codec = codec;
        this.alloc = alloc;
        this.charset = charset;
        this.ctrl = ctrl;
        this.closeConnection = !evt.requestsConnectionStayOpen();
    }

    @Override
    public synchronized void operationComplete(ChannelFuture f) throws Exception {
        if (f.isCancelled()) {
            return;
        }
        if (f.cause() != null) {
            ctrl.internalOnError(f.cause());
            return;
        }
        CompositeByteBuf aggregate = alloc.compositeBuffer();
        int currentEmission;
        if ((currentEmission = emitCount++) == 0) {
            byte[] opening = settings.openingDelimiter().getBytes(charset);
            ByteBuf buf = alloc.ioBuffer(opening.length);
            buf.writeBytes(opening);
            aggregate.addComponent(true, buf);
        }
        int max = settings.batchBytes();
        byte[] delimiterBytes = settings.interItemDelimiter().getBytes(charset);
        int ct = 0;
        while (iter.hasNext()) {
            try {
                Object o = iter.next();
                byte[] bytes = codec.writeValueAsBytes(o);
                int bytesRequired = currentEmission > 0 ? delimiterBytes.length + bytes.length : bytes.length;
                ByteBuf buf = alloc.ioBuffer(bytesRequired);
                if (currentEmission > 0 || ct > 0) {
                    buf.writeBytes(delimiterBytes);
                }
                buf.writeBytes(bytes);
                aggregate.addComponent(true, buf);
                if (aggregate.writerIndex() >= max) {
                    break;
                }
            } catch (Exception | Error e) {
                try {
                    ctrl.internalOnError(e);
                } finally {
                    f.channel().close();
                    return;
                }
            }
            ct++;
        }
        boolean done = !iter.hasNext();

        HttpContent chunk;
        if (done) {
            byte[] closing = settings.closingDelimiter().getBytes(charset);
            ByteBuf buf = alloc.ioBuffer(closing.length);
            buf.writeBytes(closing);
            aggregate.addComponent(true, buf);
            chunk = new DefaultLastHttpContent(aggregate);
        } else {
            chunk = new DefaultHttpContent(aggregate);
        }
        f = f.channel().writeAndFlush(chunk);
        if (!done) {
            enqueueListener(f, this);
        } else if (closeConnection) {
            enqueueListener(f, CLOSE);
        }
    }

    private void enqueueListener(ChannelFuture f, ChannelFutureListener l) {
        // If we add the listener directly, we can get a callback *before* the
        // data is flushed and wind up mis-ordering our output.
        f.addListener((ChannelFuture f1) -> {
            f1.channel().eventLoop().submit(() -> {
                try {
                    l.operationComplete(f1);
                } catch (Exception ex) {
                    try {
                        ctrl.internalOnError(ex);
                    } finally {
                        f1.channel().close();
                    }
                }
            });
        });
    }
}
