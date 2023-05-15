/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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

import com.google.inject.ImplementedBy;

/**
 * Allows an Acteur to dynamically modify the tail of the request-processing
 * chain it is a part of. This is rarely, but occasionally, needed when the next
 * step <i>really</i> cannot be determined at compile-time.
 * <p>
 * The few examples are database libraries which need to do very different
 * response generation processing depending on the size of a returned cursor.
 * </p>
 *
 * @author Tim Boudreau
 */
@ImplementedBy(CurrentChainImpl.class)
public sealed interface CurrentChain permits CurrentChainImpl {

    /**
     * Add an instantiated Acteur to the end of the current request-processing
     * chain, for the current request only..
     *
     * @param obj An acteur
     * @return this
     */
    CurrentChain add(Acteur obj);

    /**
     * Add an instantiated Acteur to the end of the current request-processing
     * chain, for the current request only..
     *
     * @param type An Acteur class
     * @return this
     */
    CurrentChain add(Class<? extends Acteur> type);

    /**
     * Insert an instantiated Acteur immediately after the currently running one
     * in the current request-processing chain, for the current request only.
     *
     * @param obj An acteur
     * @return this
     */
    CurrentChain insert(Acteur obj);

    /**
     * Insert an instantiated Acteur immediately after the currently running one
     * in the current request-processing chain, for the current request only.
     *
     * @param obj An acteur
     * @return this
     */
    CurrentChain insert(Class<? extends Acteur> obj);

}
