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

import com.mastfrog.util.strings.Strings;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractHeader<T> implements HeaderValueType<T> {
    private final Class<T> type;
    private final CharSequence name;

    protected AbstractHeader(Class<T> type, CharSequence name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public final CharSequence name() {
        return name;
    }

    @Override
    public final Class<T> type() {
        return type;
    }

    @Override
    public final String toString() {
        return name.toString();
    }

    @Override
    public final boolean equals(Object obj) {
        return obj == null ? false : obj == this ? true : obj instanceof HeaderValueType<?> 
                && Strings.charSequencesEqual(name(), ((HeaderValueType<?>) obj).name(), true);
    }

    @Override
    public final int hashCode() {
        int hash = 7;
        hash = 79 * hash + this.type().hashCode();
        hash = 79 * hash + this.name().hashCode();
        return hash;
    }

}
