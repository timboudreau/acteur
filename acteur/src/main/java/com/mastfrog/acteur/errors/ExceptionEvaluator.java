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
package com.mastfrog.acteur.errors;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.giulius.Ordered;
import javax.inject.Singleton;

/**
 * Converts exceptions thrown inside Acteurs into error messages.  
 * Multiple ones can be registered.  To register, simply bind your
 * implementation as an eager singleton in your Guice module.
 * <p>
 * Note that this class does not suppress <i>logging</i> of exceptions -
 * to do that, implement an ErrorHandler and bind it as an eager singleton.
 * This simply decides how to convert a particular exception into an
 * HTTP response.
 *
 * @author Tim Boudreau
 */
@Singleton
public abstract class ExceptionEvaluator implements Comparable<ExceptionEvaluator> {

    @SuppressWarnings("LeakingThisInConstructor")
    protected ExceptionEvaluator(ExceptionEvaluatorRegistry registry) {
        registry.register(this);
    }
    
    protected final Throwable unwind(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    protected int ordinal() {
        Ordered ord = getClass().getAnnotation(Ordered.class);
        if (ord != null) {
            return ord.value();
        }
        return 1;
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        return o != null && o.getClass() == getClass();
    }

    @Override
    public final int compareTo(ExceptionEvaluator o) {
        Integer mine = ordinal();
        Integer theirs = o.ordinal();
        return theirs.compareTo(mine);
    }

    public abstract ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, HttpEvent evt);
}
