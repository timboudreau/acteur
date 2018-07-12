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

import com.mastfrog.util.Strings;

/**
 * Enum of valid values for cache control
 *
 * @author Tim Boudreau
 */
public enum CacheControlTypes {
    Public, Private, must_revalidate, proxy_revalidate, no_cache, no_store, 
    max_age(true), max_stale(true), min_fresh(true), 
    no_transform, only_if_cached, s_maxage(true), immutable;
    final boolean takesValue;

    private CacheControlTypes(boolean takesValue) {
        this.takesValue = takesValue;
    }

    CacheControlTypes() {
        this(false);
    }

    @Override
    public String toString() {
        char[] c = name().toLowerCase().toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == '_') {
                c[i] = '-';
            }
        }
        return new String(c);
    }

    public static CacheControlTypes find(String s) {
        for (CacheControlTypes c : values()) {
            if (s.startsWith(c.toString())) {
                return c;
            }
        }
        return null;
    }
    
    public static CacheControlTypes find(CharSequence s) {
        for (CacheControlTypes c : values()) {
            if (Strings.startsWith(s, c.toString())) {
                return c;
            }
        }
        return null;
    }
}
