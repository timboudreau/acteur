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

package com.mastfrog.acteur.headers;

import com.mastfrog.util.Checks;
import com.mastfrog.util.Strings;

/**
 *
 * @author Tim Boudreau
 */
final class StringArrayHeader extends AbstractHeader<String[]> {

    StringArrayHeader(CharSequence name) {
        super(String[].class, name);
    }
    
    @Override
    public String toString(String... value) {
        Checks.notNull("value", value);
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < value.length; i++) {
            sb.append(value[i]);
            if (i != value.length-1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    @Override
    public String[] toValue(CharSequence value) {
        Checks.notNull("value", value);
        CharSequence[] result = Strings.split(',', value);
        String[] rr = new String[result.length];
        for (int i = 0; i < result.length; i++) {
            rr[i] = result[i] instanceof String ? (String) result[i] :
                    result[i].toString();
        }
        return rr;
    }
}
