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

import com.mastfrog.giulius.scope.ReentrantScope;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs multiple chains, calling the callback when one has finished the work.
 *
 * @author Tim Boudreau
 */
public class ChainsRunner {

    private final ExecutorService svc;
    private final ReentrantScope scope;
    private final ChainRunner chainRunner;

    /**
     * Create a ChainsRunner
     *
     * @param svc The ExecutorService which will provide threads to run the work
     * @param scope The scope types AbstractActeurs pass between them will be
     * bound in
     * @param chainRunner A runner which will run individual chains
     */
    public ChainsRunner(ExecutorService svc, ReentrantScope scope, ChainRunner chainRunner) {
        this.svc = svc;
        this.scope = scope;
        this.chainRunner = chainRunner;
    }

    /**
     * Create a ChainsRunner
     *
     * @param svc The ExecutorService which will provide threads to run the work
     * @param scope The scope types AbstractActeurs pass between them will be
     * bound in
     */
    public ChainsRunner(ExecutorService svc, ReentrantScope scope) {
        this(svc, scope, new ChainRunner(svc, scope));
    }

    /**
     * Submit an {@link java.lang.Iterable} of {@link Chain} objects to be run
     * sequentially until one satisfies the work to be done.
     *
     * @param <A> The type of acteur.
     * @param <S> The type of state
     * @param <P> The type of chain
     * @param <T> The public type the {@link AbstractActeur} is parameterized on
     * @param <R> The implementation type the {@link AbstractActeur} is
     * parameterized on
     * @param chains An iterable collection of chains
     * @param onDone The callback to be notified when the work has completed, or
     * the chains have all been completed without success, or on failure
     * @param cancelled An atomic boolean which will be checked - if true, the
     * work will be aborted.
     * @param initialContext Any objects which should be available for injection
     * into the {@link AbstractActeur}s in the chain.
     */
    public <A extends AbstractActeur<T, R, S>, S extends ActeurState<T, R>, P extends Chain<? extends A, ?>, T, R extends T>
            void submit(Iterable<P> chains, ChainCallback<A, S, P, T, R> onDone, AtomicBoolean cancelled, Object... initialContext) {
        svc.submit(scope.wrap(new OneChainRun<>(svc, onDone, chains.iterator(), cancelled), initialContext));
    }

    class OneChainRun<A extends AbstractActeur<T, R, S>, S extends ActeurState<T, R>, P extends Chain<? extends A,?>, T, R extends T> implements ChainCallback<A, S, P, T, R>, Callable<Void> {

        private final ExecutorService svc;

        private final ChainCallback<A, S, P, T, R> onDone;
        private final Iterator<P> iter;
        private final AtomicBoolean cancelled;

        public OneChainRun(ExecutorService svc, ChainCallback<A, S, P, T, R> onDone, Iterator<P> iter, AtomicBoolean cancelled) {
            this.svc = svc;
            this.onDone = onDone;
            this.iter = iter;
            this.cancelled = cancelled;
        }

        @Override
        public void onNoResponse() {
            boolean hasNext = iter.hasNext();
            if (!hasNext) {
                this.onDone.onNoResponse();
            } else {
                svc.submit(this);
            }
        }

        @Override
        public Void call() throws Exception {
            if (cancelled.get()) {
                return null;
            }
            try {
                boolean hasNext = iter.hasNext();
                if (!hasNext) {
                    this.onDone.onNoResponse();
                } else {
                    P c = iter.next();
                    chainRunner.submit(c, this, cancelled);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void onDone(S state, List<R> responses) {
            this.onDone.onDone(state, responses);
        }

        @Override
        public void onFailure(Throwable ex) {
            this.onDone.onFailure(ex);
        }

        @Override
        public void onRejected(S state) {
            onNoResponse();
        }

        @Override
        public void onBeforeRunOne(P chain) {
            onDone.onBeforeRunOne(chain);
        }

        @Override
        public void onBeforeRunOne(P chain, List<R> responsesThusFar) {
            onDone.onBeforeRunOne(chain, responsesThusFar);
        }

        @Override
        public void onAfterRunOne(P chain, A a) {
            onDone.onAfterRunOne(chain, a);
        }
    }
}
