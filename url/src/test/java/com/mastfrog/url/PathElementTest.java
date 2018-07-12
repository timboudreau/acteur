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
package com.mastfrog.url;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class PathElementTest {

    @Test
    public void testExtensions() {
        PathElement pe = new PathElement("foo.txt");
        assertEquals("foo.txt", pe.toString());
        assertTrue(pe.extensionEquals("txt"));
        assertFalse(pe.extensionEquals("txo"));
        assertFalse(pe.extensionEquals("tx"));
        assertFalse(pe.extensionEquals("txty"));
        assertFalse(pe.extensionEquals(""));
        assertFalse(pe.extensionEquals(" "));
        assertEquals("txt", pe.extension());
        assertTrue(pe.isProbableFileReference());
        assertTrue(pe.isValid());

        pe = new PathElement("wookie");
        assertEquals("wookie", pe.toString());
        assertNull(pe.extension());
        assertFalse(pe.extensionEquals(""));
        assertFalse(pe.extensionEquals("wookie"));
        assertFalse(pe.extensionEquals("txt"));
        assertFalse(pe.isProbableFileReference());
        assertTrue(pe.isValid());

        pe = new PathElement("wookie.foo", true);
        assertEquals("wookie.foo", pe.toString());
        assertFalse(pe.isProbableFileReference());
        assertTrue(pe.isValid());
    }

    @Test
    public void testTrailingSlash() {
        PathElement pe = new PathElement("goob", true);
        assertEquals("goob", pe.toString());
        assertEquals("goob", pe.rawText());
        assertEquals("goob", pe.toNonTrailingSlashElement().toString());
        assertTrue(pe.isValid());

        pe = new PathElement("goob/", true);
        assertEquals("goob%2f", pe.toString());
        assertEquals("goob/", pe.rawText());
        assertFalse(pe.isValid());

        assertFalse(pe.isProbableFileReference());

        pe = new PathElement("goob", false);
        assertEquals("goob", pe.toString());
    }

}
