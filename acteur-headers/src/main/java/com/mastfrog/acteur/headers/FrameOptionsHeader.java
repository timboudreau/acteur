/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import com.mastfrog.acteur.header.entities.FrameOptions;
import io.netty.util.AsciiString;

/**
 *
 * @author Tim Boudreau
 */
class FrameOptionsHeader extends AbstractHeader<FrameOptions> {

    private static final AsciiString X_FRAME_OPTIONS = AsciiString.of("x-frame-options");
    private static final AsciiString DENY = AsciiString.of("DENY");
    private static final AsciiString SAMEORIGIN = AsciiString.of("SAMEORIGIN");

    FrameOptionsHeader() {
        super(FrameOptions.class, X_FRAME_OPTIONS);
    }

    @Override
    public CharSequence toCharSequence(FrameOptions value) {
        return AsciiString.of(value.toString());
    }

    @Override
    public boolean is(CharSequence name) {
        return X_FRAME_OPTIONS.contentEqualsIgnoreCase(name);
    }

    @Override
    public FrameOptions toValue(CharSequence value) {
        if (value == DENY || DENY.contentEqualsIgnoreCase(value)) {
            return FrameOptions.DENY;
        } else if (value == SAMEORIGIN || SAMEORIGIN.contentEqualsIgnoreCase(value)) {
            return FrameOptions.SAMEORIGIN;
        }
        return FrameOptions.parse(value);
    }

}
