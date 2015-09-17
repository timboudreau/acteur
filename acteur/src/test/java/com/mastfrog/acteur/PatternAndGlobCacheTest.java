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

import com.mastfrog.acteur.ActeurFactory.PatternAndGlobCache;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class PatternAndGlobCacheTest {

    PatternAndGlobCache cache = new PatternAndGlobCache();

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

    private PatternAndGlobCache assertReused(String regex) {
        Pattern p = cache.getPattern(regex);
        Pattern p1 = cache.getPattern(regex);
        if (p != null && p1 != null) {
            assertSame(regex, p, p1);
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
            assertEquals(regex + " should be " + glob, glob, globForRegex);
        }
        return this;
    }

    private PatternAndGlobCacheTest assertExact(String glob) {
        assertTrue(glob, cache.isExactGlob(glob));
        assertTrue(glob, cache.isExactGlob(glob));
        return this;
    }

    private PatternAndGlobCacheTest assertInexact(String glob) {
        assertFalse(glob, cache.isExactGlob(glob));
        assertFalse(glob, cache.isExactGlob(glob));
        return this;
    }
}
