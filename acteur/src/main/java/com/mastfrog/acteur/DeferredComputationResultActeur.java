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

import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import com.mastfrog.acteur.errors.ResponseException;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.mime.MimeType.JSON_UTF_8;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
public class DeferredComputationResultActeur extends Acteur {

    @Inject
    DeferredComputationResultActeur(HttpEvent evt, DeferredComputationResult res, ExceptionEvaluatorRegistry evals) throws Throwable {
        if (res.thrown != null) {
            add(CONTENT_TYPE, JSON_UTF_8);
            if (res.thrown instanceof ResponseException) {
                throw res.thrown;
            } else {
                ErrorResponse resp = evals.evaluate(res.thrown, this, Page.get(), evt);
                if (resp != null) {
                    if (resp.status() != INTERNAL_SERVER_ERROR) {
                        reply(resp.status(), resp.message());
                        return;
                    }
                }
                reply(Err.of(res.thrown));
            }
        } else {
            if (res.what == null) {
                if (res.onSuccess == null) {
                    ok();
                } else {
                    reply(res.onSuccess);
                }
            } else {
                if (res.onSuccess == null) {
                    ok(res.what);
                } else {
                    reply(res.onSuccess, res.what);
                }
            }
        }
    }
}
