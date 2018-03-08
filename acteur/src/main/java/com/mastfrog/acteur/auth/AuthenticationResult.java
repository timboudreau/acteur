/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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
package com.mastfrog.acteur.auth;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Result of asynchronous authentication, injectable into subsequent acteurs.
 *
 * @author Tim Boudreau
 */
public final class AuthenticationResult<T> {

    public final T info;
    public final HttpResponseStatus failureStatus;
    public final String failureMessage;
    public final String token;

    public AuthenticationResult(T info, String token) {
        this(info, null, null, token);
    }

    public AuthenticationResult(String failureMessage) {
        this(null, HttpResponseStatus.UNAUTHORIZED, failureMessage, null);
    }

    public AuthenticationResult(String failureMessage, String token) {
        this(null, HttpResponseStatus.UNAUTHORIZED, failureMessage, token);
    }

    public AuthenticationResult(HttpResponseStatus status, String failureMessage, String token) {
        this(null, status, failureMessage, token);
    }

    public AuthenticationResult(HttpResponseStatus status, String failureMessage) {
        this(null, status, failureMessage, null);
    }

    public boolean isAuthenticated() {
        return info != null && failureMessage == null;
    }

    public AuthenticationResult(T info, HttpResponseStatus failureStatus, String failureMessage, String token) {
        this.info = info;
        this.failureStatus = failureStatus;
        this.failureMessage = failureMessage;
        this.token = token;
    }

}
