/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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

import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import org.junit.Test;
import static org.junit.Assert.*;

public class PragmaHeaderTest {

    @Test
    public void testConstantValue() {
        assertTrue(Headers.PRAGMA instanceof PragmaHeader);
    }

    @Test
    public void testNoCacheDetected() {
        CacheControl ctrl = Headers.PRAGMA.toValue("no-cache");
        assertNotNull(ctrl);
        assertTrue(ctrl.contains(CacheControlTypes.no_cache));
        assertFalse(ctrl.contains(CacheControlTypes.no_store));
        assertFalse(ctrl.contains(CacheControlTypes.Private));
        assertFalse(ctrl.contains(CacheControlTypes.Public));
        assertFalse(ctrl.contains(CacheControlTypes.immutable));
        assertFalse(ctrl.contains(CacheControlTypes.max_age));
        assertFalse(ctrl.contains(CacheControlTypes.max_stale));
        assertFalse(ctrl.contains(CacheControlTypes.must_revalidate));
        assertFalse(ctrl.contains(CacheControlTypes.min_fresh));
        assertFalse(ctrl.contains(CacheControlTypes.no_transform));
        assertFalse(ctrl.contains(CacheControlTypes.only_if_cached));
        assertFalse(ctrl.contains(CacheControlTypes.proxy_revalidate));
        assertFalse(ctrl.contains(CacheControlTypes.s_maxage));
    }

    @Test
    public void testGarbageIgnored() {
        assertNull(Headers.PRAGMA.toValue("no-store"));
        assertNull(Headers.PRAGMA.toValue("wuggles"));
        assertNull(Headers.PRAGMA.toValue(""));
        assertNull(Headers.PRAGMA.toValue("-"));
    }

}
