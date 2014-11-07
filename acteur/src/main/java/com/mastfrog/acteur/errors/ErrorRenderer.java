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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.errors.ErrorRenderer.DefaultErrorRenderer;
import java.io.IOException;
import javax.inject.Inject;

/**
 * Renders error messages into responses
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultErrorRenderer.class)
public abstract class ErrorRenderer {

    public void render(ErrorResponse resp, Response into, HttpEvent evt) throws IOException {
        String s = render(resp, evt);
        into.setResponseCode(resp.status());
        if (s != null) {
            into.setMessage(s);
        }
    }

    public abstract String render(ErrorResponse resp, HttpEvent evt) throws IOException;

    static class DefaultErrorRenderer extends ErrorRenderer {

        private final ObjectMapper mapper;

        @Inject
        DefaultErrorRenderer(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void render(ErrorResponse resp, Response into, HttpEvent evt) throws JsonProcessingException {
            into.setResponseCode(resp.status());
            into.setMessage(mapper.writeValueAsString(resp.message()));
        }

        @Override
        public String render(ErrorResponse resp, HttpEvent evt) throws IOException {
            return mapper.writeValueAsString(resp.message());
        }
    }
}
