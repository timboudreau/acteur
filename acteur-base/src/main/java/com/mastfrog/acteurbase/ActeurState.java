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

import java.util.Arrays;

/**
 * Base class for State objects for AbstractActeurs
 *
 * @see AbstractActeur
 * @param <T> The public type
 * @param <R> The implementation type
 */
public abstract class ActeurState<T, R extends T> {

    private static final Object[] NOTHING = new Object[0];
    private final boolean rejected;
    protected final Throwable creationStackTrace;

    /**
     * Create a state with no context objects which either rejects or finishes
     * the chain.
     *
     * @param rejected If true, the acteur producing this state will be the last
     * one executed on this chain
     */
    protected ActeurState(boolean rejected) {
        this.rejected = rejected;
        boolean asserts = false;
        assert asserts = true;
        if (asserts) {
            creationStackTrace = new Throwable();
        } else {
            creationStackTrace = null;
        }
    }

    /**
     * Determine if this state is a rejection of the input - meaning that
     * subsequent acteurs should not be created/invoked.
     *
     * @return Whether or not it is rejected
     */
    protected boolean isRejected() {
        return rejected;
    }

    /**
     * Any context objects this state makes available to subsequent acteurs in
     * the chain. May return null if not set.
     *
     * @return An array of objects or null
     */
    protected Object[] context() {
        return NOTHING;
    }

    /**
     * Get the acteur that produced this state.
     *
     * @return the acteur, never null
     */
    protected abstract AbstractActeur<T, R, ?> getActeur();

    /**
     * Determines if this state indicates successful conclusion of the work of
     * the chain of acteurs that produced it.
     *
     * @return true if the {@link ActeurResponseFactory} associated with the
     * producing {@link AbstractActeur} says that the response object's state
     * indicates it is finished.
     */
    public boolean isFinished() {
        AbstractActeur<T, R, ?> acteur = getActeur();
        if (getActeur() == null) {
            IllegalStateException ex = new IllegalStateException(getClass().getName()
                    + " does not have its acteur set");
            if (creationStackTrace != null) {
                ex.initCause(creationStackTrace);
            }
            throw ex;
        }
        R r = getActeur().getResponse();
        return r != null && getActeur().factory.isFinished(r);
    }

    /**
     * Get the response object, if it has been created and if the
     * {@link ActeurResponseFactory} says it has been modified.
     *
     * @return the response object or null
     */
    R response() {
        // getResponse() only returns non-null if the response was actually
        // created by a write method.
        R r = getActeur().getResponse();
        return r != null && getActeur().factory.isModified(r) ? r : null;
    }

    @Override
    public String toString() {
        Object[] context = context();
        return getClass().getName() + " rej? " + rejected + " for " + getActeur()
                + " with " + (context == null || context.length == 0 ? " (none)"
                        : Arrays.asList(context).toString());
    }

}
