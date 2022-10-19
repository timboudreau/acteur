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
import static java.util.Collections.singletonList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A mime type, which can be built explicitly or parsed. Originally Acteur used
 * Guava's MediaType, which is a very large library to pull in as a dependency
 * for a very simple thing.
 *
 * @author Tim Boudreau
 */
public abstract class MimeType implements Comparable<MimeType>, Supplier<Charset> {

    private static final String IMAGE = "image";
    private static final String TEXT = "text";
    private static final String APPLICATION = "application";
    public static final MimeType ANY_TYPE = new SimpleMimeTypeWithCharset("*", "*", null, null);
    public static final MimeType PLAIN_TEXT_UTF_8 = new SimpleMimeTypeWithCharset(TEXT, "plain", null, UTF_8);
    public static final MimeType JSON_UTF_8 = new SimpleMimeTypeWithCharset(APPLICATION, "json ", null, UTF_8);
    public static final MimeType HTML_UTF_8 = new SimpleMimeTypeWithCharset(TEXT, "html", null, UTF_8);
    public static final MimeType TEXT_JAVASCRIPT_UTF_8 = new SimpleMimeTypeWithCharset(TEXT, "javascript", null, UTF_8);
    public static final MimeType CSS_UTF_8 = new SimpleMimeTypeWithCharset(TEXT, "css", null, UTF_8);
    public static final MimeType CSV_UTF_8 = new SimpleMimeTypeWithCharset(TEXT, "csv", null, UTF_8);
    public static final MimeType XML_UTF_8 = new SimpleMimeTypeWithCharset(TEXT, "xml", null, UTF_8);
    public static final MimeType FORM_DATA = create(APPLICATION, "x-www-form-urlencoded");
    public static final MimeType UNKNOWN = new SimpleMimeTypeWithCharset(APPLICATION, "unknown", null, null);
    public static final MimeType OCTET_STREAM = new SimpleMimeTypeWithCharset(APPLICATION, "octet-stream", null, null);
    public static final MimeType PNG = MimeType.builder(IMAGE).withSecondaryType("png").build();
    public static final MimeType GIF = MimeType.builder(IMAGE).withSecondaryType("gif").build();
    public static final MimeType JPEG = MimeType.builder(IMAGE).withSecondaryType("jpeg").build();
    public static final MimeType TIFF = MimeType.builder(IMAGE).withSecondaryType("tiff").build();
    public static final MimeType BMP = MimeType.builder(IMAGE).withSecondaryType("bmp").build();
    public static final MimeType ICON = MimeType.builder(IMAGE).withSecondaryType("vnd.microsoft.icon").build();
    public static final MimeType PDF = MimeType.builder(APPLICATION).withSecondaryType("pdf").build();
    public static final MimeType SVG = MimeType.builder(IMAGE).withSecondaryType("xml").withVariant("svg")
            .withCharset(UTF_8).build();
    public static final MimeType XHTML_UTF_8 = MimeType.builder(APPLICATION).withSecondaryType("xhtml+xml")
            .withCharset(UTF_8).build();
    public static final MimeType JAVA_PROPERTIES = MimeType.create(TEXT, "x-java-properties");
    public static final MimeType GZIP = MimeType.create(APPLICATION, "x-gzip");
    public static final MimeType EVENT_STREAM = new SimpleMimeTypeWithCharset("text", "event-stream", null, UTF_8);

    private static final Map<String, Charset> CHARSETS
            = new ConcurrentHashMap<>(8);

    MimeType() {
        // package private
    }

    static Charset findCharset(String what) {
        // With JDK9 we can use Charset.forName(string, charset)
        return CHARSETS.computeIfAbsent(what, cs -> {
            try {
                return Charset.forName(what);
            } catch (Exception ex) {
                return UTF_8;
            }
        });
    }

    public static MimeType create(String primary, String secondary) {
        return new SimpleMimeTypeWithCharset(primary, secondary, null, null);
    }

    public static MimeType parse(CharSequence seq) {
        return MimeTypeParser.parse(seq);
    }

    /**
     * Get the primary type - e.g. "text" for "text/x-java".
     *
     * @return The primary type
     */
    public abstract CharSequence primaryType();

    /**
     * Get the secondary type if there is one - for "text/x-java", this would be
     * "x-java".
     *
     * @return The secondary type
     */
    public abstract Optional<CharSequence> secondaryType();

    public boolean isPrimaryType(String primary) {
        return charSequencesEqual(primary, this.primaryType());
    }

    public boolean isSecondaryType(String secondary) {
        Optional<CharSequence> sec = secondaryType();
        if (secondary == null || secondary.isEmpty()) {
            return !sec.isPresent();
        }
        if (sec.isPresent()) {
            return charSequencesEqual(secondary, sec.get());
        }
        return false;
    }

