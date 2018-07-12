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

import static com.mastfrog.acteur.util.StrictTransportSecurity.SecurityElements.INCLUDE_SUBDOMAINS;
import static com.mastfrog.acteur.util.StrictTransportSecurity.SecurityElements.PRELOAD;
import static com.mastfrog.util.Checks.notNull;
import com.mastfrog.util.Strings;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public final class StrictTransportSecurity implements Comparable<StrictTransportSecurity> {

    private final Duration maxAge;
    private final EnumSet<SecurityElements> elements;
    public static final StrictTransportSecurity ONE_YEAR = new StrictTransportSecurity(Duration.ofDays(365));
    public static final StrictTransportSecurity ONE_YEAR_INCLUDE_SUBDOMAINS
            = new StrictTransportSecurity(Duration.ofDays(365), INCLUDE_SUBDOMAINS);
    public static final StrictTransportSecurity ONE_YEAR_INCLUDE_SUBDOMAINS_PRELOAD
            = new StrictTransportSecurity(Duration.ofDays(365), INCLUDE_SUBDOMAINS, PRELOAD);

    public static final StrictTransportSecurity FIVE_YEARS = new StrictTransportSecurity(
            Duration.ofDays(365 * 5));
    public static final StrictTransportSecurity FIVE_YEARS_INCLUDE_SUBDOMAINS
            = new StrictTransportSecurity(Duration.ofDays(365 * 5), INCLUDE_SUBDOMAINS);
    public static final StrictTransportSecurity FIVE_YEARS_INCLUDE_SUBDOMAINS_PRELOAD
            = new StrictTransportSecurity(Duration.ofDays(365 * 5), INCLUDE_SUBDOMAINS, PRELOAD);

    StrictTransportSecurity(Duration maxAge, SecurityElements... elements) {
        this.maxAge = notNull("maxAge", maxAge).withNanos(0);
        this.elements = EnumSet.noneOf(SecurityElements.class);
        for (SecurityElements e : notNull("elements", elements)) {
            this.elements.add(e);
        }
    }

    StrictTransportSecurity(Duration maxAge, EnumSet<SecurityElements> elements) {
        this.maxAge = maxAge;
        this.elements = elements;
    }

    public Set<SecurityElements> elements() {
        return Collections.unmodifiableSet(elements);
    }

    public boolean contains(SecurityElements element) {
        return elements.contains(element);
    }

    public Duration maxAge() {
        return maxAge;
    }

    public static StrictTransportSecurity parse(CharSequence val) {
        Set<CharSequence> seqs = Strings.splitUniqueNoEmpty(';', val);
        Duration maxAge = null;
        EnumSet<SecurityElements> els = EnumSet.noneOf(SecurityElements.class);
        for (CharSequence seq : seqs) {
            seq = Strings.trim(seq);
            if (seq.length() == 0) {
                continue;
            }
            if (Strings.startsWith(seq, "max-age")) {
                CharSequence[] maParts = Strings.split('=', seq);
                if (maParts.length != 2) {
                    throw new IllegalArgumentException("Cannot parse " + seq + " as max-age");
                }
                maParts[1] = Strings.trim(maParts[1]);
                long seconds = Strings.parseLong(maParts[1]);
                maxAge = Duration.ofSeconds(seconds);
            } else if (Strings.charSequencesEqual("includeSubDomains", seq)) {
                els.add(SecurityElements.INCLUDE_SUBDOMAINS);
            } else if (Strings.charSequencesEqual("preload", seq)) {
                els.add(SecurityElements.PRELOAD);
            } else {
                throw new IllegalArgumentException("Unrecognized element: '" + seq + "'");
            }
        }
        if (maxAge == null) {
            throw new IllegalArgumentException("Required max-age= element missing in '" + val + "'");
        }
        return new StrictTransportSecurity(maxAge, els);
    }

    public int hashCode() {
        return maxAge.hashCode() + 7 * elements.hashCode();
    }

    private long toSeconds(Duration dur) {
        return dur.get(ChronoUnit.SECONDS);
    }

    public boolean equals(Object o) {
        return o == null ? false : o == this ? true : o instanceof StrictTransportSecurity
                && toSeconds(((StrictTransportSecurity) o).maxAge) == toSeconds(maxAge)
                && ((StrictTransportSecurity) o).elements.equals(elements);
        // JDK 9
//                && ((StrictTransportSecurity) o).maxAge.toSeconds() == maxAge.toSeconds()
//                && ((StrictTransportSecurity) o).elements.equals(elements);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("max-age=").append(toSeconds(maxAge));
        // JDK 9
//        StringBuilder sb = new StringBuilder("max-age=").append(maxAge.toSeconds());
        for (SecurityElements el : elements) {
            sb.append("; ").append(el.toString());
        }
        return sb.toString();
    }

    @Override
    public int compareTo(StrictTransportSecurity o) {
        int result = maxAge.compareTo(o.maxAge);
        if (result == 0) {
            result = elements.size() > o.elements.size() ? 1 : o.elements.size() == elements.size() ? 0 : -1;
        }
        return result;
    }

    public enum SecurityElements {
        INCLUDE_SUBDOMAINS("includeSubDomains"),
        PRELOAD("preload");
        private final String stringValue;

        SecurityElements(String stringValue) {
            this.stringValue = stringValue;
        }

        public String toString() {
            return stringValue;
        }
    }
}
