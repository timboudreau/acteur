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
package com.mastfrog.acteur;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which can be placed on instances of Application to add bindings in
 * that application's scope. If you write Acteurs which will include new objects
 * for injection into subsequent acteurs in the chain, you need to annotation
 * your Application subclass with this annotation and specify what types will be
 * injected.
 * <p/>
 * <b>Note:</b> If you are using the &#064;HttpCall annotation, you can specify
 * these classes there.
 *
 * @deprecated - With Acteur 1.5 and ServerBuilder, you are not subclassing
 * application anymore, so this class is less useful as it can only appear on
 * the application class. Instead, use {@link HttpCall} with the
 * <code>scopeTypes</code> parameter register types on the acteurs that use
 * them; or use ServerBuilder.withType().
 *
 * @author Tim Boudreau
 */
@Deprecated
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface ImplicitBindings {

    /**
     * A list of types which Acteurs will pass in their state, which should
     * be injectable into subsequent acteurs.
     * @return The list of classes
     */
    public Class<?>[] value();
}
