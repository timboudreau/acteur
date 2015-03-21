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
package com.mastfrog.acteur.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluator;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;

/**
/**
 * Simple bindings for MongoDB
 *
 * @author Tim Boudreau
 */
public class MongoModule extends AbstractModule implements MongoConfig<MongoModule> {

    private final GiuliusMongoModule giuliusModule;

    @Override
    protected void configure() {
        install(giuliusModule);
        bind(InvalidParamterExceptionEvaluator.class).asEagerSingleton();
    }

    public MongoModule() {
        giuliusModule = new GiuliusMongoModule();

    }

    public MongoModule(String databaseName) {
        giuliusModule = new GiuliusMongoModule(databaseName);
    }

    @Override
    public MongoModule addInitializer(Class<? extends MongoInitializer> type) {
        giuliusModule.addInitializer(type);
        return this;
    }

    @Override
    public MongoModule bindCollection(String bindingName) {
        giuliusModule.bindCollection(bindingName);
        return this;
    }

    @Override
    public MongoModule bindCollection(String bindingName, String collectionName) {
        giuliusModule.bindCollection(bindingName, collectionName);
        return this;
    }

    static class InvalidParamterExceptionEvaluator extends ExceptionEvaluator {

        @Inject
        InvalidParamterExceptionEvaluator(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, HttpEvent evt) {
            String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            return Err.badRequest(msg);
        }
    }
}
