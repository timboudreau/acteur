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

import com.google.inject.ImplementedBy;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Decorates cors responses.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(CORSResponseDecoratorImpl.class)
public interface CORSResponseDecorator {

    /**
     * Adds headers to an acteur response.
     *
     * @param evt The event
     * @param resp The response headers should be added to.
     */
    void decorateCorsPreflight(HttpEvent evt, Response resp, Page page);

    /**
     * Adds headers to application-level responses such as Not Found and errors
     *
     * @param response
     */
    default void decorateApplicationResponse(HttpResponse response) {
        // do nothing
    }

    default void decorateApplicationResponse(HttpResponse response, Page page) {
        decorateApplicationResponse(response);
    }
}
