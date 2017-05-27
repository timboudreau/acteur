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
package com.mastfrog.marshallers.netty;

import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import com.mastfrog.marshallers.Marshaller;

/**
 *
 * @author Tim Boudreau
 */
final class ByteBufferMarshaller implements Marshaller<ByteBuffer, ByteBuf> {

    @Override
    public ByteBuffer read(ByteBuf data, Object[] hints) throws Exception {
        if (data.isDirect()) {
            return data.internalNioBuffer(0, data.readableBytes());
        } else {
            ByteBuffer buffer = ByteBuffer.allocateDirect(data.readableBytes());
            data.readBytes(buffer);
            return (ByteBuffer) buffer.flip();
        }
    }

    @Override
    public void write(ByteBuffer obj, ByteBuf into, Object[] hints) throws Exception {
        into.writeBytes(obj);
    }

}
