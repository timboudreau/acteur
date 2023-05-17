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

import io.netty.handler.codec.http.HttpResponse;

/**
 * Bind in guice to customize 404 not found responses, and failures that cannot
 * be handled by ErrorRenderer (i.e. something threw an exception in it) - i.e.
 * last-ditch handling for exceptions other than closing the connection.
 *
 * @author Tim Boudreau
 */
public interface FailureResponseFactory {

    /**
     * Allows a custom response for 404's.
     *
     * @param evt The event
     * @return A response
     */
    HttpResponse createNotFoundResponse(Event<?> evt);

    /**
     * Create a response when an unexpected exception has been thrown by an
     * acteur and not handled. This method can return null to use the built in
     * response generation. It's main purpose is if you want to fully disable
     * reporting about the kind of error in an internal server error for
     * security reasons. To just handle certain types of exceptions, subclass
     * ExceptionEvaluator register it, and intercept the types of Throwable
     * you're interested in. Note that if this method returns non-null,
     * ExceptionEvaluators will not be called, and this method has complete
     * control over the respone, including response code and headers.
     *
     *
     * @param thrown
     * @return A response or null to use the default handling
     */
    HttpResponse createFallbackResponse(Throwable thrown);
}
