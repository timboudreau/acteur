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
package com.mastfrog.url;

import org.junit.Assert;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class HostIpV6Test {

    @Test
    public void testAddresses() throws Throwable {
        Host h = Host.parse("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        h.getProblems().throwIfFatalPresent();
        assertTrue(h.isValid());
        assertFalse(h.isLocalhost());
        assertTrue(h.isIpAddress());
        assertTrue(h.isIpV6Address());
        assertFalse(h.isIpV4Address());
        Host compressed = Host.parse("2001:db8:85a3::8a2e:370:7334");
        assertTrue(compressed.isValid());
        assertFalse(compressed.isLocalhost());
        assertTrue(compressed.isIpAddress());
        assertTrue(compressed.isIpV6Address());
        assertFalse(compressed.isIpV4Address());

        assertEquals(8, compressed.toIntArray().length);
        assertFalse(h.toIntArray().length == 0);
        assertFalse(compressed.toIntArray().length == 0);
        Assert.assertArrayEquals(h.toIntArray(), compressed.toIntArray());
        assertEquals(h.hashCode(), compressed.hashCode());

        assertEquals(h, compressed);

        assertEquals(compressed, h.canonicalize());
        assertEquals(compressed, compressed.canonicalize());
        assertEquals(compressed.toString(), h.canonicalize().toString());
    }

    @Test
    public void testLabelToInt() throws Throwable {
        for (int i = 0; i < 10000; i++) {
            Label lbl = new Label(Integer.toHexString(i));
            int value = lbl.asInt(true);
            assertEquals(i, value);

            Label ilbl = new Label(Integer.toString(i));
            value = ilbl.asInt(false);
            assertEquals(i, value);
        }
    }

    @Test
    public void testSplit() throws Throwable {
        Host h = Host.parse("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertEquals(8, h.getLabels().length);
    }

    @Test
    public void testIpV6() throws Throwable {
        Host h = Host.parse("::1");
        assertTrue(h.isValid());
        assertTrue(h.isLocalhost());
        assertTrue(h.isIpAddress());
        assertTrue(h.isIpV6Address());
        assertFalse(h.isIpV4Address());
        assertEquals(8, h.toIntArray().length);

        Host loc = Host.parse("localhost");
        assertEquals(h, loc);
        Host loc2 = Host.parse("127.0.0.1");
        assertEquals(h, loc2);
    }

    @Test
    public void testInvalidAddresses() {
        Host hosed = Host.parse("2001:db8:85a3:9302:8a2e:370:7334:1234:372a");
        assertFalse(hosed.isLocalhost());
        assertFalse(hosed.isIpAddress());
        assertFalse(hosed.isIpV6Address());
        assertFalse(hosed.isIpV4Address());
        assertFalse(hosed.isValid());

        hosed = Host.parse("2001:db8:85a3::8a2e:370:7334:1234:wookie");
        assertFalse(hosed.isLocalhost());
        assertFalse(hosed.isIpAddress());
        assertFalse(hosed.isIpV6Address());
        assertFalse(hosed.isIpV4Address());
        assertFalse(hosed.isValid());

        hosed = Host.parse("2001:db8:85a3::8a2e:370:7334:1234:bb0c:wookie");
        assertFalse(hosed.isLocalhost());
        assertFalse(hosed.isIpAddress());
        assertFalse(hosed.isIpV6Address());
        assertFalse(hosed.isIpV4Address());
        assertFalse(hosed.isValid());
    }

    @Test
    public void testUrlWithPort() {
        URL withPort = URL.parse("http://[2001:db8:1f70::999:de8:7648:6e8]:100/foo/bar#baz");
        assertEquals("2001:db8:1f70:0:999:de8:7648:6e8", withPort.getHost().toString());
        assertEquals("http://[2001:db8:1f70:0:999:de8:7648:6e8]:100/foo/bar#baz", withPort.toString());
        assertTrue(withPort.isValid());
        assertEquals(100, withPort.getPort().intValue());

        URL expect = URL.parse("http://2001:0db8:85a3:0000:0000:8a2e:0370:7334/foo/bar#hey");
        URL compressed = URL.parse("http://2001:0db8:85a3::8a2e:0370:7334/foo/bar#hey");
        assertEquals(expect, compressed);
        URL expectWithPort = URL.parse("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:80/foo/bar#hey");
        assertEquals(expect, expectWithPort);
        URL compressedWithPort = URL.parse("http://[2001:0db8:85a3::8a2e:0370:7334]:80/foo/bar#hey");
        assertEquals(expect, compressedWithPort);

        assertTrue(expect.isValid());
        assertTrue(expectWithPort.isValid());
        assertTrue(compressedWithPort.isValid());
        assertTrue(compressed.isValid());
    }
    
    @Test
    public void testIpV6UrlWithPortAndPassword() {
        URL url = URL.parse("http://foo:bar@[2001:db8:1f70::999:de8:7648:6e8]:100/foo/bar#baz");
        assertNotNull(url.getPassword());
        assertNotNull(url.getUserName());
        assertEquals("2001:db8:1f70:0:999:de8:7648:6e8", url.getHost().toString());
        assertTrue(url.isValid());
    }

    @Test
    public void testLabelValidity() {
        assertTrue(new Label("food").isValid());
        assertTrue(new Label("com").isValid());
        assertTrue(new Label("c").isValid());
        assertTrue(new Label("1").isValid());
        assertTrue(Host.parse("food.com").isValid());
    }

    @Test
    public void testAllZeros() {
        Host h = Host.parse("0:0:0:0:0000:000:00:0");
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 0, 0}, h.toIntArray());
        assertEquals("::", h.canonicalize().toString());
        assertTrue(h.isIpAddress());
        assertTrue(h.canonicalize().isIpAddress());
        Host h1 = Host.parse("::");
        assertTrue(h1.ipv6);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 0, 0}, h1.toIntArray());
        assertEquals(h, h1);

        h = Host.parse("::1:2:3");
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 1, 2, 3}, h.toIntArray());

        h = Host.parse("1:2:3::");
        assertArrayEquals(new int[]{1, 2, 3, 0, 0, 0, 0, 0}, h.toIntArray());

        h = Host.parse("1:2:3::4:5:6");
        assertArrayEquals(new int[]{1, 2, 3, 0, 0, 4, 5, 6}, h.toIntArray());
    }

    @Test
    public void testIpV4IntArrays() {
        Host h = Host.parse("10.23.147.32");
        assertArrayEquals(new int[]{10, 23, 147, 32}, h.toIntArray());
        assertTrue(h.isIpAddress());
        assertTrue(h.isIpV4Address());
        assertFalse(h.isIpV6Address());
    }

    @Test
    public void testMultipleZeroRuns() {
        Host h = Host.parse("0000:0000:85a3:0000:0000:0000:0370:7334");
        assertEquals("0:0:85a3::370:7334", h.canonicalize().toString());
        assertEquals("0000:0000:85a3:0000:0000:0000:0370:7334", h.toString());
    }
    
    @Test
    public void testHosed() {
        Host h = new Host(true, new Label("0023"), new Label("what"), new Label("1234"), new Label("5678"), new Label("abcd"), new Label("efg0"));
        assertFalse(h.isValid());
        // Ensure none of these methods result in NPEs or similar
        h.toString();
        h.toIntArray();
        h.canonicalize();
        h.getDomain();
        assertFalse(h.isIpV6Address());
        assertFalse(h.isIpAddress());
        assertFalse(h.isIpV4Address());
        
        // Ensure nothing assumes a minimum length
        h = new Host(true);
        h.toString();
        h.toIntArray();
        h.canonicalize();
        h.getDomain();
        assertFalse(h.isIpV6Address());
        assertFalse(h.isIpAddress());
        assertFalse(h.isIpV4Address());
    }
}
