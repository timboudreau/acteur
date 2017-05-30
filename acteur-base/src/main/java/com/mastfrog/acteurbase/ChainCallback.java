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

import java.util.List;

/**
 * Callback which is notified on various events while a chain is being run.
 *
 * @author Tim Boudreau
 */
public interface ChainCallback<A extends AbstractActeur<T, R, S>, S extends ActeurState<T, R>, P extends Chain<? extends AbstractActeur<T, R, ?>>, T, R extends T> {

    /**
     * Executing the chain or chains has completed, with the passed state and
     * the passed list of objects.
     *
     * @param state The state
     * @param responses A list of response objects constructed by some or all of
     * the AbstractActeurs that ran
     */
    void onDone(S state, List<R> responses);

    /**
     * An acteur indicated it cannot process whatever it was processing and is
     * kicking control back to the callback.
     *
     * @param state The state + acteur that rejected it
     */
    void onRejected(S state);

    /**
     * Called when the chain was completed without any acteur finishing the
     * work.
     */
    void onNoResponse();

    /**
     * Called before each AbstractActeur in the chain is run. The acteur has not
     * yet been instantiated when this is called. Used by the Acteur web
     * framework to set the threadlocal reference to the Page, which its State
     * subclasses need access to.
     *
     * @param chain The chain in question
     */
    void onBeforeRunOne(P chain);
    
    default void onBeforeRunOne(P chain, List<R> responsesThusFar) {
        //do nothing
    }

    /**
     * Called after each AbstractActeur is run.
     *
     * @param chain The chain
     * @param acteur The acteur that was just run
     */
    void onAfterRunOne(P chain, A acteur);

    /**
     * Called if an exception is thrown during processing. Execution of the
     * chain is aborted if this is called.
     *
     * @param ex The throwable
     */
    void onFailure(Throwable ex);
}
