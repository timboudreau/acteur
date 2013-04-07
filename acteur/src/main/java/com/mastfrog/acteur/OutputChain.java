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
package com.mastfrog.acteur;

import com.mastfrog.util.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Convenience class to allow chaining up of little chunks of logic that
 * write a bit of output, to stagger these on output.
 *
 * @author Tim Boudreau
 */
public class OutputChain implements ChannelFutureListener {

    private final Event evt;
    private final List<Writer> writers = Collections.<Writer>synchronizedList(new LinkedList<Writer>());
    private final boolean chunked;

    public OutputChain(Event evt, boolean chunked) {
        this.chunked = chunked;
        this.evt = evt;
    }

    public OutputChain(Event evt) {
        this(evt, false);
    }

    public OutputChain add(Writer writer) {
        Checks.notNull("writer", writer);
        writers.add(writer);
        return this;
    }

    public OutputChain add(String toWrite) {
        Checks.notNull("toWrite", toWrite);
        writers.add(new StringWriter(toWrite, chunked));
        return this;
    }
    
    private volatile boolean canFinish = true;
    public void setCanFinish(boolean canFinish) {
        this.canFinish = canFinish;
    }

    @Override
    public void operationComplete(ChannelFuture cf) throws Exception {
        Writer w = writers.isEmpty() ? null : writers.remove(0);
        if (w != null) {
            cf = w.write(cf.channel());
            if (!writers.isEmpty()) {
                cf.addListener(this);
            } else {
                if (canFinish) {
                    finish(cf);
                } else {
                    Thread.sleep(200);
                    cf.addListener(this);
                }
            }
        } else {
            if (canFinish) {
                finish(cf);
            } else {
                Thread.sleep(200);
                cf.addListener(this);
            }
        }
    }

    void finish(ChannelFuture cf) {
        if (!chunked) {
            reallyFinish(cf);
        } else {
            cf = cf.channel().write(LastHttpContent.EMPTY_LAST_CONTENT);
            reallyFinish(cf);
        }
    }

    void reallyFinish(ChannelFuture cf) {
        if (!evt.isKeepAlive()) {
            cf.addListener(CLOSE);
        }
    }

    public interface Writer {

        public ChannelFuture write(Channel channel) throws Exception;
    }

    private static final class StringWriter implements Writer {

        private final String string;
        private final boolean chunked;

        public StringWriter(String string, boolean chunked) {
            this.string = string;
            this.chunked = chunked;
        }

        @Override
        public ChannelFuture write(Channel channel) throws Exception {
            Object toWrite = Unpooled.wrappedBuffer(string.getBytes("UTF-8"));
            if (chunked) {
                toWrite = new DefaultHttpContent((ByteBuf) toWrite);
            }
            return channel.write(toWrite);
        }
    }
}
