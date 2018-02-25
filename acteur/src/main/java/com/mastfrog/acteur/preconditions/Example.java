/*
 * The MIT License
 *
 * Copyright 2018 tim.
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
package com.mastfrog.acteur.preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows a call to include an example URL and sample inputs and outputs in the
 * generated documentation.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Description("Example use of this HTTP call")
public @interface Example {

    /**
     * Example usage - typically a sample URL.
     *
     * @return
     */
    String value() default "";

    /**
     * The class to look for the value of <code>inputField()</code> on, to
     * instantiate and convert to JSON an example of input to this method. If a
     * string, will be rendered in the help in pre tags. The field must be
     * static but need not be public.
     *
     * @return A field name
     */
    Class<?> inputType() default Object.class;

    /**
     * The name of the field on the class returned by <code>inputType()</code>
     * to find an object which can be rendered as JSON sample input in the
     * documentation of this call.
     *
     * @return A field name
     */
    String inputField() default "EXAMPLE";

    /**
     * The class to look for the value of <code>outputField()</code> on, to
     * instantiate and convert to JSON an example of output of this method. If a
     * string, will be rendered in the help in pre tags.
     *
     * @return A field name
     */
    Class<?> outputType() default Object.class;

    /**
     * The name of the field on the class returned by <code>outputType()</code>
     * to find an object which can be rendered as JSON sample output in the
     * documentation of this call. The field must be static but need not be
     * public.
     *
     * @return A field name
     */
    String outputField() default "EXAMPLE";
}
