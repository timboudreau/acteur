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

import com.google.common.collect.Lists;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.settings.Settings;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Registry of things which create error responses for exceptions.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class ExceptionEvaluatorRegistry {
    private final List<ExceptionEvaluator> evaluators = Lists.newCopyOnWriteArrayList();
    public static final String SETTINGS_KEY_DEFAULT_EXCEPTION_HANDLING = "default.exception.handling";
    @Inject
    ExceptionEvaluatorRegistry(Settings settings) {
        boolean prettyErrors = settings.getBoolean(SETTINGS_KEY_DEFAULT_EXCEPTION_HANDLING, true);
        if (prettyErrors) {
            new SimpleErrors(this);
        }
    }

    private static final class SimpleErrors extends ExceptionEvaluator {
        SimpleErrors(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        protected int ordinal() {
            return Integer.MIN_VALUE; // fallback position
        }
        
        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
            return new Err(t, true);
        }
    }

    void register(ExceptionEvaluator eval) {
        evaluators.add(eval);
        Collections.sort(evaluators);
    }

    public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
        for (ExceptionEvaluator e : evaluators) {
            ErrorResponse response = e.evaluate(t, acteur, page, evt);
            if (response != null) {
                return response;
            }
        }
        return null;
    }
}
