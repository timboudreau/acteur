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
package com.mastfrog.acteur.headers.jodatime;

import static com.mastfrog.acteur.headers.jodatime.JodaCacheControlTypes.Private;
import static com.mastfrog.acteur.headers.jodatime.JodaCacheControlTypes.Public;
import static com.mastfrog.acteur.headers.jodatime.JodaCacheControlTypes.max_age;
import static com.mastfrog.acteur.headers.jodatime.JodaCacheControlTypes.must_revalidate;
import static com.mastfrog.acteur.headers.jodatime.JodaCacheControlTypes.no_cache;
import static com.mastfrog.acteur.headers.jodatime.JodaCacheControlTypes.no_store;
import com.mastfrog.util.strings.Strings;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
public final class JodaCacheControl {

    private final List<E> entries = new ArrayList<>();
    public static JodaCacheControl PUBLIC_MUST_REVALIDATE
            = new JodaCacheControl(Public, must_revalidate);
    public static JodaCacheControl PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY
            = new JodaCacheControl(Public, must_revalidate).add(max_age, Duration.standardDays(1));
    public static JodaCacheControl PRIVATE_NO_CACHE_NO_STORE 
            = new JodaCacheControl(Private, no_cache, no_store);
    public static JodaCacheControl PUBLIC
            = new JodaCacheControl(Public);

    public JodaCacheControl(JodaCacheControlTypes... types) {
        for (JodaCacheControlTypes c : types) {
            add(c);
        }
    }

    public static JodaCacheControl $(JodaCacheControlTypes types) {
        return new JodaCacheControl(types);
    }
    private final DateTime creationTime = new DateTime();

    public boolean isExpired() {
        if (contains(JodaCacheControlTypes.no_cache) || contains(JodaCacheControlTypes.no_store)) {
            return true;
        }
        long maxAgeSeconds = get(JodaCacheControlTypes.max_age);
        if (maxAgeSeconds != -1) {
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
        if (o instanceof JodaCacheControl) {
            Set<E> a = new HashSet<>(entries);
            Set<E> b = new HashSet<>(((JodaCacheControl) o).entries);
            return a.equals(b);
        }
        return false;
    }

    public JodaCacheControl add(JodaCacheControlTypes... types) {
        for (JodaCacheControlTypes type : types) {
            _add(type);
        }
        return this;
    }

    public boolean contains(JodaCacheControlTypes type) {
        for (E e : entries) {
            if (e.type == type) {
                return true;
            }
        }
        return false;
    }

    public long get(JodaCacheControlTypes type) {
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

    void _add(JodaCacheControlTypes type) {
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

    public JodaCacheControl add(JodaCacheControlTypes type, Duration value) {
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
        return Strings.join(',', entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static JodaCacheControl fromString(String s) {
        JodaCacheControl result = new JodaCacheControl();
        String[] parts = s.split(",");
        for (String part : parts) {
            part = part.trim();
            JodaCacheControlTypes t = JodaCacheControlTypes.find(part);
            if (t != null) {
                if (t.takesValue) {
                    String[] sides = part.split("=", 2);
                    if (sides.length == 2) {
                        try {
                            long val = Long.parseLong(sides[1]);
                            result.entries.add(new E(t, val));
                        } catch (NumberFormatException nfe) {
                            nfe.printStackTrace();
                            Logger.getLogger(JodaCacheControl.class.getName()).log(Level.INFO, "Bad number in cache control header", nfe);
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

        private final JodaCacheControlTypes type;
        private final long value;

        E(JodaCacheControlTypes type) {
            this(type, -1);
        }

        E(JodaCacheControlTypes type, long value) {
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
