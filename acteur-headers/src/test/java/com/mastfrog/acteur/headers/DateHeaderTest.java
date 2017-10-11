/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class DateHeaderTest {

    @Test
    public void testConversion() {
        ZonedDateTime expect = ZonedDateTime.of(1973, 12, 25, 13, 10, 30, 0, ZoneId.of("America/New_York"));

        String good = Headers.DATE.toCharSequence(expect).toString();
        String malformed1 = "Tue, 25 Dec 1973 13:10:30 -05:00";
        String malformed2 = "Tue, 25 Dec 1973 13:10:30 EST";
        String malformed3 = "Mon, 25 Dec 73 13:10:30 -05:00";
        String malformed4 = "Tue, 25 Dec 73 13:10:30 -05:00";
        String malformed5 = "Tue, 25 Dec 73 13:10:30 -0500";

        ZonedDateTime m0 = Headers.DATE.toValue(good);
        ZonedDateTime m1 = Headers.DATE.toValue(malformed1);
        ZonedDateTime m2 = Headers.DATE.toValue(malformed2);
        ZonedDateTime m3 = Headers.DATE.toValue(malformed3);
        ZonedDateTime m4 = Headers.DATE.toValue(malformed4);
        ZonedDateTime m5 = Headers.DATE.toValue(malformed5);

        assertEqualsDT(expect, m0);
        assertEqualsDT(expect, m1);
        assertEqualsDT(expect, m2);
        assertEqualsDT(expect, m3);
        assertEqualsDT(expect, m4);
        assertEqualsDT(expect, m5);
    }

    private void assertEqualsDT(ZonedDateTime a, ZonedDateTime b) {
        String msg = Headers.DATE.toCharSequence(a) + " vs " + Headers.DATE.toCharSequence(b);
        assertEquals(msg, a.toInstant(), b.toInstant());
    }
}
