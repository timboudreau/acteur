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
package com.mastfrog.acteur.mongo.reactive;

import com.google.inject.Inject;
import com.mastfrog.settings.Settings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
class ConstantBuffers {

    private final ByteBuf open;
    private final ByteBuf comma;
    private final ByteBuf close;

    @Inject
    ConstantBuffers(ByteBufAllocator alloc, Settings settings) {
        boolean useNewlines = settings.getBoolean("cursorwriter.newlines", false);
        open = alloc.buffer(2).writeBytes((useNewlines ? "[\n" : "[").getBytes(CharsetUtil.US_ASCII));
        comma = alloc.buffer(2).writeBytes((useNewlines ? ",\n" : ",").getBytes(CharsetUtil.US_ASCII));
        close = alloc.buffer(2).writeBytes((useNewlines ? "]\n" : "]").getBytes(CharsetUtil.US_ASCII));
    }

    ByteBuf open() {
        return open.duplicate();
    }

    ByteBuf comma() {
        return comma.duplicate();
    }

    ByteBuf close() {
        return close.duplicate();
    }

}
