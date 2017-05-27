/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.marshallers.netty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.util.collections.MapBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import java.io.DataInput;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class NettyContentMarshallersTest {

    private final NettyContentMarshallers m = NettyContentMarshallers.getDefault(new ObjectMapper());

    private static ByteBuf buf() {
        return Unpooled.buffer();
    }

    @Test
    public void ensureOnlyPrimitivesAreWrapped() throws Exception {
        ByteBuf buf = buf();
        Thing thing = new Thing("Hey", 23, 3.57D);
        m.write(thing, buf);
        Thing nue = m.read(Thing.class, buf);
        assertEquals(thing, nue);
        
        buf.resetReaderIndex();
        Map<String,Object> map = new ObjectMapper().readValue((DataInput) new ByteBufInputStream(buf), Map.class);
        System.out.println("MAP: " + map);
        assertFalse(map.containsKey("Result"));
        assertFalse(map.containsKey("Thing"));
        assertTrue(map.containsKey("what"));
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("bar"));
        assertEquals("Hey", map.get("what"));
    }

    @Test
    public void testTypes() throws Exception {
        ByteBuf buf = buf();
        buf.writeBytes(new byte[]{23, 123, 112, 59, 87}, 0, 5);

        ByteBuffer bbuf = ByteBuffer.allocateDirect(5);
        bbuf.put(new byte[]{3, 7, 118, 72, 91});

        Map<String, Object> mb = new MapBuilder().put("hey", "you").put("thing", 23).put("hey", true).build();

        List<Object> l = Arrays.asList(new Object[]{"word", true, 52, 22.2D});

        testOne(byte[].class, new byte[]{31, 23, 55, 72, 61});

        testOne(ByteBuffer.class, bbuf);

        testOne(String.class, "Hello world");
        testOne(String.class, "Goodbye world", CharsetUtil.UTF_16BE);

        testOne(CharSequence.class, new StringBuilder("Hello world"));
        testOne(CharSequence.class, new StringBuilder("Goodbye world"), CharsetUtil.UTF_16LE);

        testOne(List.class, l);
        testOne(Map.class, mb);

        testOne(Double.class, Double.valueOf(5.7272));

        testOne(ByteBuf.class, buf);
    }

    <T> NettyContentMarshallersTest testOne(Class<T> type, T obj, Object... hints) throws Exception {
        ByteBuf buf = buf();
        m.write(obj, buf, hints);
        T result = m.read(type, buf, hints);
        if (type.isArray()) {
            assertArrayEquals("Fail on " + type.getName(), obj, result);
        } else if (type == CharSequence.class) {
            CharSequence aa = (CharSequence) obj;
            CharSequence bb = (CharSequence) result;
            assertCharSequencesEqual(aa, bb);
        } else if (type == ByteBuf.class) {
            ByteBuf aa = (ByteBuf) obj;
            ByteBuf bb = (ByteBuf) result;
            aa.resetReaderIndex();
            byte[] aBytes = new byte[aa.readableBytes()];
            aa.readBytes(aBytes);
            bb.resetReaderIndex();
            byte[] bBytes = new byte[bb.readableBytes()];
            bb.readBytes(bBytes);
            Assert.assertArrayEquals("Fail on ByteBuf", aBytes, bBytes);
        } else {
            assertEquals("Fail on " + type.getName(), obj, result);
        }
        return this;
    }

    private static void assertCharSequencesEqual(CharSequence a, CharSequence b) {
        int maxA = a.length();
        int maxB = b.length();
        assertEquals("Size mismatch on " + a.getClass().getName() + "/" + b.getClass().getName(), maxA, maxB);
        for (int i = 0; i < maxA; i++) {
            char aa = a.charAt(i);
            char bb = b.charAt(i);
            assertEquals("Mismatch at " + i + " in '" + aa + "' vs + '" + bb, aa, bb);
        }
    }

    private static <T> void assertArrayEquals(String string, T obj, T result) {
        int maxA = Array.getLength(obj);
        int maxB = Array.getLength(result);
        assertEquals("Size mismatch on " + obj.getClass().getName(), maxA, maxB);
        for (int i = 0; i < maxA; i++) {
            Object a = Array.get(obj, i);
            Object b = Array.get(result, i);
            assertEquals("Mismatch in " + obj.getClass().getName() + " at " + i, a, b);
        }
    }

    public static final class Thing {

        public final String what;
        public final int foo;
        public final double bar;

        @JsonCreator
        public Thing(@JsonProperty("what") String what, @JsonProperty("foo") int foo, @JsonProperty("bar") double bar) {
            this.what = what;
            this.foo = foo;
            this.bar = bar;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 17 * hash + Objects.hashCode(this.what);
            hash = 17 * hash + this.foo;
            hash = 17 * hash + (int) (Double.doubleToLongBits(this.bar) ^ (Double.doubleToLongBits(this.bar) >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Thing other = (Thing) obj;
            if (this.foo != other.foo) {
                return false;
            }
            if (Double.doubleToLongBits(this.bar) != Double.doubleToLongBits(other.bar)) {
                return false;
            }
            if (!Objects.equals(this.what, other.what)) {
                return false;
            }
            return true;
        }
    }
}
