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
package com.mastfrog.acteur.headers;

import com.mastfrog.acteur.util.FrameOptions;
import io.netty.util.AsciiString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class FrameOptionsHeaderTest {

    @Test
    public void testSomeMethod() {
        HeaderValueType<FrameOptions> h = Headers.X_FRAME_OPTIONS;
        FrameOptions fo = h.toValue("DENY");
        assertSame(FrameOptions.DENY, fo);
        assertEquals(AsciiString.of("DENY"), h.toCharSequence(fo));

        fo = h.toValue("SAMEORIGIN");
        assertSame(FrameOptions.SAMEORIGIN, fo);
        assertEquals(AsciiString.of("SAMEORIGIN"), h.toCharSequence(fo));

        fo = h.toValue("ALLOW-FROM http://mastfrog.com");
        assertEquals(AsciiString.of("ALLOW-FROM http://mastfrog.com"), h.toCharSequence(fo));

        assertTrue(h.is("X-Frame-Options"));
    }

}
