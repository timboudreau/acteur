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
package com.mastfrog.acteur.base;

import com.google.inject.Injector;
import com.mastfrog.util.ConfigurationError;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public abstract class Chain<T> implements Iterable<T> {

    private final Class<T> type;
    private final List<Object> items = new LinkedList<>();

    public Chain(Class<T> type) {
        this.type = type;
    }

    protected void onBeforeAdd(Class<? extends T> itemType) {

    }

    protected void onBeforeAdd(T item) {

    }

    protected final Chain<T> add(Class<? extends T> itemType) {
        onBeforeAdd(itemType);
        if (!type().isAssignableFrom(itemType)) {
            if (!type().isInstance(itemType)) {
                throw new ClassCastException("Not a subtype of " + type() + ": " + itemType);
            }
        }
        if ((itemType.getModifiers() & Modifier.ABSTRACT) != 0) {
            throw new ConfigurationError(itemType + " is abstract");
        }
        if (itemType.isLocalClass()) {
            throw new ConfigurationError(itemType + " is not a top-level class");
        }
        items.add(itemType);
        return this;
    }

    protected final Chain<T> add(T item) {
        onBeforeAdd(item);
        if (!type().isInstance(item)) {
            throw new ClassCastException("Not an instance of " + type + ": " + item);
        }
        items.add(type);
        return this;
    }

    public Class<T> type() {
        return type;
    }

    protected abstract Injector injector();

    @Override
    public final Iterator<T> iterator() {
        InstantiatingIterators iters = new InstantiatingIterators(injector());
        return iters.iterable(items, type).iterator();
    }
}
