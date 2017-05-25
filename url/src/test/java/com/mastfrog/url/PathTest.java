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

package com.mastfrog.url;

import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class PathTest {
    @Test
    public void testParseEmpty() {
        Path pth = Path.parse("");
        assertEquals("", pth.toString());
        assertEquals("/", pth.toStringWithLeadingSlash());
    }
    
    @Test
    public void testParsing() {
        Path pth = Path.parse("foo/bar/baz");
        assertEquals(3, pth.size());
        assertEquals("foo", pth.getElement(0).toString());
        assertEquals("bar", pth.getElement(1).toString());
        assertEquals("baz", pth.getElement(2).toString());
        assertEquals("baz", pth.getLastElement().toString());
    }
    
    @Test
    public void testInvalid() {
        Path pth = Path.parse("адресу/用");
        assertFalse(pth.isValid());
        assertFalse(pth.getElement(0).isValid());
    }
    
    @Test
    public void testLeadingAndTrailingSlashesDontAffectEquality() {
        Path a = Path.parse("foo/bar/baz");
        Path b = Path.parse("/foo/bar/baz");
        Path c = Path.parse("foo/bar/baz/");
        Path d = Path.parse("/foo/bar/baz/");
        Path e = Path.parse("foo/bar/baz/bean");
        Path f = Path.parse("moo/bar/baz");
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals(a, d);
        assertEquals(b, a);
        assertEquals(b, c);
        assertEquals(b, d);
        assertEquals(c, a);
        assertEquals(c, b);
        assertEquals(c, d);
        assertNotEquals(a, e);
        assertNotEquals(b, e);
        assertNotEquals(c, e);
        assertNotEquals(d, e);
        assertNotEquals(a, f);
        assertNotEquals(b, f);
        assertNotEquals(c, f);
        assertNotEquals(d, f);
    }
}
