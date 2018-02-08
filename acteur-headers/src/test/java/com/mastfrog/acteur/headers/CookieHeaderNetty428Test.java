/*
 * The MIT License
 *
 * Copyright 2018 tim.
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

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CookieHeaderNetty428Test {

    @Test
    public void testMultipleCookies() {
        CookieHeaderNetty428 h = new CookieHeaderNetty428(true);
        DefaultCookie a = new DefaultCookie("a", "a");
        DefaultCookie b = new DefaultCookie("b", "b");
        DefaultCookie c = new DefaultCookie("c", "c");
        String val = h.toCharSequence(new Cookie[]{a, b, c}).toString();
        Cookie[] result = h.toValue(val);
        assertNotNull(result);
        assertNotNull(val);
        assertTrue("Should contain all cookies:" + val, val.contains("a") && val.contains("b") && val.contains("c"));
        assertEquals(3, result.length);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testLegacyCookies() {
        CookieHeader h = new CookieHeader();
        io.netty.handler.codec.http.DefaultCookie a = new io.netty.handler.codec.http.DefaultCookie("a", "a");
        io.netty.handler.codec.http.DefaultCookie b = new io.netty.handler.codec.http.DefaultCookie("b", "b");
        io.netty.handler.codec.http.DefaultCookie c = new io.netty.handler.codec.http.DefaultCookie("c", "c");
        String val = h.toString(new io.netty.handler.codec.http.Cookie[]{a, b, c});
        io.netty.handler.codec.http.Cookie[] result = h.toValue(val);
        assertNotNull(result);
        assertNotNull(val);
        assertTrue("Should contain all cookies:" + val, val.contains("a") && val.contains("b") && val.contains("c"));
        assertEquals(3, result.length);

    }

}
