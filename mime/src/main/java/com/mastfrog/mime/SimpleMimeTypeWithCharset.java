/*
 * The MIT License
 *
 * Copyright 2022 Tim Boudreau.
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
package com.mastfrog.mime;

import java.nio.charset.Charset;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleMimeTypeWithCharset extends MimeType {

    private final String primaryType;
    private final String subtype;
    private final Charset charset;
    private final String variant;
    private transient String stringValue;

    SimpleMimeTypeWithCharset(String primaryType, String subtype,
            String variant, Charset charset) {
        if (primaryType == null) {
            throw new IllegalArgumentException(primaryType);
        }
        this.primaryType = primaryType.trim();
        this.subtype = subtype == null ? null : subtype.trim();
        this.variant = variant == null ? null : variant.trim();
        this.charset = charset;
    }

    @Override
    boolean hasParams() {
        return false;
    }

    @Override
    public String primaryType() {
        return primaryType;
    }

    @Override
    public Optional<CharSequence> secondaryType() {
        return Optional.ofNullable(subtype);
    }

    @Override
    public Optional<CharSequence> variant() {
        return Optional.ofNullable(variant);
    }

    @Override
    public Optional<Charset> charset() {
        return Optional.ofNullable(charset);
    }

    @Override
    public String toString() {
        if (stringValue != null) {
            return stringValue;
        }
        int ct = primaryType.length() + (subtype == null ? 0 : subtype.length() + 1)
                + (charset == null ? 0 : 2 + charset.name().length());

        StringBuilder result = new StringBuilder(ct);
        result.append(primaryType);
        if (subtype != null) {
            result.append('/')
                    .append(subtype);
        }
        if (variant != null) {
            result.append('+')
                    .append(variant);
        }
        if (charset != null) {
            result.append(';')
                    .append("charset=")
                    .append(charset.name().toLowerCase());
        }
        return stringValue = result.toString();
    }

}
