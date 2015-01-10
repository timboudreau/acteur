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

package com.mastfrog.acteur;

import com.google.inject.ProvisionException;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorRenderer;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import com.mastfrog.acteur.errors.ResponseException;
import com.mastfrog.giulius.Dependencies;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 *
 * @author Tim Boudreau
 */
final class ErrorActeur extends Acteur {

    ErrorActeur(Acteur errSource, HttpEvent evt, Page page, Throwable t, boolean tryErrResponse, boolean log) throws IOException {
        Throwable orig = t;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof ResponseException) {
            ResponseException rt = (ResponseException) t;
            setState(new RespondWith(new Err(rt.status(), rt.getMessage())));
            return;
        }
        if (tryErrResponse) {
            Dependencies deps = page.application.getDependencies();
            if (t instanceof ProvisionException && t.getCause() != null) {
                t = t.getCause();
            }
            if (t == null) {
                t = orig;
            }
            ExceptionEvaluatorRegistry reg = deps.getInstance(ExceptionEvaluatorRegistry.class);
            ErrorResponse resp = reg.evaluate(t, errSource, page, evt);
            if (log && resp instanceof Err && ((Err) resp).unhandled) {
                page.application.control().internalOnError(t);
            }
            if (resp != null) {
                ErrorRenderer ren = deps.getInstance(ErrorRenderer.class);
                ren.render(resp, response(), evt);
                setState(new RespondWith(resp.status()));
                return;
            }
        }
        StringBuilder sb = new StringBuilder("Page " + page + " (" + page.getClass().getName() + " threw " + t.getMessage() + '\n');
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            t.printStackTrace(new PrintStream(out));
            sb.append(new String(out.toByteArray()));
        } catch (IOException ioe) {
        }
        setState(new RespondWith(HttpResponseStatus.INTERNAL_SERVER_ERROR, sb.toString()));
    }

}
