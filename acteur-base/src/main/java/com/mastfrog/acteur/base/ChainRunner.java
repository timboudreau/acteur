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

package com.mastfrog.acteur.base;

import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.treadmill.Treadmill;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author Tim Boudreau
 */
public class ChainRunner<T> {
    private final Iterable<T> chain;
    private final ExecutorService svc;
    private final ReentrantScope scope;
    private final Thread.UncaughtExceptionHandler handler;
    private final Converter<Callable<Object[]>, T> converter;

    public ChainRunner(Iterable<T> chain, ExecutorService svc, ReentrantScope scope, Thread.UncaughtExceptionHandler handler, Converter<Callable<Object[]>, T> converter) {
        this.chain = chain;
        this.svc = svc;
        this.scope = scope;
        this.handler = handler;
        this.converter = converter;
    }
    
    public void run(Runnable onDone, Object... initialContents) {
        Iterator<Callable<Object[]>> it = CollectionUtils.convertedIterator(converter, chain.iterator());
        Treadmill treadmill = new Treadmill(svc, scope, it, handler);
        treadmill.start(onDone, initialContents);
    }
}
