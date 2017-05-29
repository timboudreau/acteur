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
import com.mastfrog.acteurbase.Deferral.DeferredCode;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.util.Checks;
import com.mastfrog.util.thread.QuietAutoCloseable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

/**
 * Runs a chain of AbstractActeurs, invoking the callback when the chain has
 * been exhausted.
 * <p>
 * This class involves a sadly complex generic signature, which is necessary in
 * order to preserve the types in question. If you are creating an acteur-based
 * framework, it is best to write specific, parameterized subclasses of this,
 * AbstractActeur, State and StateCallback, so that it is clear what someone is
 * supposed to pass.
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

    /**
     * Run one {@link Chain} of {@link AbstractActeur}s, constructing each and
     * retrieving its state, and calling the passed callback with the results.
     *
     * @param <A> The AbstractActeur subtype
     * @param <S> The State subtype
     * @param <P> The Chain subtype
     * @param <T> The public type the AbstractActeur subtype is parameterized on
     * @param <R> The implementation type the AbstractActeur subtype is
     * parameterized on
     * @param chain The chain
     * @param onDone The callback
     * @param cancelled Set this to true if execution should be silently
     * cancelled
     */
    public <A extends AbstractActeur<T, R, S>, S extends ActeurState<T, R>, P extends Chain<? extends A>, T, R extends T>
            void submit(P chain, ChainCallback<A, S, P, T, R> onDone, AtomicBoolean cancelled) {
        ActeurInvoker<A, S, P, T, R> cc = new ActeurInvoker<>(svc, scope, chain, onDone, cancelled);
        // Enter the scope, with the Chain (so it can be dynamically added to)
        // and the deferral, which can be used to pause the chain
        try (QuietAutoCloseable ac = scope.enter(chain, cc.deferral)) {
            // Wrap the callable so whenn it is invoked, we will be in the
            // scope with the same contents as before
            svc.submit(scope.wrap(cc));
        }
    }

    private static class ActeurInvoker<A extends AbstractActeur<T, R, S>, S extends ActeurState<T, R>, P extends Chain<? extends A>, T, R extends T> implements Callable<Void>, Resumer {

        private final ExecutorService svc;

        private final ReentrantScope scope;
        private final Iterator<? extends A> iter;
        private Object[] state = new Object[0];
        private final List<R> responses = new LinkedList<>();
        private final ChainCallback<A, S, P, T, R> onDone;
        private final AtomicBoolean deferred = new AtomicBoolean();
        private Callable<?> next;
        final Deferral deferral = new DeferralImpl();
        private final P chain;
        private final AtomicBoolean cancelled;
        private final AtomicReference<DeferredCode> deferredCode = new AtomicReference<>();

        public ActeurInvoker(ExecutorService svc, ReentrantScope scope, P chain, ChainCallback<A, S, P, T, R> onDone, AtomicBoolean cancelled) {
            this.svc = svc;
            this.scope = scope;
            this.iter = chain.iterator();
            this.chain = chain;
            this.onDone = onDone;
            this.cancelled = cancelled;
        }

        class DeferralImpl implements Deferral {

            @Override
            public Resumer defer() {
                if (deferred.compareAndSet(false, true)) {
                    return ActeurInvoker.this;
                } else {
                    throw new IllegalStateException("Already deferred");
                }
            }

            @Override
            public Resumer defer(DeferredCode code) {
                if (deferred.compareAndSet(false, true)) {
                    deferredCode.set(code);
                    return ActeurInvoker.this;
                } else {
                    throw new IllegalStateException("Already deferred");
                }
            }
        }

        private void addToContext(ActeurState state) {
            synchronized (this) {
                addToContext(state.context());
            }
        }

        private synchronized void addToContext(Object[] ctx) {
            if (ctx != null && ctx.length > 0) {
                Object[] nue = new Object[this.state.length + ctx.length];
                System.arraycopy(this.state, 0, nue, 0, this.state.length);
                System.arraycopy(ctx, 0, nue, this.state.length, ctx.length);
                this.state = nue;
            }
        }

        @Override
        public Void call() throws Exception {
            if (cancelled.get()) {
                return null;
            }
            try (AutoCloseable ctx = scope.enter(chain.getContextContribution())) {
                AutoCloseable ac = null;
                // Optimization - only reenter the scope if we have some state
                // from previous acteurs to incorporate into it
                synchronized (this) {
                    if (this.state.length > 0) {
                        ac = scope.enter(this.state);
                    }
                }
                S newState;
                try {
                    A a2 = null;
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
                } catch (Exception | Error e) {
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
                if (cancelled.get()) {
                    return null;
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
                        Deferral.DeferredCode code = deferredCode.getAndSet(null);
                        next = scope.wrap(this);
                        if (code != null) {
                            code.run(this);
                        }
                    } else if (!cancelled.get()) {
                        svc.submit(scope.wrap(this));
                    }
                } else {
                    onDone.onDone(newState, responses);
                }
                return null;
            } catch (Exception | Error e) {
                e.printStackTrace();
                onDone.onFailure(e);
                return null;
            }
        }

        @Override
        public void resume(Object... addToContext) {
            if (cancelled.get()) {
                return;
            }
            if (deferred.compareAndSet(true, false)) {
                addToContext(addToContext);
                Callable<?> next = this.next;
                svc.submit(next);
            } else {
                throw new IllegalStateException("Not deferred");
            }
        }
    }
}
