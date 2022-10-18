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
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import static java.util.Collections.emptyMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleMimeTypeWithParams extends MimeType {

    private final String primaryType;
    private final String subtype;
    private final String variant;
    private final Map<? extends CharSequence, ? extends CharSequence> params;
    private transient Optional<Charset> charset;

    SimpleMimeTypeWithParams(String primaryType, String subtype,
            String variant,
            Map<? extends CharSequence, ? extends CharSequence> params) {
        if (primaryType == null) {
            throw new IllegalArgumentException(primaryType);
        }
        this.primaryType = primaryType;
        this.subtype = subtype;
        this.variant = variant;
        this.params = params;
    }

    SimpleMimeTypeWithParams(MimeType orig, String newKey, String newVal) {
        this.primaryType = orig.primaryType().toString();
        subtype = orig.secondaryType().map(CharSequence::toString).orElse(null);
        variant = orig.variant().map(CharSequence::toString).orElse(null);
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<? extends CharSequence, ? extends CharSequence> p : orig.parameters()) {
            if (!charSequencesEqual(newKey, p.getKey())) {
                params.put(p.getKey().toString(), Objects.toString(p.getValue()));
            }
        }
        params.put(newKey, newVal);
        this.params = params;
    }

    SimpleMimeTypeWithParams(MimeType orig) {
        this.primaryType = orig.primaryType().toString();
        subtype = orig.secondaryType().map(CharSequence::toString).orElse(null);
        variant = orig.variant().map(CharSequence::toString).orElse(null);
        List<Map.Entry<? extends CharSequence, ? extends CharSequence>> ops
                = orig.parameters();
        if (ops.isEmpty()) {
            this.params = emptyMap();
        } else {
            Map<String, String> params = new LinkedHashMap<>();
            for (Map.Entry<? extends CharSequence, ? extends CharSequence> p : ops) {
                params.put(p.getKey().toString(), Objects.toString(p.getValue()));
            }
            this.params = params;
        }
    }

    @Override
    boolean hasParams() {
        if (params.isEmpty() || (params.size() == 1
                && params.containsKey("charset"))) {
            return false;
        }
        return true;
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
    public List<Map.Entry<? extends CharSequence, ? extends CharSequence>> parameters() {
        return new ArrayList<>(params.entrySet());
    }

    @Override
    public Optional<Charset> charset() {
        if (charset != null) {
            return charset;
        }
        CharSequence cs = params.get("charset");
        if (cs != null) {
            return charset = Optional.of(findCharset(cs.toString()));
        }
        return charset = Optional.empty();
    }

}
