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

import com.mastfrog.util.preconditions.Checks;

/**
 * Base class for Acteurs. Implementations should implement ResponseFactory and
 * not expose generic types directly.
 * <p>
 * An acteur is a function object which uses its constructor to do whatever work
 * it is going to do. The output of the constructor is its {@link State}.
 * <p>
 * An acteur also has a response object, which it may alter. Acteurs in a chain
 * each have their own response object (for thread-safety purposes); these are
 * coalesced into a final result.
 * <p>
 * Acteurs may provide objects to subsequent acteurs in a chain by including
 * them in the state's context.
 * <p>
 * Types which are passed between Acteurs need to be registered with the Guice
 * binder and the {@link com.mastfrog.guicy.scope.ReentrantScope} used by the
 * {@link ChainRunner}.
 *
 * @author Tim Boudreau
 */
public class AbstractActeur<T, R extends T, S extends ActeurState<T, R>> {

    private S state;
    private R response;
    final ActeurResponseFactory<T, R> factory;
    /**
     * If running with assertions enabled, this will be a throwable which can be
     * used to determine the instantiation point of this AbstractActeur.
     */
    protected Throwable creationStackTrace;

    /**
     * Create a new AbstractActeur.
     *
     * @param factory The thing which is responsible for creating the response
     * object on-demand, and determining if it is modified and so should be
     * included in the list of response objects passed to the callback when the
     * chain is completed.
     */
    protected AbstractActeur(ActeurResponseFactory<T, R> factory) {
        Checks.notNull("factory", factory);
        this.factory = factory;
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
            throw new IllegalStateException("State not set in " + getClass().getName());
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

    /**
     * Getter for the response which will not create it if nothing else has
     * triggered its creation.
     *
     * @return
     */
    protected R getResponse() {
        return response;
    }
}