    /**
     * Get the variant - the part of the secondary type after the "+" - e.g. for
     * "text/x-java+jdk9", return "jdk9".
     *
     * @return A variant if one is present
     */
    public Optional<CharSequence> variant() {
        return Optional.empty();
    }

    /**
     * Get the character set, if there is one.
     *
     * @return A character set
     */
    public Optional<Charset> charset() {
        return Optional.empty();
    }

    /**
     * Determine if this instance has parameters *other than charset*.
     *
     * @return true if there are parameters
     */
    abstract boolean hasParams();

    /**
     * Create a new mime type from this one with an added parameter.
     *
     * @param newKey A key
     * @param newVal A value
     * @return
     */
    public MimeType withParameter(String newKey, String newVal) {
        if (newKey == null) {
            throw new IllegalArgumentException("Key is null");
        }
        if (newVal == null) {
            throw new IllegalArgumentException("Value is null");
        }
        return new SimpleMimeTypeWithParams(this, newKey, newVal);
    }

    /**
     * Create a new mime type from this one with the passed variant.
     *
     * @param variant a variant
     * @return A mime type
     */
    public MimeType withVariant(String variant) {
        if (variant != null) {
            throw new IllegalArgumentException("Variant may not be null");
        }
        return toBuilder().withVariant(variant).build();
    }

    /**
     * Create a new mime type from this one with the passed character set.
     *
     * @param cs A character set
     * @return A mime type
     */
    public MimeType withCharset(Charset cs) {
        Optional<Charset> mine = charset();
        if (mine.isPresent() && cs.equals(mine.get())) {
            return this;
        }
        if (!hasParams()) {
            return new SimpleMimeTypeWithCharset(primaryType().toString(),
                    secondaryType().map(CharSequence::toString).orElse(null),
                    variant().map(CharSequence::toString).orElse(null),
                    cs);
        }
        return withParameter("charset", cs.name().toLowerCase());
    }

    /**
     * Get the set of components that differ between this mime type and another.
     *
     * @param other A mime type
     * @return A set of components - will be empty if the mime types are equal
     */
    public final Set<MimeTypeComponent> differingComponents(MimeType other) {
        Set<MimeTypeComponent> result = EnumSet.noneOf(MimeTypeComponent.class);
        MimeTypeComponent.eachComponent(cmp -> {
            if (!cmp.isSame(this, other)) {
                result.add(cmp);
            }
        });
        return result;
    }

