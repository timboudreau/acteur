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
package com.mastfrog.acteur.util;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractHeader<T> implements HeaderValueType<T> {
    private final Class<T> type;
    private final String name;

    AbstractHeader(Class<T> type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HeaderValueType<?> && ((HeaderValueType<?>) obj).type() == type() 
                && ((HeaderValueType<?>) obj).name().equals(name());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + this.type.hashCode();
        hash = 79 * hash + this.name.hashCode();
        return hash;
    }

}
