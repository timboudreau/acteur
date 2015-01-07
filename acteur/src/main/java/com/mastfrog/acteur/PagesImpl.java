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

import com.google.inject.name.Named;
import com.mastfrog.acteur.errors.ResponseException;
import static com.mastfrog.acteur.server.ServerModule.DELAY_EXECUTOR;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 * Thing which takes an event and runs it against all of the pages of the
 * application until one responds; or sends a 404 if none does.
 *
 * @author Tim Boudreau
 */
final class PagesImpl implements Pages {

    private final Application application;

    private final ScheduledExecutorService scheduler;

    private final Settings settings;

    @Inject
    PagesImpl(Application application, Settings settings, @Named(DELAY_EXECUTOR) ScheduledExecutorService scheduler) {
        this.application = application;
        this.settings = settings;
        this.scheduler = scheduler;
    }

    /**
     * Returns a CountDownLatch which can be used in tests to wait until a
     * request has been fully processed. Note that the latch will be called when
     * the <i>headers have been flushed</i> - it is entirely possible that a
     * listener has been attached which will write a body after that. Add a
     * close listener to the channel to detect that the channel has been
     * completely written to.
     *
     * @param id The request unique id
     * @param event The event - the request itself
     * @param channel The channel
     * @return A latch which will count down when we're done
     */
    @Override
    public final CountDownLatch onEvent(final RequestID id, final Event<?> event, final Channel channel) {
        Iterator<Page> it = application.iterator();
        CountDownLatch latch = new CountDownLatch(1);
        Closables closables = new Closables(channel, application.control());
        PageRunner pageRunner = new PageRunner(application, settings, it, latch, id, event, channel, scheduler, closables);
        application.getWorkerThreadPool().submit(pageRunner);
        return latch;
    }

    private static final class PageRunner implements Callable<Void>, ResponseSender {

        private final Application application;
        private final Settings settings;
        private final Iterator<Page> pages;
        private final CountDownLatch latch;
        private final RequestID id;
        private final Event<?> event;
        private final Channel channel;
        private final ScheduledExecutorService scheduler;
        private final Closables close;
        private final boolean debug;

        public PageRunner(Application application, Settings settings, Iterator<Page> pages, CountDownLatch latch, RequestID id, Event<?> event, Channel channel, ScheduledExecutorService scheduler, Closables close) {
            this.application = application;
            this.settings = settings;
            this.pages = pages;
            this.latch = latch;
            this.id = id;
            this.event = event;
            this.channel = channel;
            this.scheduler = scheduler;
            this.close = close;
            debug = settings.getBoolean("acteur.debug", ActeursImpl.DEFAULT_DEBUG);
        }

        @Override
        public Void call() throws Exception {
            // See if any pages are left
            if (pages.hasNext()) {
                try (AutoCloseable a1 = application.getRequestScope().enter(close)) {
                    Page page = pages.next();
                    page.setApplication(application);
                    if (debug) {
                        System.out.println("PAGE " + page);
                    }
                    Page.set(page);
                    try (AutoCloseable ac = application.getRequestScope().enter(page)) {
                        // if so, grab its acteur runner
                        Acteurs a = new ActeursImpl(application.getWorkerThreadPool(), application.getRequestScope(), page, settings);
                        // forward the event.  receive() will be called with the final
                        // state, which will either send the response or re-submit this
                        // object to call the next page (if any)
                        a.onEvent(event, this);
                    }
                }
            } else {
                try {
                    // All done, we lose
                    application.send404(id, event, channel);
                } finally {
                    latch.countDown();
                }
            }
            return null;
        }

        @Override
        public void receive(final Acteur acteur, final State state, final ResponseImpl response) {
            if (response.isModified() && response.status != null) {
                // Actually send the response
                try {
                    // Abort if the client disconnected
                    if (!channel.isOpen()) {
                        latch.countDown();
                        return;
                    }

                    Charset charset = application.getDependencies().getInstance(Charset.class);

                    application.onBeforeSendResponse(response.status, event, response, state.getActeur(), state.getLockedPage());
                    // Create a netty response
                    HttpResponse httpResponse = response.toResponse(event, charset);
                    // Allow the application to add headers
                    httpResponse = application._decorateResponse(event, state.getLockedPage(), acteur, httpResponse);

                    // Allow the page to add headers
                    state.getLockedPage().decorateResponse(event, acteur, httpResponse);
                    // Abort if the client disconnected
                    if (!channel.isOpen()) {
                        latch.countDown();
                        return;
                    }
                    final HttpResponse resp = httpResponse;
                    try {

                        Callable<ChannelFuture> c = new ResponseTrigger(response, resp, state, acteur);
                        Duration delay = response.getDelay();
                        if (delay == null) {
                            if (debug) {
                                System.out.println("Response will be delayed for " + delay);
                            }
                            c.call();
                        } else {
                            final ScheduledFuture<?> s = scheduler.schedule(c, response.getDelay().getMillis(), TimeUnit.MILLISECONDS);
                            // Ensure the task is discarded if the connection is broken
                            channel.closeFuture().addListener(new CancelOnClose(s));
                        }
                    } finally {
                        latch.countDown();
                    }
                } catch (ThreadDeath | OutOfMemoryError ee) {
                    Exceptions.chuck(ee);
                } catch (Exception | Error e) {
                    if (!(e instanceof ResponseException)) {
                        e.printStackTrace();
                        application.internalOnError(e);
                    }

                    // Send an error message
                    Acteur err = Acteur.error(null, state.getLockedPage(), e,
                            application.getDependencies().getInstance(HttpEvent.class), true);
                    // XXX this might recurse badly
                    receive(err, err.getState(), err.getResponse());
                }
            } else // Do we have more pages?
            if (pages.hasNext()) {
                if (debug) {
                    System.out.println("Try next page");
                }
                try {
//                        call();
                    application.getWorkerThreadPool().submit(this);
                } catch (Exception ex) {
                    application.internalOnError(ex);
                }
            } else {
                // Otherwise, we're done - no page handled the request
                try {
                    if (debug) {
                        System.out.println("Send 404");
                    }
                    application.send404(id, event, channel);
                } finally {
                    latch.countDown();
                }
            }
        }

        @Override
        public void uncaughtException(Thread thread, Throwable thrwbl) {
            application.internalOnError(thrwbl);
        }

        private class ResponseTrigger implements Callable<ChannelFuture> {

            private final ResponseImpl response;
            private final HttpResponse resp;
            private final State state;
            private final Acteur acteur;

            public ResponseTrigger(ResponseImpl response, HttpResponse resp, State state, Acteur acteur) {
                this.response = response;
                this.resp = resp;
                this.state = state;
                this.acteur = acteur;
            }

            public ChannelFuture call() throws Exception {
                // Give the application a last chance to do something
                application.onBeforeRespond(id, event, response.getResponseCode());

                // Send the headers
                ChannelFuture fut = channel.writeAndFlush(resp);

                final Page pg = state.getLockedPage();
                fut = response.sendMessage(event, fut, resp);
                application.onAfterRespond(id, event, acteur, pg, state, HttpResponseStatus.OK, resp);
                return fut;
            }
        }
    }

    static class CancelOnClose implements ChannelFutureListener {

        private final ScheduledFuture future;

        public CancelOnClose(ScheduledFuture future) {
            this.future = future;
        }

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            future.cancel(true);
        }
    }

}
