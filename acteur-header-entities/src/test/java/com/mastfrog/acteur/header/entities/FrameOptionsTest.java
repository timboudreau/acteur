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
package com.mastfrog.acteur.header.entities;

import com.mastfrog.acteur.header.entities.FrameOptions.FrameOptionType;
import java.net.URI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class FrameOptionsTest {

    @Test
    public void testNoArgumentOptionsParse() {
        assertSame(FrameOptions.DENY, FrameOptions.parse("deny"));
        assertSame(FrameOptions.SAMEORIGIN, FrameOptions.parse("sameorigin"));
        assertSame(FrameOptions.DENY, FrameOptions.parse("deny "));
        assertSame(FrameOptions.SAMEORIGIN, FrameOptions.parse("sameorigin "));
        assertSame(FrameOptions.DENY, FrameOptions.parse(" deny"));
        assertSame(FrameOptions.SAMEORIGIN, FrameOptions.parse(" sameorigin"));
        assertSame(FrameOptions.DENY, FrameOptions.parse("DENY"));
        assertSame(FrameOptions.SAMEORIGIN, FrameOptions.parse("SAMEORIGIN"));
        assertEquals("DENY", FrameOptions.DENY.toString());
        assertEquals("SAMEORIGIN", FrameOptions.SAMEORIGIN.toString());

        assertSame(FrameOptions.FrameOptionType.DENY, FrameOptions.parse("DENY").type());
        assertSame(FrameOptions.FrameOptionType.SAMEORIGIN, FrameOptions.parse("SAMEORIGIN").type());

    }

    @Test
    public void testAllowFrom() {
        FrameOptions fo = FrameOptions.allowFrom(URI.create("http://mastfrog.com"));
        assertEquals(URI.create("http://mastfrog.com"), fo.uri());
        assertSame(FrameOptionType.ALLOW_FROM, fo.type());
        assertEquals("ALLOW-FROM http://mastfrog.com", fo.toString());
        FrameOptions fo2 = FrameOptions.parse(fo.toString());
        assertEquals(fo, fo2);
        assertEquals(fo.hashCode(), fo2.hashCode());

        FrameOptions fo3 = FrameOptions.parse(fo.toString().toLowerCase());
        assertEquals(fo, fo3);
        assertEquals(fo.hashCode(), fo3.hashCode());
        assertEquals(fo.toString(), fo3.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidArgument() {
        new FrameOptions(FrameOptionType.DENY, URI.create("http://mastfrog.com"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingArgument() {
        FrameOptions.parse("DENY http://mastfrog.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingArgumen2t() {
        FrameOptions.parse("SAMEORIGIN http://mastfrog.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidName() {
        FrameOptions.parse("PERCEIVE http://mastfrog.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidName2() {
        FrameOptions.parse("DENI http://mastfrog.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidUri() {
        FrameOptions.parse("ALLOW-FROM \\u0000//:////");
    }
}
