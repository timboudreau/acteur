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
package com.mastfrog.acteur.util;

import static com.mastfrog.acteur.util.FrameOptions.FrameOptionType.ALLOW_FROM;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.net.URI;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public final class FrameOptions {

    private final FrameOptionType type;
    private final URI value;

    public static final FrameOptions DENY = new FrameOptions(FrameOptionType.DENY);
    public static final FrameOptions SAMEORIGIN = new FrameOptions(FrameOptionType.SAMEORIGIN);

    FrameOptions(FrameOptionType type) {
        this(type, null);
    }

    FrameOptions(FrameOptionType type, URI value) {
        this.type = notNull("type", type);
        if (!type.takesValue && value != null) {
            throw new IllegalArgumentException(type + " does not take a value");
        } else if (type.takesValue && value == null) {
            throw new IllegalArgumentException(type + " requires a value");
        }
        this.value = value;
    }

    public FrameOptionType type() {
        return type;
    }

    public URI uri() {
        return value;
    }

    public String name() {
        return type.name();
    }

    public String toString() {
        return type + (value == null ? "" : " " + value.toASCIIString());
    }

    public static FrameOptions allowFrom(URI uri) {
        return new FrameOptions(FrameOptionType.ALLOW_FROM, notNull("uri", uri));
    }

    public static FrameOptions parse(CharSequence seq) {
        Set<CharSequence> s = Strings.splitUniqueNoEmpty(' ', notNull("seq", seq));
        if (s.size() > 2) {
            throw new IllegalArgumentException("FrameOptions should be "
                    + "DENY, SAMEORIGIN or ALLOW-FROM $uri.  Found " 
                    + s.size() + " elements in '" + seq + "'");
        }
        Iterator<CharSequence> it = s.iterator();
        if (!it.hasNext()) {
            return null;
        }
        CharSequence first = it.next();
        outer:
        for (FrameOptionType ft : FrameOptionType.values()) {
            if (Strings.charSequencesEqual(ft.toString(), first, true)) {
                if (ft.takesValue != it.hasNext()) {
                    if (it.hasNext()) {
                        throw new IllegalArgumentException(ft + " does not take a value");
                    } else {
                        throw new IllegalArgumentException(ft + " must be followed by a uri");
                    }
                }
                switch(ft) {
                    case SAMEORIGIN :
                        return SAMEORIGIN;
                    case DENY :
                        return DENY;
                    case ALLOW_FROM :
                        URI uri = URI.create(it.next().toString());
                        return new FrameOptions(ALLOW_FROM, uri);
                    default :
                        throw new AssertionError(ft);
                }
            }
        }
        throw new IllegalArgumentException("Could not parse '" + seq + "'");
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof FrameOptions) {
            FrameOptions fo = (FrameOptions) o;
            return type == fo.type && Objects.equals(fo.value, value);
        }
        return false;
    }

    public int hashCode() {
        return (type.ordinal() + 1) * (value == null ? 1 : value.hashCode());
    }

    public enum FrameOptionType {
        DENY(false),
        SAMEORIGIN(false),
        ALLOW_FROM(true);
        private final boolean takesValue;
        private String headerField;

        private FrameOptionType(boolean takesValue) {
            this.takesValue = takesValue;
        }

        @Override
        public String toString() {
            if (headerField == null) {
                headerField = name().replace('_', '-');
            }
            return headerField;
        }
    }
}
