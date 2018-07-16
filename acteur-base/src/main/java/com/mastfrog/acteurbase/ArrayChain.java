/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteurbase;

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.ConfigurationError;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Base class for Chain implementations - a thing you can add either class
 * objects of type T or objects of type T to, and it will provide an
 * <code>Iterator&lt;T&gt;</code> over the result.
 *
 * @author Tim Boudreau
 */
public class ArrayChain<T, C extends ArrayChain<T, C>> implements Chain<T, C> {

    protected final List<Object> types = new LinkedList<>();
    protected final Dependencies deps;
    protected final Class<? super T> type;
    protected AtomicInteger chainPosition;

    @SuppressWarnings("unchecked")
    public ArrayChain(Dependencies deps, Class<? super T> type, List<Object> objs) {
        this(deps, type);
        for (Object o : objs) {
            if (o == null) {
                throw new ConfigurationError("Null in acteur list");
            }
            if (o instanceof Class<?>) {
                Class<?> c = (Class<?>) o;
                if (!type.isAssignableFrom(c)) {
                    throw new ConfigurationError(c.getName() + " is not a subtype of " + type.getName());
                }
                this.add((Class<? extends T>) o);
            } else {
                T t = (T) type.cast(o);
                this.add(t);
            }
        }
    }

    public ArrayChain(Dependencies deps, Class<? super T> type) {
        Checks.notNull("deps", deps);
        Checks.notNull("type", type);
        this.deps = deps;
        this.type = type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Supplier<C> remnantSupplier(Object... scopeContents) {
        assert chainPosition != null : "Called out of sequence";
        int pos = chainPosition.get();
        final List<Object> rem = new ArrayList<>(types.size() - pos);
        for (int i = pos; i < types.size(); i++) {
            rem.add(types.get(i));
        }
        return () -> {
            List<Object> l = new ArrayList<>(rem);
            return (C) new ArrayChain<T, C>(deps, type, l);
        };
    }

    @SuppressWarnings("unchecked")
    public final C add(Class<? extends T> type) {
        Checks.notNull("type", type);
        if (!this.type.isAssignableFrom(type)) {
            throw new ConfigurationError(type.getName() + " is not a " + this.type.getName());
        }
        if ((type.getModifiers() & Modifier.ABSTRACT) != 0) {
            throw new ConfigurationError(type + " is abstract");
        }
        if (type.isLocalClass()) {
            throw new ConfigurationError(type + " is an inner class");
        }
        if (type.isArray()) {
            throw new ConfigurationError(type + " is an array type");
        }
        if (type.isAnnotation()) {
            throw new ConfigurationError(type + " is an annotation type");
        }
        types.add(type);
        return (C) this;
    }

    private boolean validElement (Object obj) {
        if (type.isInstance(obj)) {
            return true;
        }
        if (obj instanceof Class<?>) {
            Class<?> c = (Class<?>) obj;
            if (type.isAssignableFrom(c)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public final C insert(T obj) {
        Checks.notNull("obj", obj);
        if (!validElement(obj)) {
            throw new ConfigurationError("Not an instance of " + this.type.getName() + ": " + obj);
        }
        int pos = chainPosition.get();
        types.add(pos, obj);
        return (C) this;
    }

    @SuppressWarnings("unchecked")
    public final C add(T obj) {
        Checks.notNull("obj", obj);
        if (!validElement(obj)) {
            throw new ConfigurationError("Not an instance of " + this.type.getName() + ": " + obj);
        }
        types.add(obj);
        return (C) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<T> iterator() {
        return (Iterator<T>) new InstantiatingIterators(deps, chainPosition = new AtomicInteger()).iterable(types, type).iterator();
    }
}
