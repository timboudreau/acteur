/*
 * The MIT License
 *
 * Copyright 2022 Tim Boudreau.
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
package com.mastfrog.mime;

import java.nio.charset.StandardCharsets;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class MimeTypeParserTest {

    @Test
    public void testParsedMimeTypeWithoutCharsetOrVariant() {
        ParsedMimeType mt = MimeTypeParser.parse("text/x-java");
        csEqual("text", mt.primaryType());
        assertCs(mt.secondaryType(), "x-java");
        assertFalse(mt.charset().isPresent());
        assertEquals(0, mt.parameters().size());
        assertFalse(mt.variant().isPresent());
        assertEquals("text/x-java", mt.toString());
    }

    @Test
    public void testParsedMimeTypeWithCharsetAndVariant() {
        ParsedMimeType mt = MimeTypeParser.parse("text/x-java+jdk9;charset=utf-8");
        csEqual("text", mt.primaryType());
        assertCs(mt.secondaryType(), "x-java");
        assertEquals(1, mt.parameters().size(), "Should have a parameter");
        assertTrue(mt.charset().isPresent(), "charset was not found in " + mt);
        assertEquals(UTF_8, mt.charset().get(), "wrong charset");
        assertTrue(mt.variant().isPresent(), "variant not present");
        assertEquals("jdk9", mt.variant().get(), "wrong variant");
        assertEquals("text/x-java+jdk9;charset=utf-8", mt.toString());
    }

    @Test
    public void testSimpleMimeTypeWithCharsetWithoutCharsetOrVariant() {
        SimpleMimeTypeWithCharset java = new SimpleMimeTypeWithCharset(
                "text", "x-java", null, null);
        ParsedMimeType mt = MimeTypeParser.parse("text/x-java");

        csEqual("text", mt.primaryType());
        assertCs(mt.secondaryType(), "x-java");
        assertFalse(mt.charset().isPresent(), "charset should not be present");
        assertEquals(0, mt.parameters().size());
        assertFalse(mt.variant().isPresent());
        assertEquals("text/x-java", mt.toString());

        assertEquals(java, mt);
    }

    @Test
    public void testSimpleMimeTypeWithCharsetWithCharsetAndVariant() {
        SimpleMimeTypeWithCharset java = new SimpleMimeTypeWithCharset(
                "text", "x-java", "jdk9", UTF_8);
        ParsedMimeType mt = MimeTypeParser.parse("text/x-java+jdk9;charset=utf-8");

        csEqual("text", mt.primaryType());
        assertCs(mt.secondaryType(), "x-java");
        assertTrue(java.charset().isPresent(), "charset should be present in " + java);
        assertEquals(1, mt.parameters().size(), "should have a parameter");

        Map.Entry<? extends CharSequence, ? extends CharSequence> mapEntry = mt.parameters().get(0);
        csEqual(mapEntry.getKey(), "charset");
        csEqual(mapEntry.getValue(), "utf-8");

        assertTrue(mt.charset().isPresent(), "charset should be present in " + mt);
        assertTrue(mt.variant().isPresent(), "variant should be present");
        assertCs(mt.variant(), "jdk9");
        assertCs(java.variant(), "jdk9");
        assertEquals("text/x-java+jdk9;charset=utf-8", mt.toString());
        assertEquals("text/x-java+jdk9;charset=utf-8", java.toString(),
                " for " + java.getClass().getSimpleName());

        assertTrue(java
                .differingComponents(mt)
                .isEmpty(),
                "Should not have differing components but got"
                + java.differingComponents(mt));

        assertEquals(java, mt);
    }

    @Test
    public void testParsedMimeTypeWithParams() {
        ParsedMimeType mt = MimeTypeParser.parse("text/x-java+jdk9;foodbar=blee;snorks=portles;charset=utf-8");
        assertEquals(3, mt.parameters().size(), "Wrong number of parameters " + mt.parameters());
        assertTrue(mt.charset().isPresent(), "Charset absent");
        assertEquals(UTF_8, mt.charset().get(), "Wrong charset");
        assertEquals("text/x-java+jdk9;foodbar=blee;snorks=portles;charset=utf-8", mt.toString());

        MimeType wc = mt.withCharset(StandardCharsets.ISO_8859_1);
        assertTrue(wc instanceof SimpleMimeTypeWithParams);
        assertEquals("text/x-java+jdk9;foodbar=blee;snorks=portles;charset=iso-8859-1", wc.toString());
        assertEquals(StandardCharsets.ISO_8859_1, wc.charset().get());
        assertEquals(wc, MimeTypeParser.parse(wc.toString()));
    }

    private static void assertCs(Optional<CharSequence> seq, String what) {
        if (what == null) {
            assertTrue(!seq.isPresent() || seq.get().length() == 0,
                    "Is empty but should be " + what);
        } else {
            assertTrue(seq.isPresent());
            csEqual(what, seq.get());
        }
    }

    private static void csEqual(CharSequence a, CharSequence b) {
        assertTrue(MimeType.charSequencesEqual(a, b), "Non-match: '" + a
                + "' and '" + b + "'");
        assertEquals(Objects.toString(a), Objects.toString(b));
    }
}
