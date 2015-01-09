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
package com.mastfrog.acteurbase.impl;

import com.mastfrog.acteurbase.AbstractActeur;
import com.mastfrog.acteurbase.ActeurResponseFactory;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
public class A2 extends AbstractActeur<Response, ResponseImpl> {

    protected A2() {
        super(INSTANCE);
    }

    protected A2 reject() {
        setState(new State<Response, ResponseImpl>(true));
        return this;
    }

    protected A2 next(Object... context) {
        if (context == null || context.length == 0) {
            setState(new State<Response, ResponseImpl>(false));
        } else {
            setState(new State<Response, ResponseImpl>(true));
        }
        return this;
    }

    protected A2 reply(HttpResponseStatus status, Object response) {
        response().setStatus(status);
        response().setMessage(response);
        setState(new State<Response, ResponseImpl>(response == null ? new Object[0] : new Object[]{response}));
        return this;
    }

    private static final RT INSTANCE = new RT();

    static class RT extends ActeurResponseFactory<Response, ResponseImpl> {

        @Override
        protected ResponseImpl create() {
            return new ResponseImpl();
        }

        @Override
        protected boolean isFinished(ResponseImpl obj) {
            return obj != null && obj.status() != null;
        }

        @Override
        protected boolean isModified(ResponseImpl obj) {
            return obj != null && obj.modified();
        }
    }
}
