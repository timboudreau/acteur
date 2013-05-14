/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur;

import com.mastfrog.acteur.util.Headers;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.treadmill.Treadmill;
import com.mastfrog.util.Checks;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Tim Boudreau
 */
public class ActeursImpl implements Acteurs {

    private final ReentrantScope scope;
    private final ExecutorService exe;
    private final Page page;
    private final Settings settings;
    private static boolean debug;

    @Inject
    ActeursImpl(@Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService exe, ReentrantScope scope, Page page, Settings settings) {
        Checks.notNull("page", page);
        Checks.notNull("exe", exe);
        Checks.notNull("scope", scope);
        this.scope = scope;
        this.exe = exe;
        this.page = page;
        // perform sanity checks if assertions are on
        assert check(page.getActeurs());
        this.settings = settings;
        debug = settings.getBoolean("acteur.debug", true);
    }

    private boolean check(List<Object> actionsOrTypes) {
        // Make sure nobody played nasty tricks with raw types and generics
        for (Object object : actionsOrTypes) {
            if (object instanceof Class<?>) {
                Class<?> clazz = (Class<?>) object;
                assert Acteur.class.isAssignableFrom(clazz) : "Not an Acteur: " + clazz;
            } else {
                assert object instanceof Acteur : "Not an Acteur: " + object;
            }
        }
        return true;
    }

    @Override
    public void onEvent(Event event, ResponseSender receiver) {
        Checks.notNull("receiver", receiver);
        Checks.notNull("event", event);
        // Create a new empty response
        ResponseImpl response = new ResponseImpl();
        // A holder for the last state
        final AtomicReference<State> lastState = new AtomicReference<>();
        // A runnable which will be called if the request completes normally,
        // to send the headers and start sending the body (if any)
        FinishRequest finish = new FinishRequest(lastState, response, receiver, settings);

        Iterator<Acteur> acteurs = page.iterator();
        // Convert Acteurs to Callable<Object[]> which return the state's context
        // for injection into the request scope for the next Acteur
        ActeurToCallable converter = new ActeurToCallable(page, response, lastState, acteurs);
        
        if (!acteurs.hasNext()) {
            throw new IllegalStateException("No acteurs at all from " + page);
        }
        // Create an iterator of callables over the iterator of Acteurs from the page
        Iterator<Callable<Object[]>> it = CollectionUtils.convertedIterator(converter, acteurs);
        // The nifty thing that will run each Callable in succession and 
        // inject its output into the next callable's scope
        Treadmill t = new Treadmill(exe, scope, it, receiver);
        // Launch the response
        t.start(finish, event, page);
    }

    @Override
    public Iterator<Acteur> iterator() {
        return page.iterator();
    }

    /**
     * Converter from Acteur to Callable which calls the Acteur's getState()
     * method and returns the result of getState().getContext() (the objects
     * this acteur is offering up for injection)
     */
    private static class ActeurToCallable implements Converter<Callable<Object[]>, Acteur> {

        private final Page page;
        private final ResponseImpl response;
        private final AtomicReference<State> lastState;
        private final Iterator<Acteur> acteurs;

        ActeurToCallable(Page page, ResponseImpl response, AtomicReference<State> lastState, Iterator<Acteur> acteurs) {
            this.page = page;
            this.response = response;
            this.lastState = lastState;
            this.acteurs = acteurs;
        }

        @Override
        public Callable<Object[]> convert(Acteur acteur) {
            Checks.notNull("Null acteur in iterator from " + page, acteur);
            Page.set(page);
//            System.out.println("Run acteur " + acteur + " for " + page);
            try {
                return new ActeurCallable(page, acteur, response, lastState, !acteurs.hasNext());
            } finally {
                Page.clear();
            }
        }

        @Override
        public Acteur unconvert(Callable<Object[]> t) {
            // Not ever actually called
            return ((ActeurCallable) t).acteur;
        }
    }

    private static class FinishRequest implements Runnable {

        private final AtomicReference<State> lastState;
        private final ResponseImpl response;
        private final ResponseSender receiver;
        private final boolean debug;

        public FinishRequest(AtomicReference<State> lastState, ResponseImpl response, ResponseSender receiver, Settings settings) {
            this.lastState = lastState;
            this.response = response;
            this.receiver = receiver;
            debug = settings.getBoolean("acteur.debug", true);
        }

        @Override
        public void run() {
            try {
                // Get the last known state
                State state = lastState.get();
                // Null means a broken Acteur implementation - bail out
                if (state == null) {
                    lastState.lazySet(null);
                    receiver.uncaughtException(Thread.currentThread(), new NullPointerException("Last state is null"));
                } else {
                    // Pass it into the receiver
                    try {
                        if (debug) {
                            response.add(Headers.stringHeader("X-Acteur"), state.getActeur().getClass().getName());
                            response.add(Headers.stringHeader("X-Page"), state.getLockedPage().getClass().getName());
                        }
                        try (AutoCloseable cl = Page.set(state.getLockedPage())) {
                            receiver.receive(state.getActeur(), state, response);
                        }
                    } catch (Exception ex) {
                        receiver.uncaughtException(Thread.currentThread(), ex);
                    }
                }
            } catch (Exception e) {
                receiver.uncaughtException(Thread.currentThread(), e);
            }
        }
    }

    private static final class ActeurCallable implements Callable<Object[]> {

        private final Page page;
        final Acteur acteur;
        private final ResponseImpl response;
        private final AtomicReference<State> lastState;
        private final boolean isLast;

        public ActeurCallable(Page page, Acteur acteur, ResponseImpl response, AtomicReference<State> lastState, boolean isLast) {
            this.page = page;
            this.acteur = acteur;
            this.response = response;
            this.lastState = lastState;
            this.isLast = isLast;
        }

        @Override
        public Object[] call() throws Exception {
//            if (debug) {
//                System.out.println("ACTEUR " + acteur);
//            }
            // Set the Page ThreadLocal, for things that will call Page.get()
            Page.set(page);
            try {
                // Get the state
                State state = acteur.getState();
                // Null is not permitted - broken Acteur implementation didn't
                // call setState() in its constructor or didn't override getState(),
                // or overrode it to return null
                if (state == null) {
                    NullPointerException npe = new NullPointerException(acteur + " returns null from getState(), which is not permitted");
                    if (acteur.creationStackTrace != null) {
                        npe.addSuppressed(acteur.creationStackTrace);
                    }
                    state = Acteur.error(page, npe).getState();
                    npe.printStackTrace();
                    throw npe;
                }
                // Set the atomic reference used by the finisher
                lastState.set(state);
//                if (debug) {
//                    System.out.println(acteur + " - " + state);
//                }
                // Merge in the response, in case some headers were added
                
                boolean done = state.isRejected();
//                if (!done || isLast) {
                  response.merge(acteur.getResponse());
//                }
                
                // If the state is rejected, return null - we're done processing
                // in this Treadmill
                return done ? null : state.getContext();
            } catch (ThreadDeath | OutOfMemoryError e) {
                throw e;
            } catch (Exception | Error e) {
//                page.getApplication().onError(e);
//                throw e;
                State state = Acteur.error(page, e).getState();
                lastState.set(state);
                response.merge(acteur.getResponse());
                page.getApplication().internalOnError(e);
                throw e;
            } finally {
                // Clear the current page ThreadLocal
                Page.clear();
            }
        }
    }
}
