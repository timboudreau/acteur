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

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.giulius.annotations.Setting;
import com.mastfrog.giulius.annotations.Setting.ValueType;
import com.mastfrog.settings.Settings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.InputStream;
import javax.inject.Inject;

/**
 * Takes an input stream and writes it
 *
 * @author Tim Boudreau
 */
public final class InputStreamActeur extends Acteur {

    public static final int DEFAULT_INPUT_STREAM_BATCH_SIZE = 512;
    @Setting(value = "If using InputStreamActeur, the number of bytes that should be "
            + "pulled into memory and written to the socket in a single batch.",
            defaultValue = DEFAULT_INPUT_STREAM_BATCH_SIZE + "", pattern = "\\d+",
            type = ValueType.INTEGER)
    public static final String SETTINGS_KEY_INPUT_STREAM_BATCH_SIZE = "input.stream.batch.size";

    @Inject
    public InputStreamActeur(Closables clos, InputStream in) {
        clos.add(in);
        ok();
        setChunked(true);
        setResponseBodyWriter(InputStreamPublisher.class);
    }

    static class InputStreamPublisher implements ChannelFutureListener {

        private final InputStream stream;
        private final ApplicationControl ctrl;
        private final byte[] buffer;
        private final ByteBufAllocator alloc;
        private final boolean closeChannelOnEnd;

        @Inject
        InputStreamPublisher(InputStream stream, Settings settings, ApplicationControl ctrl,
                ByteBufAllocator alloc, HttpEvent evt) {
            this.stream = stream;
            this.ctrl = ctrl;
            this.alloc = alloc;
            int batchSize = settings.getInt(SETTINGS_KEY_INPUT_STREAM_BATCH_SIZE,
                    DEFAULT_INPUT_STREAM_BATCH_SIZE);
            buffer = new byte[batchSize];
            closeChannelOnEnd = !evt.requestsConnectionStayOpen();
        }

        @Override
        public synchronized void operationComplete(ChannelFuture f) throws Exception {
            if (f.isCancelled()) {
                return;
            } else if (f.cause() != null) {
                ctrl.internalOnError(f.cause());
                f.channel().close();
                return;
            }
            int readCount = stream.read(buffer);
            if (readCount < 0) {
                f = f.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                if (closeChannelOnEnd) {
                    enqueueListener(f, CLOSE);
                }
                return;
            }
            ByteBuf output = alloc.ioBuffer(readCount);
            output.writeBytes(buffer, 0, readCount);
            enqueueListener(f.channel().writeAndFlush(new DefaultHttpContent(output)), this);
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
}
