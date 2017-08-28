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

import java.util.function.Supplier;

/**
 * A chain of objects which can be added to by either adding class objects or
 * instances, which will take care of instantiating the class objects on the
 * fly.
 * <p>
 * Acteurs can ask for an instance of Chain to be injected, and can insert
 * additional objects into the chain on the fly.
 *
 * @author Tim Boudreau
 */
public interface Chain<T, C extends Chain<T, C>> extends Iterable<T> {

    /**
     * Add an object of type T to this chain.
     *
     * @param obj The object, non null, checked for type safety
     * @return this
     */
    C add(T obj);

    /**
     * Add a type which should be instantiated by the iterator on-demand. The
     * type must be a subtype of T, must not be an inner class or abstract
     * class. Any of these will throw an immediate error.
     *
     * @param type The type
     * @return this
     */
    C add(Class<? extends T> type);

    /**
     * Get any objects this chain should contribute into the injection context.
     *
     * @return an array of objects
     */
    default Object[] getContextContribution() {
        return new Object[0];
    }

    /**
     * Insert an object at the next position in the chain, while iterating it.
     *
     * @param obj A new object to include
     * @return this
     */
    C insert(T obj);

    Supplier<C> remnantSupplier(Object... scopeTypes);
}
