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

/**
 *
 * @author Tim Boudreau
 */
class HeaderNamesHeader extends AbstractHeader<HeaderValueType<?>[]> {

    @SuppressWarnings(value = "unchecked")
    HeaderNamesHeader(CharSequence name) {
        super((Class) HeaderValueType[].class, name);
    }

    @Override
    public String toString(HeaderValueType[] value) {
        StringBuilder sb = new StringBuilder();
        for (HeaderValueType t : value) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.name());
        }
        return sb.toString();
    }

    @Override
    public HeaderValueType<?>[] toValue(CharSequence value) {
        if (value == null || value.length() == 0) {
            return new HeaderValueType<?>[0];
        }
        CharSequence[] items = Strings.split(',', value);
        HeaderValueType<?>[] result = new HeaderValueType<?>[items.length];
        for (int i = 0; i < items.length; i++) {
            result[i] = Headers.header(items[i]);
        }
        return result;
    }
}
