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

import com.google.inject.ProvisionException;
import com.mastfrog.acteurbase.AbstractActeur.State;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.util.Checks;
import com.mastfrog.util.thread.QuietAutoCloseable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

/**
 * Runs a chain of AbstractActeurs, invoking the callback when the chain has
 * been exhausted.
 *
 * @author Tim Boudreau
 */
public final class ChainRunner {

    private final ExecutorService svc;
    private final ReentrantScope scope;

    @Inject
    public ChainRunner(ExecutorService svc, ReentrantScope scope) {
        Checks.notNull("svc", svc);
        Checks.notNull("scope", scope);
        this.svc = svc;
        this.scope = scope;
    }

    public <X extends P, P extends Chain<? extends AbstractActeur<T, R>>, T, R extends T> void run(X chain, ChainCallback<P, T, R> onDone) {
        CC<P, T, R> cc = new CC<>(svc, scope, chain, onDone);
        // Enter the scope, with the Chain (so it can be dynamically added to)
        // and the deferral, which can be used to pause the chain
        try (QuietAutoCloseable ac = scope.enter(chain, cc.deferral)) {
            // Wrap the callable so whenn it is invoked, we will be in the
            // scope with the same contents as before
            svc.submit(scope.wrap(cc));
        }
    }

    static class CC<P extends Chain<? extends AbstractActeur<T, R>>, T, R extends T> implements Callable<Object[]>, Resumer {

        private final ExecutorService svc;

        private final ReentrantScope scope;
        private final Iterator<? extends AbstractActeur<T, R>> iter;
        private Object[] state = new Object[0];
        private final List<R> responses = new LinkedList<>();
        private final ChainCallback<P, T, R> onDone;
        private final AtomicBoolean deferred = new AtomicBoolean();
        private Callable<?> next;
        final Deferral deferral = new DeferralImpl();
        private final P chain;

        public CC(ExecutorService svc, ReentrantScope scope, P chain, ChainCallback<P, T, R> onDone) {
            this.svc = svc;
            this.scope = scope;
            this.iter = chain.iterator();
            this.chain = chain;
            this.onDone = onDone;
        }

        class DeferralImpl implements Deferral {

            @Override
            public Resumer defer() {
                if (deferred.compareAndSet(false, true)) {
                    return CC.this;
                } else {
                    throw new IllegalStateException("Already deferred");
                }
            }
        }

        private void addToContext(State state) {
            if (state.context() != null && state.context().length > 0) {
                synchronized (this) {
                    Object[] nue = new Object[this.state.length + state.context().length];
                    System.arraycopy(this.state, 0, nue, 0, this.state.length);
                    System.arraycopy(state.context(), 0, nue, this.state.length, state.context().length);
                    this.state = nue;
                }
            }
        }

        @Override
        public Object[] call() throws Exception {
            AutoCloseable ac = null;
            // Optimization - only reenter the scope if we have some state
            // from previous acteurs to incorporate into it
            synchronized (this) {
                if (this.state.length > 0) {
                    ac = scope.enter(this.state);
                }
            }
            State<T, R> newState;
            try {
                AbstractActeur<T,R> a2 = null;
                try {
                    onDone.onBeforeRunOne(chain);
                    // Instantiate the next acteur, most likely causing its 
                    // constructor to set its state
                    a2 = iter.next();
                    // Get the state, which may compute the state if it is lazy
                    newState = a2.getState();
                } finally {
                    onDone.onAfterRunOne(chain, a2);
                }
                if (newState.isRejected()) {
                    onDone.onRejected(newState);
                    return null;
                }
                // Add any objects it provided into the scope for the next 
                // invocation
                addToContext(newState);
            } catch (Exception e) {
                Throwable t = e;
                if (e instanceof ProvisionException && e.getCause() != null) {
                    t = e.getCause();
                }
                onDone.onFailure(t);
                return null;
            } finally {
                if (ac != null) {
                    ac.close();
                }
            }
            // Get the response, which may be null if it was untouched by the
            // acteurs execution
            R resp = newState.response();
            if (resp != null) {
                // Add it into the set of response objects the OnDone will
                // coalesce
                synchronized (this) {
                    responses.add(resp);
                }
            }
            // See if we're done
            if (!newState.isFinished()) {
                // If no more Acteurs, tell the callback we give up
                if (!iter.hasNext()) {
                    onDone.onNoResponse();
                } else if (deferred.get()) {
                    // Store the next iteration with the current scope
                    // contents, so that when resumer.resume() is called
                    // we can go back to work
//                    try (QuietAutoCloseable qac = scope.enter(state)) {
                    next = scope.wrap(this);
//                    }
                } else {
                    // Re-wrap "this" in the current scope and tee it up
                    // to be run
                    svc.submit(scope.wrap(this));
                }
            } else {
                onDone.onDone(newState, responses);
            }
            return this.state;
        }

        @Override
        public void resume() {
            if (deferred.compareAndSet(true, false)) {
                Callable<?> next = this.next;
                svc.submit(next);
            } else {
                throw new IllegalStateException("Not deferred");
            }
        }
    }
}
