/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Encapsulates either an object indicating success, or a throwable indicating
 * failure, which is written to a response using DeferredComputationResultActeur
 * (added automatically to the chain if you use
 * <code>Acteur.then(CompletableFuture)</code>.
 *
 * @see com.mastfrog.acteur.Acteur
 * @author Tim Boudreau
 */
public final class DeferredComputationResult {

    public final Object what;
    public final Throwable thrown;
    public final HttpResponseStatus onSuccess;
    private static final DeferredComputationResult EMPTY = new DeferredComputationResult(null, null, null);

    DeferredComputationResult(Object what, Throwable thrown, HttpResponseStatus onSuccess) {
        this.what = what;
        this.thrown = thrown;
        this.onSuccess = onSuccess;
    }

    /**
     * Get the payload as a specific type.
     *
     * @param <T> The type
     * @param type The class of the type
     * @throws ClassCastException if the payload object is non null and not a
     * matching type
     * @return The object cast as the passed type
     */
    public <T> T as(Class<T> type) {
        return what == null ? null : type.cast(what);
    }

    public static DeferredComputationResult empty() {
        return EMPTY;
    }

    public static DeferredComputationResult of(Object what) {
        return new DeferredComputationResult(what, null, null);
    }

    public static DeferredComputationResult of(Object what, HttpResponseStatus onSuccess) {
        return new DeferredComputationResult(what, null, onSuccess);
    }

    public static DeferredComputationResult thrown(Throwable thrown) {
        return new DeferredComputationResult(null, thrown, null);
    }
}
