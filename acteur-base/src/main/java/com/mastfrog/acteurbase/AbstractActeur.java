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

import com.mastfrog.util.Checks;
import java.util.Arrays;

/**
 * Base class for Acteurs. Implementations should implement ResponseFactory and
 * not expose generic types directly.
 *
 * @author Tim Boudreau
 */
public class AbstractActeur<T, R extends T, S extends AbstractActeur.State<T, R>> {

    private S state;
    private R response;
    private final ActeurResponseFactory<T, R> factory;
    protected Throwable creationStackTrace;

    public AbstractActeur(ActeurResponseFactory<T, R> rf) {
        this.factory = rf;
        boolean asserts = false;
        assert asserts = true;
        if (asserts) {
            creationStackTrace = new Throwable();
        }
    }

    /**
     * Set the state of this acteur.
     *
     * @param state The new state, must be non null.
     */
    protected void setState(S state) {
        Checks.notNull("state", state);
        this.state = state;
        state.acteur = this;
    }

    /**
     * Get the state of this acteur.
     *
     * @return The state, if setcreationStackTrace
     * @throws IllegalStateException if the state has not been set
     */
    protected S getState() {
        if (state == null) {
            throw new IllegalStateException("State not set");
        }
        return state;
    }

    /**
     * Get the response object as the public type, creating it if necessary.
     *
     * @return The response
     */
    protected T response() {
        if (response == null) {
            response = factory.create();
        }
        return response;
    }

    protected R getResponse() {
        return response;
    }

    /**
     * State of an Acteur
     *
     * @param <T> The public type
     * @param <R> The implementation type
     */
    public static class State<T, R extends T> {

        private final Object[] context;
        private final boolean rejected;
        AbstractActeur<T, R, ?> acteur;
        protected Throwable creationStackTrace;

        private State(boolean rejected, Object... context) {
            this.context = context;
            this.rejected = rejected;
            boolean asserts = false;
            assert asserts = true;
            if (asserts) {
                creationStackTrace = new Throwable();
            }
        }

        public State(Object... context) {
            this(false, context);
        }

        public State(boolean rejected) {
            this(rejected, (Object[]) null);
        }

        protected boolean isRejected() {
            return rejected;
        }

        protected Object[] context() {
            return context;
        }

        protected AbstractActeur<T, R, ?> getActeur() {
            return acteur;
        }

        public boolean isFinished() {
            AbstractActeur<T, R, ?> acteur = getActeur();
            if (getActeur() == null) {
                IllegalStateException ex = new IllegalStateException(getClass().getName()
                        + " does not have its acteur set");
                if (creationStackTrace != null) {
                    ex.initCause(creationStackTrace);
                }
                ex.printStackTrace();
                throw ex;
            }
            R r = getActeur().getResponse();
            return r != null && getActeur().factory.isFinished(r);
        }

        R response() {
            R r = getActeur().getResponse();
            return r != null && getActeur().factory.isModified(r) ? r : null;
        }

        public String toString() {
            return getClass().getName() + " rej? " + rejected
                    + " for " + getActeur() + " with " + (context == null
                            ? " (none0)" : Arrays.asList(context).toString());
        }
    }
}
