/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteur;

import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class PatternAndGlobCacheTest {

    PathPatterns cache = new PathPatterns();

    @Test
    public void testGlobs() {
        assertExact("/foo");
        assertExact("foo/bar/baz");
        assertInexact("foo/*/baz");
        assertInexact("foo/bar/baz/*");
        assertInexact("foo/bar/baz/foo*");
        assertInexact("*foo/bar/baz/foo");
        assertInexact("*/foo/bar/baz/foo");
        assertExact("");
        assertExact("/numble");
    }

    @Test
    public void testPatterns() {
        assertReused("\\/foo");
        assertReused("^\\/foo[.*?]/[\\d]{0,1}");
    }

    @Test
    public void testRegexToExact() {
        assertRegexToExact("path$", null);
        assertRegexToExact("^echo$", "echo");
        assertRegexToExact("^echo", null);
        assertRegexToExact("\\/path$", "path");
        assertRegexToExact("path\\/", "/path");
        assertRegexToExact("\\/path\\/to\\/foo", "path/to/foo");
        assertRegexToExact("foo\\/.*?\\/bar", null);
        assertRegexToExact("foo[0-9]+bar", null);
    }

    private PathPatterns assertReused(String regex) {
        Pattern p = cache.getPattern(regex);
        Pattern p1 = cache.getPattern(regex);
        if (p != null && p1 != null) {
            assertSame(p, p1, regex);
        } else if ((p == null) != (p1 == null)) {
            fail("Got null and non null in consecutive tries: " + p + " " + p1);
        }
        return cache;
    }

    private PatternAndGlobCacheTest assertRegexToExact(String regex, String glob) {
        String globForRegex = cache.exactPathForRegex(regex);
        if (glob == null && globForRegex == null) {
            return this;
        } else if (globForRegex != null) {
            assertEquals( glob, globForRegex, regex + " should be " + glob);
        }
        return this;
    }

    private PatternAndGlobCacheTest assertExact(String glob) {
        assertTrue(cache.isExactGlob(glob), glob);
        assertTrue(cache.isExactGlob(glob), glob);
        return this;
    }

    private PatternAndGlobCacheTest assertInexact(String glob) {
        assertFalse(cache.isExactGlob(glob), glob);
        assertFalse(cache.isExactGlob(glob), glob);
        return this;
    }
}
