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

import io.netty.handler.codec.http.HttpHeaders;

/**
 *
 * @author Tim Boudreau
 */
final class VaryHeader extends AbstractHeader<HeaderValueType[]> {

    VaryHeader() {
        super(HeaderValueType[].class, HttpHeaders.Names.VARY.toString());
    }

    @Override
    public String toString(HeaderValueType[] value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length; i++) {
            sb.append(value[i].name());
            if (i != value.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    @Override
    public HeaderValueType<?>[] toValue(String value) {
        String[] s = value.split(",");
        HeaderValueType<?>[] result = new HeaderValueType<?>[s.length];
        for (int i = 0; i < s.length; i++) {
            result[i] = new StringHeader(s[i].trim());
        }
        return result;
    }

}
