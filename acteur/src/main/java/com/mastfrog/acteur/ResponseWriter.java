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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Abstraction over Netty's channel/ByteBuf to insulate from changes there.
 *
 * @author Tim Boudreau
 */
public abstract class ResponseWriter {

    public Status write(Event<?> evt, Output out, int iteration) throws Exception {
        return write(evt, out);
    }
    
    public Status write(Event<?> evt, Output out) throws Exception {
        throw new UnsupportedOperationException("Must override one of the write() methods");
    }

    public enum Status {
        /**
         * Call the object returning this value again in the future so it can
         * write more output
         */
        NOT_DONE,
        /**
         * Don't call the object returing this value back - it will take care
         * of writing more output and closing the channel when done.
         */
        DEFERRED,
        DONE;

        boolean isCallback() {
            return this == NOT_DONE;
        }
    }

    public interface Output {

        Output writeObject(Object o) throws IOException;

        Output writeObjectIf(Object o, boolean condition) throws IOException;

        Output writeIf(boolean condition, String what) throws IOException;

        Output write(String string) throws IOException;

        Output write(byte[] bytes) throws IOException;

        Output write(InputStream in) throws IOException;

        Output write(ByteBuffer buf) throws IOException;

        Output write(ByteBuf buf) throws IOException;

        <T> Output trailer(HeaderValueType<T> type, T value);
        
        Channel channel();
        
        ChannelFuture future();
    }

    static abstract class AbstractOutput implements Output {

        private final Charset charset;
        private final ByteBufAllocator allocator;
        private final ObjectMapper mapper;

        protected AbstractOutput(Charset charset, ByteBufAllocator allocator, ObjectMapper mapper) {
            this.charset = charset;
            this.allocator = allocator;
            this.mapper = mapper;
        }

        @Override
        public Output writeObject(Object o) throws IOException {
            return write(mapper.writeValueAsString(o));
        }

        @Override
        public Output writeObjectIf(Object o, boolean condition) throws IOException {
            if (condition) {
                return writeObject(o);
            }
            return this;
        }

        @Override
        public Output writeIf(boolean condition, String what) throws IOException {
            if (condition) {
                return write(what);
            }
            return this;
        }

        @Override
        public Output write(String string) throws IOException {
            return write(string.getBytes(charset));
        }

        @Override
        public Output write(byte[] bytes) throws IOException {
            ByteBuf b = buf();
            b.writeBytes(bytes);
            return write(b);
        }

        @Override
        public Output write(InputStream in) throws IOException {
            ByteBuf b = buf();
            // XXX stagger this so we don't pull the whole stream into RAM
            ByteBufOutputStream out = new ByteBufOutputStream(b);
            Streams.copy(in, out);
            return write(b);
        }

        @Override
        public Output write(ByteBuffer buf) throws IOException {
            ByteBuf b = buf().writeBytes(buf);
            return write(b);
        }

        protected ByteBuf buf() {
            return allocator.compositeBuffer();
        }

        @Override
        public <T> Output trailer(HeaderValueType<T> type, T value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
