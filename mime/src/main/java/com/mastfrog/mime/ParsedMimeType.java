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
import static java.util.Collections.emptyList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
final class ParsedMimeType extends MimeType {

    private final CharSequence seq;
    private final int slashLoc;
    private final int plusLoc;
    private final int semiLoc;
    private transient Optional<Charset> charset;

    ParsedMimeType(CharSequence seq, int slashLoc, int plusLoc, int semiLoc) {
        this.seq = seq;
        this.slashLoc = slashLoc;
        this.plusLoc = plusLoc;
        this.semiLoc = semiLoc;
    }

    boolean hasParams() {
        return semiLoc >= 0;
    }

    public String toString() {
        return seq.toString();
    }

    @Override
    public CharSequence primaryType() {
        if (slashLoc < 0) {
            if (semiLoc < 0) {
                return seq;
            } else {
                return seq.subSequence(0, semiLoc);
            }
        } else {
            return seq.subSequence(0, slashLoc);
        }
    }

    @Override
    public Optional<CharSequence> secondaryType() {
        if (slashLoc < 0) {
            return Optional.empty();
        }
        int len = seq.length();
        int end = plusLoc > 0 ? plusLoc : semiLoc > 0 ? semiLoc : len;
        int sstart = slashLoc + 1;
        if (end <= sstart) {
            return Optional.empty();
        }
        return Optional.of(seq.subSequence(sstart, end));
    }

    @Override
    public Optional<CharSequence> variant() {
        if (plusLoc < 0) {
            return Optional.empty();
        }
        int len = seq.length();
        int end = semiLoc > 0 ? semiLoc : len;
        if (end < len - 1 && plusLoc < end - 1) {
            return Optional.of(seq.subSequence(plusLoc + 1, end));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Charset> charset() {
        if (semiLoc < 0) {
            return Optional.empty();
        }
        if (charset != null) {
            return charset;
        }
        for (Map.Entry<? extends CharSequence, ? extends CharSequence> p : parameters()) {
            if (charSequencesEqual("charset", p.getKey())) {
                String cs = p.getValue().toString();
                return charset = Optional.of(findCharset(cs.toString()));
            }
        }
        return charset = Optional.empty();
    }

    @Override
    public List<Map.Entry<? extends CharSequence, ? extends CharSequence>> parameters() {
        if (semiLoc < 0) {
            return emptyList();
        }
        List<Map.Entry<? extends CharSequence, ? extends CharSequence>> result
                = new ArrayList<>(3);
        int currStart = semiLoc + 1;
        int len = seq.length();
        boolean inQuotes = false;
        for (int i = currStart; i < len; i++) {
            char c = seq.charAt(i);
            if (c == '"') {
                if (inQuotes) {
                    inQuotes = false;
                } else {
                    inQuotes = true;
                }
                continue;
            }
            if (c == ';' && !inQuotes) {
                if (i > currStart) {
                    result.add(new Pair(currStart, i));
                    currStart = i + 1;
                }
            }
        }
        if (currStart < len && currStart < len) {
            result.add(new Pair(currStart, len));
        }
        return result;
    }

    private final class Pair implements Map.Entry<CharSequence, CharSequence> {

        private final int start;
        private final int end;

        Pair(int start, int end) {
            this.start = start;
            this.end = end;
        }

        CharSequence seq() {
            return ParsedMimeType.this.seq;
        }

        @Override
        public String toString() {
            return seq.subSequence(start, end).toString();
        }

        @Override
        public int hashCode() {
            int h;
            return (h = charSequenceHashCode(getKey(), true)) ^ (h >>> 16);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || !(o instanceof Map.Entry<?, ?>)) {
                return false;
            }
            if (o instanceof Pair) {
                Pair p = (Pair) o;
                if (p.seq() == seq) {
                    return p.start == start && p.end == end;
                }
            }
            if (o instanceof Map.Entry<?, ?>) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                Object ok = e.getKey();
                Object ov = e.getValue();
                if (ok instanceof CharSequence && ov instanceof CharSequence) {
                    CharSequence cok = (CharSequence) ok;
                    CharSequence cov = (CharSequence) ov;
                    return charSequencesEqual(getKey(), cok)
                            && charSequencesEqual(getValue(), cov);
                }
            }
            return false;
        }

        @Override
        public CharSequence getKey() {
            int keyEnd = start;
            for (int i = start; i < end; i++) {
                char c = seq.charAt(i);
                if (c == '=') {
                    keyEnd = i;
                    break;
                }
            }
            return seq.subSequence(start, keyEnd);
        }

        @Override
        public CharSequence getValue() {
            int valStart = end;
            for (int i = start; i < end; i++) {
                char c = seq.charAt(i);
                if (c == '=') {
                    valStart = i + 1;
                    break;
                }
            }
            if (valStart >= end) {
                return "";
            }
            while (valStart < end && seq.charAt(valStart) == '"') {
                valStart++;
            }
            int en = end;
            while (seq.charAt(en - 1) == '"') {
                en--;
            }
            if (en < valStart) {
                return "";
            }
            return seq.subSequence(valStart, en);
        }

        @Override
        public CharSequence setValue(CharSequence value) {
            throw new UnsupportedOperationException("Not writable.");
        }

    }

}
