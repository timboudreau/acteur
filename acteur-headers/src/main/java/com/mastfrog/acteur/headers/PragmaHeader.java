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

import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import io.netty.util.AsciiString;

/**
 * Handles the HTTP 1.0 <code>Pragma: no-cache</code> header style.
 *
 * @author Tim Boudreau
 */
final class PragmaHeader extends AbstractHeader<CacheControl> {

    static final AsciiString PRAGMA = AsciiString.cached("pragma");
    private static final AsciiString NO_CACHE = AsciiString.cached("no-cache");
    private static final CacheControl RESP = CacheControl.$(CacheControlTypes.no_cache);

    PragmaHeader() {
        super(CacheControl.class, PRAGMA);
    }

    @Override
    public CacheControl toValue(CharSequence value) {
        if (NO_CACHE.contentEqualsIgnoreCase(value)) {
            return RESP;
        }
        return null;
    }

}
