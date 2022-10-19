/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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

package com.mastfrog.acteur.headers;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.util.AsciiString;
import java.io.File;
import java.nio.file.Path;

/**
 *
 * @author Tim Boudreau
 */
public final class BoundedRangeNetty extends BoundedRange {

    public BoundedRangeNetty(long start, long end) {
        super(start, end);
    }

    public BoundedRangeNetty(long start, long end, long of) {
        super(start, end, of);
    }

    public BoundedRangeNetty(CharSequence value) {
        super(value);
    }

    public FileRegion toRegion(File f) {
        long st = start();
        return new DefaultFileRegion(f, st, (end() + 1) - st);
    }

    public FileRegion toRegion(Path f) {
        long st = start();
        return new DefaultFileRegion(f.toFile(), st, (end() + 1) - st);
    }

    public CharSequence toCharSequence() {
        long start = start();
        long end = end();
        long of = of();
        if (start == -1L && end == -1L) {
            AsciiString.of("bytes */" + of);
        }
        return AsciiString.of("bytes " + start + "-" + end + "/" + (of == -1L ? "*" : of));
    }

}
