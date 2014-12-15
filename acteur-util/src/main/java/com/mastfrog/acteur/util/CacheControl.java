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
package com.mastfrog.acteur.util;

import static com.mastfrog.acteur.util.CacheControlTypes.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
public final class CacheControl {

    private final List<E> entries = new ArrayList<>();
    public static CacheControl PUBLIC_MUST_REVALIDATE
            = new CacheControl(Public, must_revalidate);
    public static CacheControl PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY
            = new CacheControl(Public, must_revalidate).add(max_age, Duration.standardDays(1));
    public static CacheControl PRIVATE_NO_CACHE_NO_STORE 
            = new CacheControl(Private, no_cache, no_store);

    public CacheControl(CacheControlTypes... types) {
        for (CacheControlTypes c : types) {
            add(c);
        }
    }

    public static CacheControl $(CacheControlTypes types) {
        return new CacheControl(types);
    }
    private final DateTime creationTime = new DateTime();

    public boolean isExpired() {
        if (contains(CacheControlTypes.no_cache) || contains(CacheControlTypes.no_store)) {
            return true;
        }
        Long maxAgeSeconds = get(CacheControlTypes.max_age);
        if (maxAgeSeconds != null) {
            Duration dur = new Duration(new DateTime(), creationTime);
            Duration target = Duration.standardSeconds(maxAgeSeconds);
            if (dur.isLongerThan(target)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 7;
        for (E e : entries) {
            result += 79 * e.hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CacheControl) {
            Set<E> a = new HashSet<>(entries);
            Set<E> b = new HashSet<>(((CacheControl) o).entries);
            return a.equals(b);
        }
        return false;
    }

    public CacheControl add(CacheControlTypes... types) {
        for (CacheControlTypes type : types) {
            _add(type);
        }
        return this;
    }

    public boolean contains(CacheControlTypes type) {
        for (E e : entries) {
            if (e.type == type) {
                return true;
            }
        }
        return false;
    }

    public long get(CacheControlTypes type) {
        if (!type.takesValue) {
            throw new IllegalArgumentException(type + " does not take a value");
        }
        for (E e : entries) {
            if (e.type == type) {
                return e.value;
            }
        }
        return -1;
    }

    void _add(CacheControlTypes type) {
        if (type.takesValue) {
            throw new IllegalArgumentException(type + " requires a value");
        }
        for (Iterator<E> it = entries.iterator(); it.hasNext();) {
            E e = it.next();
            if (e.type == type) {
                it.remove();
            }
        }
        entries.add(new E(type));
    }

    public CacheControl add(CacheControlTypes type, Duration value) {
        if (!type.takesValue) {
            throw new IllegalArgumentException(type + " requires a value");
        }
        for (Iterator<E> it = entries.iterator(); it.hasNext();) {
            E e = it.next();
            if (e.type == type) {
                it.remove();
            }
        }
        entries.add(new E(type, value.toStandardSeconds().getSeconds()));
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<E> it = entries.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static CacheControl fromString(CharSequence s) {
        CacheControl result = new CacheControl();
        String[] parts = s.toString().split(",");
        for (String part : parts) {
            part = part.trim();
            CacheControlTypes t = CacheControlTypes.find(part);
            if (t != null) {
                if (t.takesValue) {
                    String[] sides = part.split("=", 2);
                    if (sides.length == 2) {
                        try {
                            long val = Long.parseLong(sides[1]);
                            result.entries.add(new E(t, val));
                        } catch (NumberFormatException nfe) {
                            nfe.printStackTrace();
                            Logger.getLogger(CacheControl.class.getName()).log(Level.INFO, "Bad number in cache control header", nfe);
                        }
                    }
                } else {
                    result.add(t);
                }
            } else {
                System.err.println("Unrecognized: " + part);
            }
        }
        return result;
    }

    private static class E {

        private final CacheControlTypes type;
        private final long value;

        E(CacheControlTypes type) {
            this(type, -1);
        }

        E(CacheControlTypes type, long value) {
            this.type = type;
            this.value = value;
            assert type.takesValue || value == -1;
        }

        @Override
        public String toString() {
            if (type.takesValue) {
                return type + "=" + value;
            } else {
                return type.toString();
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof E && ((E) o).type == type && (!type.takesValue || (((E) o).value == value));
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 23 * hash + this.type.hashCode();
            hash = 23 * hash + (int) (this.value ^ (this.value >>> 32));
            return hash;
        }
    }
}
