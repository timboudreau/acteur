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
package com.mastfrog.acteur.util;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class ErrorHandlers {

    private final List<ErrorHandler> handlers;
    final FallbackErrorHandler fallback = new FallbackErrorHandler();

    @Inject
    ErrorHandlers(ErrorHandler.Registry registry) {
        this.handlers = registry.handlers;
    }

    public boolean onError(Throwable thrown) {
        Iterator<ErrorHandler> all = handlers.iterator();
        if (all.hasNext()) {
            RecursiveErrorHandler h = new RecursiveErrorHandler(all, fallback);
            h.accept(thrown);
            return h.wasHandled();
        } else {
            fallback.handle(thrown, null);
            return false;
        }
    }

    static final class RecursiveErrorHandler extends ErrorHandler implements Consumer<Throwable> {

        private final Iterator<ErrorHandler> handlers;
        private final ErrorHandler fallback;
        private boolean wasHandled = true;

        public RecursiveErrorHandler(Iterator<ErrorHandler> handlers, ErrorHandler fallback) {
            this.handlers = handlers;
            this.fallback = fallback;
        }

        boolean wasHandled() {
            return wasHandled;
        }

        @Override
        protected void handle(Throwable err, Consumer<Throwable> next) {
            if (handlers.hasNext()) {
                ErrorHandler h = handlers.next();
                h.handle(err, next);
            } else {
                wasHandled = false;
                fallback.handle(err, null);
            }
        }

        @Override
        public void accept(Throwable t) {
            handle(t, this);
        }

    }

    static final class FallbackErrorHandler extends ErrorHandler implements Consumer<Throwable> {

        FallbackErrorHandler() {
            super();
        }

        @Override
        protected void handle(Throwable err, Consumer<Throwable> next) {
            err.printStackTrace();
        }

        @Override
        public void accept(Throwable t) {
            handle(t, null);
        }
    }
}
