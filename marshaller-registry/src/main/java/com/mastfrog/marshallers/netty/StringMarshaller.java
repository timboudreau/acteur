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
import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;
import java.nio.charset.Charset;
import com.mastfrog.marshallers.Marshaller;
import static com.mastfrog.marshallers.netty.NettyContentMarshallers.findCharset;

/**
 *
 * @author Tim Boudreau
 */
final class StringMarshaller implements Marshaller<String, ByteBuf> {

    @Override
    public String read(ByteBuf data, Object[] hints) throws Exception {
        byte[] bytes = new byte[data.readableBytes()];
        data.getBytes(0, bytes);
        Charset charset = findCharset(hints);
        if (charset == null) {
            charset = NettyContentMarshallers.findHint(Charset.class, hints, CharsetUtil.UTF_8);
        }
        return new String(bytes, charset);
    }

    @Override
    public void write(String obj, ByteBuf into, Object[] hints) throws Exception {
        Charset charset = findCharset(hints);
        if (CharsetUtil.UTF_8.equals(charset)) {
            ByteBufUtil.writeUtf8(into, obj);
        } else if (CharsetUtil.US_ASCII.equals(charset)) {
            ByteBufUtil.writeAscii(into, obj);
        } else {
            byte[] bytes = obj.getBytes(charset);
            into.writeBytes(bytes);
        }
    }

}
