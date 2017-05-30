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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

/**
 * Header type for headers that are array types.
 * 
 * @author Tim Boudreau
 */
public abstract class ArrayHeaderValueType<T, R> extends AbstractHeader<T> {

    private final Class<R> componentType;

    public ArrayHeaderValueType(Class<T> type, CharSequence name, Class<R> componentType) {
        super(type, name);
        this.componentType = componentType;
        if (!type.isArray()) {
            throw new IllegalArgumentException(type.getName() + " is not an array type");
        }
        if (componentType != type.getComponentType()) {
            throw new IllegalArgumentException(componentType.getName() + " is not the array component type of " + type.getName());
        }
    }

    public Class<R> componentType() {
        return componentType;
    }

    @SuppressWarnings("unchecked")
    public CharSequence toStringSingle(R single) {
        R[] rs = (R[]) Array.newInstance(componentType, 1);
        rs[0] = single;
        return toCharSequence((T) rs);
    }

    public CharSequence toString(Collection<R> all) {
        if (all.size() == 1) {
            return toStringSingle(all.iterator().next());
        }
        R[] rs = (R[]) Array.newInstance(componentType, all.size());
        Iterator<R> iter = all.iterator();
        for (int i = 0; i < rs.length; i++) {
            rs[i] = iter.next();
        }
        return toCharSequence((T) rs);
    }
}
