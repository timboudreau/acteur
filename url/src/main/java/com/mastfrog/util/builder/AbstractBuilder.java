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

import com.mastfrog.util.preconditions.Checks;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A basic implementation of the builder pattern.  Mutable factory for
 * some immutable object which is composed of multiple objects of the same type.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractBuilder<ElementType, CreateType> implements Builder<ElementType, CreateType, AbstractBuilder<ElementType, CreateType>> {
    protected final List<ElementType> elements = new LinkedList<>();
    protected AbstractBuilder() {

    }

    @Override
    public AbstractBuilder<ElementType, CreateType> add (ElementType element) {
        Checks.notNull("element", element);
        onBeforeAdd(element);
        elements.add (element);
        onAfterAdd(element);
        return this;
    }
    
    protected void onAfterAdd (ElementType element) {
        
    }

    @Override
    public final AbstractBuilder<ElementType, CreateType> add (String string) {
        Checks.notNull("string", string);
        return add (createElement(string));
    }

    protected List<ElementType> elements() {
        return new ArrayList<>(elements);
    }

    protected AbstractBuilder<ElementType, CreateType> addElement(ElementType element) {
        Checks.notNull("element", element);
        onBeforeAdd(element);
        elements.add(element);
        return this;
    }

    protected int size() {
        return elements.size();
    }
    protected abstract ElementType createElement(String string);
    /**
     * Hook which can be overridden when an element is added.
     * @param toAdd
     */
    protected void onBeforeAdd (ElementType toAdd) {
        //do nothing
    }
}
