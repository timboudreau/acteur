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

package com.mastfrog.acteur.annotations;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Annotation applied to generated classes as a pointer back to the type
 * that declared the annotations that generated it.
 * <p/>
 * You don't need to apply this to your own classes - it will be attached
 * to your generated classes.  It is used internally by the framework to
 * locate the original annotations for runtime processing.
 *
 * @author Tim Boudreau
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Origin {
    // Moved here from numble, to eliminate Acteur's dependency on it
    public static final String META_INF_PATH = "META-INF/http/numble.list";
    /**
     * The class, annotations on which were used to generate this class.  Since
     * validation information is retained at runtime, integrations may use this
     * to look up enough information to validate the data before constructing
     * an object.
     * @return The type
     */
    Class<?> value();
}
