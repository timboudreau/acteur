/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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

import com.mastfrog.util.Strings;
import com.mastfrog.util.collections.Converter;

/**
 * Base interface for things that convert an HTTP header to an appropriate
 * Java object and back.  See <a href="./Headers.html">Headers</a> for
 * useful implementations.
 *
 * @see HeaderValueType
 * @author Tim Boudreau
 */
public interface HeaderValueType<T> extends Converter<T, CharSequence> {
    /**
     * The Java type
     * @return A type
     */
    public Class<T> type();

    /**
     * The header name as it should appear in HTTP headers
     * @return The name
     */
    public CharSequence name();
    
    public default boolean is(CharSequence seq) {
        return Strings.charSequencesEqual(name(), seq, true);
    }

    /**
     * Convert an object to a String suitable for inclusion in headers.
     * 
     * @param value A value
     * @return A header value
     */
    public String toString(T value);
    
    /**
     * Parse the value of a header of this type, returning an appropriate
     * Java object or null
     * @param value A header
     * @return An object that represents the header appropriately, such as a
     * <code>DateTime</code> for a date header.
     */
    public T toValue(String value);

    @Override
    public default T convert(CharSequence r) {
        return toValue(r.toString());
    }

    @Override
    public default CharSequence unconvert(T t) {
        return toString(t);
    }
}
