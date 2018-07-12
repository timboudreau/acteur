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
package com.mastfrog.acteur.headers;

import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.util.Checks;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

/**
 *
 * @author Tim Boudreau
 */
final class CacheControlHeader extends AbstractHeader<CacheControl> {
    private static final AsciiString PRIVATE_NO_CACHE_NO_STORE = AsciiString.of(CacheControl.PRIVATE_NO_CACHE_NO_STORE.toString());
    private static final AsciiString PUBLIC = AsciiString.of(CacheControl.PUBLIC.toString());
    private static final AsciiString PUBLIC_MUST_REVALIDATE = AsciiString.of(CacheControl.PUBLIC_MUST_REVALIDATE.toString());
    private static final AsciiString PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY = AsciiString.of(CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY.toString());
    private static final AsciiString PUBLIC_MUST_REVALIDATE_MAX_AGE_TEN_YEARS = AsciiString.of(CacheControl.PUBLIC_MAX_AGE_TEN_YEARS.toString());
    private static final AsciiString PUBLIC_MAX_AGE_TEN_YEARS = AsciiString.of(CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_TEN_YEARS.toString());
    private static final AsciiString PUBLIC_IMMUTABLE = AsciiString.of(CacheControl.PUBLIC_IMMUTABLE.toString());

    CacheControlHeader() {
        super(CacheControl.class, HttpHeaderNames.CACHE_CONTROL);
    }

    @Override
    public String toString(CacheControl value) {
        Checks.notNull("value", value);
        return value.toString();
    }

    @Override
    public CharSequence toCharSequence(CacheControl value) {
        // Avoid parsing common values
        if (value == CacheControl.PRIVATE_NO_CACHE_NO_STORE) {
            return PRIVATE_NO_CACHE_NO_STORE;
        } else if (value == CacheControl.PUBLIC) {
            return PUBLIC;
        } else if (value == CacheControl.PUBLIC_MUST_REVALIDATE) {
            return PUBLIC_MUST_REVALIDATE;
        } else if (value == CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY) {
            return PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY;
        } else if (value == CacheControl.PUBLIC_MAX_AGE_TEN_YEARS) {
            return PUBLIC_MAX_AGE_TEN_YEARS;
        } else if (value == CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_TEN_YEARS) {
            return PUBLIC_MUST_REVALIDATE_MAX_AGE_TEN_YEARS;
        } else if (value == CacheControl.PUBLIC_IMMUTABLE) {
            return PUBLIC_IMMUTABLE;
        }
        return toString(value);
    }

    @Override
    public CacheControl toValue(CharSequence value) {
        Checks.notNull("value", value);
        return CacheControl.fromString(value);
    }

}
