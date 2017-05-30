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

import com.mastfrog.acteurbase.ActeurState;

/**
 * The output condition of an {@link Acteur}.  An acteur must either set its state
 * in its constructor, or override getState().  As of 1.6, Acteur has methods such
 * as <code>next()</code>, <code>reject()</code>, <code>reply()</code> that will
 * create one of these under-the=hood, so directly using this class is rare.
 */
public abstract class State extends ActeurState<Response, ResponseImpl> {

    protected final Page page;

    State(Object... context) {
        super(context);
        page = Page.get();
        if (page == null) {
            throw new IllegalStateException("Page not set");
        }
    }

    State(boolean rejected) {
        super(rejected);
        page = Page.get();
        if (page == null) {
            throw new IllegalStateException("Page not set");
        }
    }

    @Override
    public boolean isRejected() {
        return super.isRejected();
    }

    /**
     * Returns whether the event processing has to be stopped after the
     * processing by the chain the acteur producing this state belongs to - the
     * request will not be processed by other "page" chains if this one rejects
     * it.
     *
     * @return true if locked in chain
     * @deprecated no longer used
     */
    @Deprecated
    protected boolean isLockedInChain() {
        return !isRejected();
    }

    /**
     * Returns whether (and by which "page") the next event has to be processed
     * prior to regular processing.
     *
     * @return the locked widget; if null, then there is no prior widget
     */
    public final Page getLockedPage() {
        return page;
    }

    public final Object[] getContext() {
        return super.context();
    }

    /**
     * Returns whether (and by which action) the next event has to be processed
     * prior to regular processing.
     *
     * @return the locked action; if null, then there is no prior action
     */
    @Override
    public abstract Acteur getActeur();

}
