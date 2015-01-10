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

/**
 * Does the plumbing of allowing an AbstractActeur to work with a ChainRunner.
 * The typical use case of AbstractActeur is to write a subclass for your users
 * to subclass, which provides a specific state type and hides the generic
 * signatures.
 *
 * @param <T> The exposed type
 * @param <ImplType> The implementation type
 */
public abstract class ActeurResponseFactory<T, ImplType extends T> {

    /**
     * Create whatever object the acteur chain assembles, such as an http
     * response.
     *
     * @return A new object
     */
    protected abstract ImplType create();

    /**
     * Determine if the state of the response object indicates that this chain
     * is finished, and no later acteurs should be run.
     *
     * @param obj The object
     * @return Whether or not no further acteurs in the chain should be executed
     */
    protected abstract boolean isFinished(ImplType obj);

    /**
     * Determine whether execution of the acteur has altered the state of the
     * response object.
     *
     * @param obj The response object
     * @return Whether or not it is modified and should be passed to the
     * {@link OnDone}, or whether it can be omitted.
     */
    protected abstract boolean isModified(ImplType obj);

}
