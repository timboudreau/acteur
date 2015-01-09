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
package com.mastfrog.acteurbase;

import com.mastfrog.guicy.scope.ReentrantScope;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author Tim Boudreau
 */
public class ChainsRunner {

    private final ExecutorService svc;
    private final ReentrantScope scope;
    private final ChainRunner chainRunner;

    public ChainsRunner(ExecutorService svc, ReentrantScope scope, ChainRunner chainRunner) {
        this.svc = svc;
        this.scope = scope;
        this.chainRunner = chainRunner;
    }

    public <P extends Chain<? extends AbstractActeur<T, R>>, T, R extends T> void run(Iterable<P> chains, ChainCallback<P, T, R> onDone, Object... initialContext) {
        svc.submit(scope.wrap(new OneChainRun<>(svc, onDone, chains.iterator()), initialContext));
    }

    class OneChainRun<P extends Chain<? extends AbstractActeur<T, R>>, T, R extends T> implements ChainCallback<P, T, R>, Callable<Void> {

        private final ExecutorService svc;

        private final ChainCallback<P, T, R> onDone;
        private final Iterator<P> iter;

        public OneChainRun(ExecutorService svc, ChainCallback<P, T, R> onDone, Iterator<P> iter) {
            this.svc = svc;
            this.onDone = onDone;
            this.iter = iter;
        }

        @Override
        public void onNoResponse() {
            if (!iter.hasNext()) {
                this.onDone.onNoResponse();
            } else {
                svc.submit(this);
            }
        }

        @Override
        public Void call() throws Exception {
            if (!iter.hasNext()) {
                this.onDone.onNoResponse();
            } else {
                chainRunner.run(iter.next(), this);
            }
            return null;
        }

        @Override
        public void onDone(AbstractActeur.State<T, R> state, List<R> responses) {
            this.onDone.onDone(state, responses);
        }

        @Override
        public void onFailure(Throwable ex) {
            this.onDone.onFailure(ex);
        }

        @Override
        public void onRejected(AbstractActeur.State<T, R> state) {
            onNoResponse();
        }

        @Override
        public void onBeforeRunOne(P chain) {
        }

        @Override
        public void onAfterRunOne(P chain, AbstractActeur<T, R> a) {
        }
    }
}
