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
package com.mastfrog.acteur.headers;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import org.junit.Test;
import static org.junit.Assert.*;

public class ByteRangesTest {

    @Test
    public void testStartAndEndAreInclusive() {
        ByteRanges ranges = ByteRanges.builder().add(0, 0).build();
        assertEquals(1, ranges.size());
        assertNotNull(ranges.first());
        assertEquals(0, ranges.first().start(100));
        assertEquals(0, ranges.first().end(100));
        assertEquals(1, ranges.first().length(100));

        BoundedRange br = ranges.first().toBoundedRange(100);
        assertEquals(0, br.start());
        assertEquals(0, br.end());
        assertEquals(1, br.length());
        assertFalse(br.isRangeNotSatisfiable());
        assertTrue(br.isValid());

        Range range =  ByteRanges.of(0, 10).first();
        assertEquals(11, range.length(100));

        assertEquals(10, range.end(11));
        assertEquals(-1, range.end(9));
        assertEquals(-1, range.end(10));
    }

    @Test
    public void testParsing() {
        ByteRanges ranges = ByteRanges.builder().add(1, 10).build();
        assertTrue(ranges.isValid());
        assertEquals("bytes=1-10", ranges.toString());
        assertEquals(1, ranges.size());
        Range r = ranges.first();
        assertEquals(1, r.start(10));
        assertEquals(10, r.end(11));
        assertEquals(-1, r.end(9));
        assertEquals(-1, r.end(10));
        ByteRanges test = new ByteRanges(ranges.toString());
        assertEquals(ranges, test);

        ranges = ByteRanges.builder().addStartpoint(1).build();
        assertTrue(ranges.isValid());
        assertEquals("bytes=1-", ranges.toString());
        assertEquals(1, ranges.size());
        r = ranges.first();
        assertEquals(1, r.start(10));
        assertEquals(9, r.end(10));
        assertEquals(8, r.end(9));
        test = new ByteRanges(ranges.toString());
        assertEquals(ranges, test);

        ranges = ByteRanges.builder().addFromEnd(90).build();
        assertTrue(ranges.isValid());
        assertEquals("bytes=-90", ranges.toString());
        assertEquals(1, ranges.size());
        r = ranges.first();
        assertEquals(-1, r.start(10));
        assertEquals(-1, r.end(10));
        assertEquals(-1, r.end(9));
        assertEquals(10, r.start(100));
        assertEquals(100, r.end(100));
        test = new ByteRanges(ranges.toString());
        assertEquals(ranges, test);

        ranges = ByteRanges.builder().addFromEnd(90).add(20, 30).add(90,100).build();
        assertTrue(ranges.isValid());
        assertEquals("bytes=-90,20-30,90-100", ranges.toString());
        assertEquals(3, ranges.size());
        r = ranges.get(0);
        Range twentyThirty = ranges.get(1);
        assertEquals(20, twentyThirty.start(120));
        assertEquals(30, twentyThirty.end(120));
        Range ninetyHundred = ranges.get(2);
        assertEquals(90, ninetyHundred.start(120));
        assertEquals(100, ninetyHundred.end(120));
        test = new ByteRanges(ranges.toString());
        assertEquals(test, ranges);
        BoundedRange br = ninetyHundred.toBoundedRange(120);
        assertEquals("bytes 90-100/120", br.toString());

        ranges = new ByteRanges("bytes=44-35");
        assertFalse(ranges.isValid());
    }

    @Test
    public void testBoundedRange() {
        BoundedRange br = new BoundedRange(5, 10, 20);
        assertEquals(5, br.start());
        assertEquals(10, br.end());
        assertEquals(20, br.of());
        assertEquals("bytes 5-10/20", br.toString());
        assertTrue(br.isValid());
        assertFalse(br.isRangeNotSatisfiable());

        BoundedRange br2 = new BoundedRange(br.toString());
        assertEquals(br2 + "", br, br2);
        assertTrue(br2.isValid());
        assertFalse(br2.isRangeNotSatisfiable());

        br = new BoundedRange("bytes */1234");
        assertTrue(br.isValid());
        assertTrue(br.isRangeNotSatisfiable());
    }
    
    @Test
    public void sanityCheckNettyCookieParser() {
        
        String s = "fk=eAh0JMiaiaqtoHXYQCulxFp26I956Dqbchf7Z06d1KnTGfir1j6aVes29cLp4vWhgH94jDBNgbU3kFtI7wOhxw==:user2; Max-Age=10800000; Expires=Sun, 11 Oct 2015 05:47:58 GMT; Path=/; Domain=localhost";
        Cookie ck = ClientCookieDecoder.LAX.decode(s);
        assertEquals("fk", ck.name());
        assertEquals("eAh0JMiaiaqtoHXYQCulxFp26I956Dqbchf7Z06d1KnTGfir1j6aVes29cLp4vWhgH94jDBNgbU3kFtI7wOhxw==:user2", ck.value());
    }
}
