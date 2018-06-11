/* 
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur.errors;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Information needed to generate an http response for an error.  The
 * <a href="Err.html">Err</a> class provides a good default implementation
 * over a map, with handlers for exceptions.
 *
 * @author Tim Boudreau
 */
public interface ErrorResponse {

    public HttpResponseStatus status();

    public Object message();

    public static class Simple<T> implements ErrorResponse {
        public final HttpResponseStatus status;
        public final T message;

        public Simple(HttpResponseStatus status, T message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public HttpResponseStatus status() {
            return status;
        }

        @Override
        public Object message() {
            return message;
        }

        public String toString() {
            return status + " " + message;
        }
    }
}
