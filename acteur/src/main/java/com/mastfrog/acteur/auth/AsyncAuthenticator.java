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

import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.util.function.EnhCompletionStage;

/**
 * Asynchronous authenticator.
 *
 * @author Tim Boudreau
 */
public interface AsyncAuthenticator<T> {

    /**
     * Validate an incoming authenticated request. This is called synchronously
     * before attempting asynchronous authentication, in order to reject
     * requests that are obviously invalid.
     *
     * @param evt The event
     * @param token The token (the substring of the header after 'bearer ').
     * @return An error string if validation fails
     */
    default String validate(HttpEvent evt, String token) {
        return null;
    }

    /**
     * Authenticate a request, returning a completion stage which will resume
     * the acteur chain when completed.
     *
     * @param rid The request id
     * @param evt The event
     * @param token The token from the authorization header
     * @return A completable future
     */
    EnhCompletionStage<AuthenticationResult<T>> authenticate(RequestID rid, HttpEvent evt, String token);

    /**
     * The type of the user object that will be inside the authentication result
     *
     * @return A type
     */
    Class<T> type();

}
