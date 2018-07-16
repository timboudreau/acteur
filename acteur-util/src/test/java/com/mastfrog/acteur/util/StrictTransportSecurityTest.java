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

import com.mastfrog.acteur.util.StrictTransportSecurity.SecurityElements;
import static com.mastfrog.acteur.util.StrictTransportSecurity.SecurityElements.INCLUDE_SUBDOMAINS;
import static com.mastfrog.acteur.util.StrictTransportSecurity.SecurityElements.PRELOAD;
import com.mastfrog.util.preconditions.NullArgumentException;
import com.mastfrog.util.time.TimeUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class StrictTransportSecurityTest {

    @Test
    public void testParseAndConstruct() throws Throwable {
        StrictTransportSecurity sec = new StrictTransportSecurity(Duration.ofMinutes(1));
        assertEquals("max-age=60", sec.toString());
        StrictTransportSecurity parsed = StrictTransportSecurity.parse(sec.toString());
        assertEquals(sec, parsed);
        assertEquals(sec.hashCode(), parsed.hashCode());
        assertEquals(sec.toString(), parsed.toString());

        sec = new StrictTransportSecurity(Duration.ofMinutes(1), INCLUDE_SUBDOMAINS);
        assertEquals("max-age=60; includeSubDomains", sec.toString());
        parsed = StrictTransportSecurity.parse(sec.toString());
        assertEquals(sec, parsed);
        assertEquals(sec.hashCode(), parsed.hashCode());
        assertEquals(sec.toString(), parsed.toString());

        assertEquals(sec, StrictTransportSecurity.parse("max-age=60; includeSubDomains   "));
        assertEquals(sec, StrictTransportSecurity.parse("  max-age=60; includeSubDomains   "));
        assertEquals(sec, StrictTransportSecurity.parse("max-age=60;includeSubDomains"));
        assertEquals(sec, StrictTransportSecurity.parse("max-age=60;;includeSubDomains"));
        assertEquals(sec, StrictTransportSecurity.parse("max-age=60;includeSubDomains;"));
        assertEquals(sec, StrictTransportSecurity.parse(";max-age=60;includeSubDomains"));
        assertEquals(sec, StrictTransportSecurity.parse("max-age=60;includeSubDomains;includeSubDomains"));
        assertEquals(sec, StrictTransportSecurity.parse("max-age=60;includeSubDomains;includeSubDomains;includeSubDomains"));
        assertEquals(sec, StrictTransportSecurity.parse("includeSubDomains;max-age=60"));
        assertEquals(sec.hashCode(), StrictTransportSecurity.parse("includeSubDomains;max-age=60").hashCode());

        sec = new StrictTransportSecurity(Duration.ofMinutes(1), INCLUDE_SUBDOMAINS, PRELOAD);
        assertEquals("max-age=60; includeSubDomains; preload", sec.toString());
        parsed = StrictTransportSecurity.parse(sec.toString());
        assertEquals(sec, parsed);
        assertEquals(sec.hashCode(), parsed.hashCode());
        assertEquals(sec.toString(), parsed.toString());

        assertEquals(sec, StrictTransportSecurity.parse("max-age=60; preload; includeSubDomains"));
        assertEquals(sec, StrictTransportSecurity.parse("preload; includeSubDomains; max-age=60"));
        assertNotEquals(sec, StrictTransportSecurity.parse("preload; includeSubDomains; max-age=61"));

        for (Field f : StrictTransportSecurity.class.getDeclaredFields()) {
            if (f.getType() == StrictTransportSecurity.class && (f.getModifiers() & Modifier.STATIC) != 0) {
                System.out.println(f.getName() + " " + f.get(null));
                assertEquals(f.get(null), StrictTransportSecurity.parse(f.get(null).toString()));
            }
        }
    }

    @Test
    public void testExtremeValues() {
        StrictTransportSecurity sec = new StrictTransportSecurity(TimeUtil.MAX_DURATION, SecurityElements.PRELOAD);
        assertEquals(sec, StrictTransportSecurity.parse(sec.toString()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidParse1() {
        StrictTransportSecurity.parse("");
    }

    @Test(expected=NullArgumentException.class)
    public void testInvalidConstruct() {
        StrictTransportSecurity.parse(null);
    }

    @Test(expected=NullArgumentException.class)
    public void testInvalidConstruct2() {
        new StrictTransportSecurity(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidParse2() {
        StrictTransportSecurity.parse("preload; includeSubDomains; max-age=61; foodbar");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidParse3() {
        StrictTransportSecurity.parse("foodbar; preload; includeSubDomains; max-age=61;");
    }

    @Test(expected=NumberFormatException.class)
    public void testInvalidParse4() {
        StrictTransportSecurity.parse("preload; includeSubDomains; max-age=073r8b;");
    }

    @Test(expected=NumberFormatException.class)
    public void testInvalidParse5() {
        StrictTransportSecurity.parse("preload; includeSubDomains; max-age=infinity;");
    }
}
