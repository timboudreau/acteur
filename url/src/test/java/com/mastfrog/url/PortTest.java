/*
 * The MIT License
 *
 * Copyright 2015 tim.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

/**
 *
 * @author tim
 */
public class PortTest {

    @Test
    public void testValue() {
        for (int i = 1; i < 65535; i++) {
            Port port = new Port(i);
            assertEquals(i, port.intValue());
            assertEquals("At " + i, Integer.toString(i), port.toString());
            URL u = URL.builder(Protocols.HTTPS).setHost("foo.com").setPort(i).create();
            assertEquals(port, u.getPort());
            URL u1 = URL.parse(u.toString());
            assertEquals(u, u1);
        }
        for (int i = 1; i < 65535; i++) {
            Port port = new Port(Integer.toString(i));
            assertEquals("Failed at " + i, i, port.intValue());
            assertEquals(Integer.toString(i), port.toString());
        }
        Port p = new Port("foo");
        assertFalse(p.isValid());
        assertEquals(-1, p.intValue());
    }
}