    /**
     * Returns true if the components passed are the same in this mime type and
     * the passed one.
     *
     * @param other Another mime type
     * @param components Some components - must be > one
     * @return Whether or not those components of the two mime types are equal
     */
    public final boolean isSameAs(MimeType other, MimeTypeComponent... components) {
        if (components.length == 0) {
            throw new IllegalArgumentException("All types are the same when comparing "
                    + "on 0 components.");
        }
        for (int i = 0; i < components.length; i++) {
            if (!components[i].isSame(this, other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the set of parameters that belong to this mime type - this is
     * key-value pairs following the first unquoted ';' character.
     *
     * @return A list of key/value pairs
     */
    public List<Map.Entry<? extends CharSequence, ? extends CharSequence>> parameters() {
        Optional<Charset> cs = charset();
        if (cs.isPresent()) {
            return singletonList(new FakeMapEntry("charset",
                    cs.get().name().toLowerCase()));
        }
        return emptyList();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof MimeType)) {
            return false;
        }
        MimeType mt = (MimeType) o;
        if (!primaryType().equals(mt.primaryType())) {
            return false;
        }
        return secondaryType().equals(mt.secondaryType())
                && variant().equals(mt.variant())
                && parameters().equals(mt.parameters());
    }

    @Override
    public int hashCode() {
        return primaryType().hashCode()
                + (71 * hc(secondaryType()))
                + (3 * hc(variant()))
                + parameters().hashCode();
    }

    /**
     * Convert to a character sequence; depending on the internal implementation
     * used, the returned object may not be a string.
     *
     * @return A parsable representation of this mime type
     */
    public CharSequence toCharSequence() {
        return toString();
    }

    /**
     * For API compatibility with Guava's MediaType.
     *
     * @return A character set or null
     */
    public Charset get() {
        return charset().orElse(null);
    }

    private static <T> int hc(Optional<T> opt) {
        return opt.isPresent() ? opt.get().hashCode() : 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(primaryType());
        secondaryType().ifPresent(tp -> {
            sb.append('/').append(tp);
        });
        variant().ifPresent(v -> {
            sb.append('+').append(v);
        });
        for (Map.Entry<? extends CharSequence, ? extends CharSequence> e : parameters()) {
            sb.append(';').append(e.getKey()).append('=');
            String v = e.getValue().toString();
            if (v.indexOf(';') >= 0 || v.indexOf(' ') >= 0) {
                sb.append('"').append(v).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    @Override
    public int compareTo(MimeType o) {
        return toString().compareToIgnoreCase(o.toString());
    }

    static Map.Entry<? extends CharSequence, ? extends CharSequence> mapEntry(String k, String v) {
        return new FakeMapEntry(k, v);
    }

    private static class FakeMapEntry implements Map.Entry<CharSequence, CharSequence> {

        private final String key;
        private final String val;

        FakeMapEntry(String key, String val) {
            this.key = key;
            this.val = val;
        }

        @Override
        public CharSequence getKey() {
            return key;
        }

        @Override
        public CharSequence getValue() {
            return val;
        }

        @Override
        public String setValue(CharSequence value) {
            throw new UnsupportedOperationException("Read-only.");
        }

        @Override
        public String toString() {
            return key + "=" + val;
        }

        @Override
        public int hashCode() {
            int h;
            return (h = charSequenceHashCode(getKey(), true)) ^ (h >>> 16);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null || !(obj instanceof Map.Entry<?, ?>)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) obj;
            Object ok = e.getKey();
            Object ov = e.getValue();
            if (ok instanceof CharSequence && ov instanceof CharSequence) {
                CharSequence cok = (CharSequence) ok;
                CharSequence cov = (CharSequence) ov;
                return charSequencesEqual(getKey(), cok);
            }
            return false;
        }
    }

    static boolean charSequencesEqual(CharSequence a, CharSequence b) {
        int lenA = a.length();
        int lenB = b.length();
        if (lenA != lenB) {
            return false;
        }
        lenB--;
        char ca, cb;
        for (int ixa = 0; ixa < lenA; ixa++) {
            int ixb = lenB - ixa;
            if (ixb < ixa) {
                break;
            }
            ca = a.charAt(ixa);
            cb = b.charAt(ixa);
            if (ca != cb) {
                return false;
            }
            ca = a.charAt(ixb);
            cb = b.charAt(ixb);
            if (ca != cb) {
                return false;
            }
        }
        return true;
    }

    static int charSequenceHashCode(CharSequence seq, boolean ignoreCase) {
        // Same computation as java.lang.String for case sensitive
        int length = seq.length();
        if (length == 0) {
            return 0;
        }
        int result = 0;
        for (int i = 0; i < length; i++) {
            if (ignoreCase) {
                result = 31 * result + Character.toLowerCase(seq.charAt(i));
            } else {
                result = 31 * result + seq.charAt(i);
            }
        }
        return result;
    }

    static boolean charSequenceContains(CharSequence seq, char what) {
        int len = seq.length();
        for (int i = 0; i < len; i++) {
            if (what == seq.charAt(i)) {
                return true;
            }
        }
        return false;
    }

    static boolean charSequenceContains(int start, int end, CharSequence seq, char what) {
        int len = seq.length();
        for (int i = start; i < end; i++) {
            if (what == seq.charAt(i)) {
                return true;
            }
        }
        return false;
    }

    public static Builder builder(String primary) {
        return new Builder(primary);
    }

    public Builder toBuilder() {
        Builder b = builder(primaryType().toString());
        secondaryType().ifPresent(sec -> b.withSecondaryType(sec.toString()));
        variant().ifPresent(v -> b.withVariant(v.toString()));
        charset().ifPresent(cs -> b.withCharset(cs));
        for (Map.Entry<? extends CharSequence, ? extends CharSequence> p : parameters()) {
            if (!charSequencesEqual("charset", p.getKey())) {
                b.withParameter(p.getKey(), p.getValue());
            }
        }
        return b;
    }

    public static final class Builder {

        private final List<Map.Entry<? extends CharSequence, ? extends CharSequence>> params
                = new ArrayList<>();

        private final String primary;
        private String secondary;
        private String variant;
        private Charset charset;

        Builder(String primary) {
            this.primary = primary;
        }

        public Builder withSecondaryType(String sec) {
            this.secondary = secondary;
            return this;
        }

        public Builder withVariant(String variant) {
            this.variant = variant;
            return this;
        }

        public Builder withCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder withParameter(CharSequence key, CharSequence val) {
            if (charSequencesEqual("charset", key)) {
                return withCharset(findCharset(val.toString()));
            }
            return this;
        }

        public MimeType build() {
            SimpleMimeTypeWithCharset result = new SimpleMimeTypeWithCharset(primary, secondary, variant, charset);
            if (params.isEmpty()) {
                return result;
            }
            Map<CharSequence, CharSequence> map = new HashMap<>();
            for (Map.Entry<? extends CharSequence, ? extends CharSequence> e : params) {
                map.put(e.getKey(), e.getValue());
            }
            if (charset != null) {
                map.put("charset", charset.name().toLowerCase());
            }
            return new SimpleMimeTypeWithParams(primary, secondary, variant, map);
        }

    }
}
