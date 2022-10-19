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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TimestampHeaderTest {

    @Test
    public void testInstantConversion() {
        ZonedDateTime when = ZonedDateTime.now().with(ChronoField.NANO_OF_SECOND, 0).with(ChronoField.MILLI_OF_SECOND, 0);
        TimestampHeader<ZonedDateTime> raw = Headers.LAST_MODIFIED;
        String origFmt = raw.toCharSequence(when).toString();
        TimestampHeader<Instant> inst = raw.toInstantHeader();
        String fromInstant = inst.toCharSequence(when.toInstant()).toString();
        assertEquals(origFmt, fromInstant);
        Instant in = inst.convert(origFmt);
        assertEquals(when.toInstant(), in);
    }

}
