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
package com.mastfrog.acteur.headers;

import com.mastfrog.util.Strings;
import io.netty.util.AsciiString;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A range of bytes within some number of servable bytes.
 *
 * @see Range.toBoundedRange(long,long,long)
 *
 * @author Tim Boudreau
 */
public final class BoundedRange {

    private long start;
    private long end;
    private long of;

    private boolean valid;

    public BoundedRange(long start, long end) {
        this(start, end, -1);
    }

    public BoundedRange(long start, long end, long of) {
        if (end < start) {
            throw new IllegalArgumentException("End less than start: " + start + " end: " + end);
        }
        if ((of < start || of < end) && of != -1) {
            throw new IllegalArgumentException("Total bytes less than end");
        } else {
            valid = true;
        }
        this.start = start;
        this.end = end;
        this.of = of;
    }
    
    public long length() {
        return end - start;
    }

    private final Pattern RANGE_PATTERN = Pattern.compile("bytes (\\d+)-(\\d+)\\/([\\d\\*]+)");
    private final Pattern INVALID_RANGE_PATTERN = Pattern.compile("bytes \\*\\/(\\d+)");

    public BoundedRange(CharSequence value) {
        value = Strings.trim(value);
        Matcher m = RANGE_PATTERN.matcher(value);
        if (m.find()) {
            try {
                start = Long.parseLong(m.group(1));
                end = Long.parseLong(m.group(2));
                if ("*".equals(m.group(3))) {
                    of = -1;
                } else {
                    of = Long.parseLong(m.group(3));
                }
                valid = start < end && of >= end;
            } catch (NumberFormatException nfe) {
                valid = false;
            }
        } else {
            m = INVALID_RANGE_PATTERN.matcher(value);
            if (m.find()) {
                start = -1;
                end = -1;
                try {
                    of = Long.parseLong(m.group(1));
                    valid = true;
                } catch (NumberFormatException nfe) {
                    valid = false;
                }
            } else {
                valid = false;
                start = -1;
                end = -1;
                of = -1;
            }
        }
    }

    public static BoundedRange unsatisfiable(long availableBytes) {
        return new BoundedRange(-1, -1, availableBytes);
    }

    public boolean isRangeNotSatisfiable() {
        return start == -1L && end == -1L && of > 0;
    }

    public boolean isValid() {
        return valid;
    }

    public long start() {
        return start;
    }

    public long end() {
        return end;
    }

    public long of() {
        return of;
    }
    
    public CharSequence toCharSequence() {
        if (start == -1L && end == -1L) {
            AsciiString.of("bytes */" + of);
        }
        return AsciiString.of("bytes " + start + "-" + end + "/" + (of == -1L ? "*" : of));
        
    }

    @Override
    public String toString() {
        if (start == -1L && end == -1L) {
            return "bytes */" + of;
        }
        return "bytes " + start + "-" + end + "/" + (of == -1L ? "*" : of);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof BoundedRange) {
            BoundedRange br = (BoundedRange) o;
            return br.start == start && br.end == end && br.of == of;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new long[]{start, end, of});
    }
}
