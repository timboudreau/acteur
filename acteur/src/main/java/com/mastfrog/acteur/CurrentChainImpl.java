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

import com.mastfrog.acteurbase.Chain;

/**
 * Implementation of CurrentChain, which exists to hide the rather ugly
 * signature you have to inject for Chain, not to mention methods on Chain that
 * are needed for page runners but absolutely should not be called by an acteur
 * during processing.
 *
 * @author Tim Boudreau
 */
final class CurrentChainImpl implements CurrentChain {

    private final Chain<Acteur, ? extends Chain<Acteur, ?>> chain;

    CurrentChainImpl(Chain<Acteur, ? extends Chain<Acteur, ?>> chain) {
        this.chain = chain;
    }

    @Override
    public CurrentChain add(Acteur obj) {
        chain.add(obj);
        return this;
    }

    @Override
    public CurrentChain add(Class<? extends Acteur> type) {
        chain.add(type);
        return this;
    }

    @Override
    public CurrentChain insert(Acteur obj) {
        chain.insert(obj);
        return this;
    }

    @Override
    public CurrentChain insert(Class<? extends Acteur> obj) {
        chain.insert(obj);
        return this;
    }
}
