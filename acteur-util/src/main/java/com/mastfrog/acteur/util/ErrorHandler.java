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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * An error handler, which can take over handling of thrown exceptions for the
 * application, or pass them on to the next handler by calling the passed
 * ErrorHandler.
 *
 * @author Tim Boudreau
 */
public abstract class ErrorHandler {

    @SuppressWarnings("LeakingThisInConstructor")
    @Inject
    protected ErrorHandler(Registry handlers) {
        handlers.register(this);
    }

    ErrorHandler() {

    }

    /**
     * Handle an exception - could be logging it, suppressing it, or whatever.
     * This method <i><b>must</b></i> call the next handler if it does not
     * handle the exceptions - an implementation that does nothing will simply
     * swallow all errors.
     *
     * @param err The error
     * @param next The next error
     */
    protected abstract void handle(Throwable err, Consumer<Throwable> next);

    protected int ordinal() {
        Ordinal ord = getClass().getAnnotation(Ordinal.class);
        return ord == null ? Integer.MIN_VALUE : ord.value();
    }

    @Singleton
    public static final class Registry {

        final List<ErrorHandler> handlers = new ArrayList<>(3);

        public void register(ErrorHandler handler) {
            handlers.add(handler);
            Collections.sort(handlers, (ErrorHandler o1, ErrorHandler o2) -> {
                int i1 = o1.ordinal();
                int i2 = o2.ordinal();
                return i1 == i2 ? 0 : i1 > i2 ? 1 : -1;
            });
        }
    }

    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    protected @interface Ordinal {

        int value() default Integer.MIN_VALUE;
    }

    public static abstract class Typed<T extends Throwable> extends ErrorHandler {

        private final Class<T> type;
        private final boolean checkCause;

        protected Typed(Registry handlers, Class<T> type, boolean checkCause) {
            super(handlers);
            this.type = type;
            this.checkCause = checkCause;
        }

        @Override
        protected final void handle(Throwable err, Consumer<Throwable> next) {
            if (type.isInstance(err)) {
                if (!doHandle(type.cast(err))) {
                    next.accept(err);
                }
            }
            if (checkCause) {
                Throwable t = err.getCause();
                while (t != null) {
                    if (type.isInstance(t)) {
                        if (doHandle(type.cast(t))) {
                            return;
                        }
                    }
                    t = t.getCause();
                }
            }
        }

        protected abstract boolean doHandle(T err);

    }
}
