/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.TextHeaders;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
public class HackHttpHeaders implements HttpHeaders {

    final HttpHeaders orig;

    public HackHttpHeaders(HttpHeaders orig, boolean chunked) {
        this.orig = orig;
        if (chunked) {
            orig.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            orig.remove(HttpHeaderNames.CONTENT_LENGTH);
        } else {
            orig.remove(HttpHeaderNames.TRANSFER_ENCODING);
        }
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence... values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders addObject(CharSequence name, Object value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders addObject(CharSequence name, Iterable<?> values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addObject(CharSequence name, Object... values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addBoolean(CharSequence name, boolean value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders addByte(CharSequence name, byte value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders addChar(CharSequence name, char value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders addLong(CharSequence name, long value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders addFloat(CharSequence name, float value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders addDouble(CharSequence name, double value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.addDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(TextHeaders headers) {
        TextHeaders th = new DefaultHttpHeaders();
        for (CharSequence key : th.names()) {
            if (HttpHeaderNames.TRANSFER_ENCODING.equals(key)) {
                continue;
            }
            if (HttpHeaderNames.CONTENT_LENGTH.equals(key)) {
                continue;
            }
            th.add(key, th.getAll(key));
        }
        orig.add(headers);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence... values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders setObject(CharSequence name, Object value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders setObject(CharSequence name, Iterable<?> values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setObject(CharSequence name, Object... values) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setBoolean(CharSequence name, boolean value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders setByte(CharSequence name, byte value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders setChar(CharSequence name, char value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders setLong(CharSequence name, long value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders setFloat(CharSequence name, float value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders setDouble(CharSequence name, double value) {
        if (HttpHeaderNames.TRANSFER_ENCODING.equals(name)) {
            return this;
        }
        if (HttpHeaderNames.CONTENT_LENGTH.equals(name)) {
            return this;
        }
        orig.setDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(TextHeaders headers) {
        return orig.set(headers);
    }

    @Override
    public HttpHeaders setAll(TextHeaders headers) {
        return orig.setAll(headers);
    }

    @Override
    public HttpHeaders clear() {
        return orig.clear();
    }

    @Override
    public String getAndConvert(CharSequence name) {
        return orig.getAndConvert(name);
    }

    @Override
    public String getAndConvert(CharSequence name, String defaultValue) {
        return orig.getAndConvert(name, defaultValue);
    }

    @Override
    public String getAndRemoveAndConvert(CharSequence name) {
        return orig.getAndRemoveAndConvert(name);
    }

    @Override
    public String getAndRemoveAndConvert(CharSequence name, String defaultValue) {
        return orig.getAndRemoveAndConvert(name, defaultValue);
    }

    @Override
    public List<String> getAllAndConvert(CharSequence name) {
        return orig.getAllAndConvert(name);
    }

    @Override
    public List<String> getAllAndRemoveAndConvert(CharSequence name) {
        return orig.getAllAndRemoveAndConvert(name);
    }

    @Override
    public List<Map.Entry<String, String>> entriesConverted() {
        return orig.entriesConverted();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iteratorConverted() {
        return orig.iteratorConverted();
    }

    @Override
    public Set<String> namesAndConvert(Comparator<String> comparator) {
        return orig.namesAndConvert(comparator);
    }

    @Override
    public CharSequence get(CharSequence name) {
        return orig.get(name);
    }

    @Override
    public CharSequence get(CharSequence name, CharSequence defaultValue) {
        return orig.get(name, defaultValue);
    }

    @Override
    public CharSequence getAndRemove(CharSequence name) {
        return orig.getAndRemove(name);
    }

    @Override
    public CharSequence getAndRemove(CharSequence name, CharSequence defaultValue) {
        return orig.getAndRemove(name, defaultValue);
    }

    @Override
    public List<CharSequence> getAll(CharSequence name) {
        return orig.getAll(name);
    }

    @Override
    public List<CharSequence> getAllAndRemove(CharSequence name) {
        return orig.getAllAndRemove(name);
    }

    @Override
    public Boolean getBoolean(CharSequence name) {
        return orig.getBoolean(name);
    }

    @Override
    public boolean getBoolean(CharSequence name, boolean defaultValue) {
        return orig.getBoolean(name, defaultValue);
    }

    @Override
    public Byte getByte(CharSequence name) {
        return orig.getByte(name);
    }

    @Override
    public byte getByte(CharSequence name, byte defaultValue) {
        return orig.getByte(name, defaultValue);
    }

    @Override
    public Character getChar(CharSequence name) {
        return orig.getChar(name);
    }

    @Override
    public char getChar(CharSequence name, char defaultValue) {
        return orig.getChar(name, defaultValue);
    }

    @Override
    public Short getShort(CharSequence name) {
        return orig.getShort(name);
    }

    @Override
    public short getInt(CharSequence name, short defaultValue) {
        return orig.getInt(name, defaultValue);
    }

    @Override
    public Integer getInt(CharSequence name) {
        return orig.getInt(name);
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        return orig.getInt(name, defaultValue);
    }

    @Override
    public Long getLong(CharSequence name) {
        return orig.getLong(name);
    }

    @Override
    public long getLong(CharSequence name, long defaultValue) {
        return orig.getLong(name, defaultValue);
    }

    @Override
    public Float getFloat(CharSequence name) {
        return orig.getFloat(name);
    }

    @Override
    public float getFloat(CharSequence name, float defaultValue) {
        return orig.getFloat(name, defaultValue);
    }

    @Override
    public Double getDouble(CharSequence name) {
        return orig.getDouble(name);
    }

    @Override
    public double getDouble(CharSequence name, double defaultValue) {
        return orig.getDouble(name, defaultValue);
    }

    @Override
    public Long getTimeMillis(CharSequence name) {
        return orig.getTimeMillis(name);
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        return orig.getTimeMillis(name, defaultValue);
    }

    @Override
    public Boolean getBooleanAndRemove(CharSequence name) {
        return orig.getBooleanAndRemove(name);
    }

    @Override
    public boolean getBooleanAndRemove(CharSequence name, boolean defaultValue) {
        return orig.getBooleanAndRemove(name, defaultValue);
    }

    @Override
    public Byte getByteAndRemove(CharSequence name) {
        return orig.getByteAndRemove(name);
    }

    @Override
    public byte getByteAndRemove(CharSequence name, byte defaultValue) {
        return orig.getByteAndRemove(name, defaultValue);
    }

    @Override
    public Character getCharAndRemove(CharSequence name) {
        return orig.getCharAndRemove(name);
    }

    @Override
    public char getCharAndRemove(CharSequence name, char defaultValue) {
        return orig.getCharAndRemove(name, defaultValue);
    }

    @Override
    public Short getShortAndRemove(CharSequence name) {
        return orig.getShortAndRemove(name);
    }

    @Override
    public short getShortAndRemove(CharSequence name, short defaultValue) {
        return orig.getShortAndRemove(name, defaultValue);
    }

    public Integer getIntAndRemove(CharSequence name) {
        return orig.getIntAndRemove(name);
    }

    @Override
    public int getIntAndRemove(CharSequence name, int defaultValue) {
        return orig.getIntAndRemove(name, defaultValue);
    }

    @Override
    public Long getLongAndRemove(CharSequence name) {
        return orig.getLongAndRemove(name);
    }

    @Override
    public long getLongAndRemove(CharSequence name, long defaultValue) {
        return orig.getLongAndRemove(name, defaultValue);
    }

    @Override
    public Float getFloatAndRemove(CharSequence name) {
        return orig.getFloatAndRemove(name);
    }

    @Override
    public float getFloatAndRemove(CharSequence name, float defaultValue) {
        return orig.getFloatAndRemove(name, defaultValue);
    }

    @Override
    public Double getDoubleAndRemove(CharSequence name) {
        return orig.getDoubleAndRemove(name);
    }

    @Override
    public double getDoubleAndRemove(CharSequence name, double defaultValue) {
        return orig.getDoubleAndRemove(name, defaultValue);
    }

    @Override
    public Long getTimeMillisAndRemove(CharSequence name) {
        return orig.getTimeMillisAndRemove(name);
    }

    @Override
    public long getTimeMillisAndRemove(CharSequence name, long defaultValue) {
        return orig.getTimeMillisAndRemove(name, defaultValue);
    }

    @Override
    public List<Map.Entry<CharSequence, CharSequence>> entries() {
        return orig.entries();
    }

    @Override
    public boolean contains(CharSequence name) {
        return orig.contains(name);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return orig.contains(name, value);
    }

    @Override
    public boolean containsObject(CharSequence name, Object value) {
        return orig.containsObject(name, value);
    }

    @Override
    public boolean containsBoolean(CharSequence name, boolean value) {
        return orig.containsBoolean(name, value);
    }

    @Override
    public boolean containsByte(CharSequence name, byte value) {
        return orig.containsByte(name, value);
    }

    @Override
    public boolean containsChar(CharSequence name, char value) {
        return orig.containsChar(name, value);
    }

    @Override
    public boolean containsShort(CharSequence name, short value) {
        return orig.containsShort(name, value);
    }

    @Override
    public boolean containsInt(CharSequence name, int value) {
        return orig.containsInt(name, value);
    }

    @Override
    public boolean containsLong(CharSequence name, long value) {
        return orig.containsLong(name, value);
    }

    @Override
    public boolean containsFloat(CharSequence name, float value) {
        return orig.containsFloat(name, value);
    }

    @Override
    public boolean containsDouble(CharSequence name, double value) {
        return orig.containsDouble(name, value);
    }

    @Override
    public boolean containsTimeMillis(CharSequence name, long value) {
        return orig.containsTimeMillis(name, value);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, Comparator<? super CharSequence> comparator) {
        return orig.contains(name, value, comparator);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, Comparator<? super CharSequence> keyComparator, Comparator<? super CharSequence> valueComparator) {
        return orig.contains(name, value, keyComparator, valueComparator);
    }

    @Override
    public boolean containsObject(CharSequence name, Object value, Comparator<? super CharSequence> comparator) {
        return orig.containsObject(name, value, comparator);
    }

    @Override
    public boolean containsObject(CharSequence name, Object value, Comparator<? super CharSequence> keyComparator, Comparator<? super CharSequence> valueComparator) {
        return orig.containsObject(name, value, keyComparator, valueComparator);
    }

    @Override
    public int size() {
        return orig.size();
    }

    @Override
    public boolean isEmpty() {
        return orig.isEmpty();
    }

    @Override
    public Set<CharSequence> names() {
        return orig.names();
    }

    @Override
    public List<CharSequence> namesList() {
        return orig.namesList();
    }

    @Override
    public io.netty.handler.codec.Headers<CharSequence> add(io.netty.handler.codec.Headers<CharSequence> headers) {
        return orig.add(headers);
    }

    @Override
    public io.netty.handler.codec.Headers<CharSequence> set(io.netty.handler.codec.Headers<CharSequence> headers) {
        return orig.set(headers);
    }

    @Override
    public io.netty.handler.codec.Headers<CharSequence> setAll(io.netty.handler.codec.Headers<CharSequence> headers) {
        return orig.setAll(headers);
    }

    @Override
    public boolean remove(CharSequence name) {
        return orig.remove(name);
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return orig.iterator();
    }

    @Override
    public void forEach(Consumer<? super Map.Entry<CharSequence, CharSequence>> cnsmr) {
        orig.forEach(cnsmr);
    }

    public Spliterator<Map.Entry<CharSequence, CharSequence>> spliterator() {
        return orig.spliterator();
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return orig.contains(name, value, ignoreCase);
    }

    @Override
    public boolean containsObject(CharSequence name, Object value, boolean ignoreCase) {
        return orig.containsObject(name, value, ignoreCase);
    }

    @Override
    public HttpHeaders addTimeMillis(CharSequence name, long value) {
        orig.addTimeMillis(name, value);
        return this;
    }

    @Override
    public HttpHeaders setTimeMillis(CharSequence name, long value) {
        orig.setTimeMillis(name, value);
        return this;
    }

    @Override
    public Map.Entry<CharSequence, CharSequence> forEachEntry(Headers.EntryVisitor<CharSequence> visitor) throws Exception {
        return orig.forEachEntry(visitor);
    }

    @Override
    public CharSequence forEachName(Headers.NameVisitor<CharSequence> visitor) throws Exception {
        return orig.forEachName(visitor);
    }
}
