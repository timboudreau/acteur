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

import static com.mastfrog.mime.MimeType.charSequenceContains;
import java.util.Optional;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
public enum MimeTypeComponent {
    PRIMARY_TYPE, SECONDARY_TYPE, VARIANT, CHARACTER_SET, PARAMETERS;

    static void eachComponent(Consumer<? super MimeTypeComponent> c) {
        c.accept(PRIMARY_TYPE);
        c.accept(SECONDARY_TYPE);
        c.accept(VARIANT);
        c.accept(CHARACTER_SET);
        c.accept(PARAMETERS);
    }

    public boolean isSame(MimeType a, MimeType b) {
        return get(a).equals(get(b));
    }

    public Optional<CharSequence> get(MimeType type) {
        switch (this) {
            case PRIMARY_TYPE:
                return Optional.of(type.primaryType());
            case SECONDARY_TYPE:
                return type.secondaryType();
            case VARIANT:
                return type.variant();
            case CHARACTER_SET:
                return type.charset().map(cs -> cs.name().toLowerCase());
            case PARAMETERS:
                StringBuilder sb = new StringBuilder();
                type.parameters().forEach(entry -> {
                    if ("charset".equals(entry.getKey())) {
                        return;
                    }
                    if (sb.length() > 0) {
                        sb.append(';');
                    }
                    sb.append(entry.getKey()).append('=');
                    CharSequence v = entry.getValue();
                    if (charSequenceContains(v, '=') || charSequenceContains(v, ' ')) {
                        sb.append('"').append(v).append('"');
                    } else {
                        sb.append(v);
                    }
                });
                return Optional.of(sb.toString());
            default:
                throw new AssertionError(this);
        }
    }

}
