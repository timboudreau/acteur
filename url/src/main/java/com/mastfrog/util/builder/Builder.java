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
package com.mastfrog.util.builder;

/**
 * Basic abstraction for the builder pattern.  A builder can have elements
 * added to it, and once some number of elements have been added, can use those
 * elements to construct an object of some sort.
 * <p/>
 * Builders are meant to be used once to create an object and then thrown
 * away, not reused.
 * <p/>
 * This class is parameterized on (typically) its own type so that, if additional
 * methods are required (for example, URLBuilder lets you set some things and
 * add others), the exact builder subclass does not need to be exposed.
 *
 * @author Tim Boudreau
 */
public interface Builder<ElementType, CreateType, BuilderType extends Builder<ElementType,CreateType, BuilderType>> {
    /**
     * Add an element
     * <p/>
     * May throw an exception if called after <code>create()</code> has been called.
     * @param element The element
     * @return this, or a new builder (callers should not assume all implementations return <code>this</code>)
     */
    BuilderType add(ElementType element);
    /**
     * Add an element as a string.  Optional operation, may throw UnsupportedOperationException.
     * <p/>
     * May throw an exception if called after <code>create()</code> has been called.
     * 
     * @param element The element as a string which will be parsed
     * @return this, or a new builder (callers should not assume all implementations return <code>this</code>)
     */
    BuilderType add(String string);
    /**
     * Create an object.  
     * A Builder should be thrown away after a call to this
     * method, and implementations will typically throw an exception if this is
     * called twice.
     * 
     * @return The object this builder creates, which somehow composes together
     * the elements that were passed to it
     */
    CreateType create();

    default CreateType build() {
        return create();
    }
}
