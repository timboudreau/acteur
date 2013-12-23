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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Thing which takes an event and runs it against all of the pages of the
 * application until one responds; or sends a 404 if none does.
 *
 * @author Tim Boudreau
 */
final class PagesImpl implements Pages {

    private final Application application;
    private static boolean debug;
    
    private final ScheduledExecutorService scheduler;

    @Inject
    PagesImpl(Application application, Settings settings, @Named(ServerModule.WORKER_THREADS) ThreadFactory workerThreadFactory) {
        this.application = application;
        debug = settings.getBoolean("acteur.debug", true);
        scheduler = Executors.newSingleThreadScheduledExecutor(workerThreadFactory);
    }

    /**
     * Returns a CountDownLatch which can be used in tests to wait until a
     * request has been fully processed
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
        PageRunner pageRunner = new PageRunner(application, it, latch, id, event, channel, scheduler);
        application.getWorkerThreadPool().submit(pageRunner);
        return latch;
    }

    private static final class PageRunner implements Callable<Void>, ResponseSender {

        private final Application application;
        private final Iterator<Page> pages;
        private final CountDownLatch latch;
        private final RequestID id;
        private final Event<?> event;
        private final Channel channel;
        private final ScheduledExecutorService scheduler;

        public PageRunner(Application application, Iterator<Page> pages, CountDownLatch latch, RequestID id, Event<?> event, Channel channel, ScheduledExecutorService scheduler) {
            this.application = application;
            this.pages = pages;
            this.latch = latch;
            this.id = id;
            this.event = event;
            this.channel = channel;
            this.scheduler = scheduler;
        }

        @Override
        public Void call() throws Exception {
            // See if any pages are left
            if (pages.hasNext()) {
                try (AutoCloseable a1 = application.getRequestScope().enter(event, id, channel)) {
                    Page page = pages.next();
                    page.setApplication(application);
    //                if (debug) {
    //                    System.out.println("PAGE " + page);
    //                }
                    Page.set(page);
                    try (AutoCloseable ac = application.getRequestScope().enter(page)) {
                        // if so, grab its acteur runner
                        Acteurs a = page.getActeurs(application.getWorkerThreadPool(), application.getRequestScope());
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
                    // Create a netty response
                    HttpResponse httpResponse = response.toResponse(event, charset);
                    // Allow the application to add headers
                    httpResponse = application.decorateResponse(event, state.getLockedPage(), acteur, httpResponse);

                    // Allow the page to add headers
                    state.getLockedPage().decorateResponse(event, acteur, httpResponse);
                    // Abort if the client disconnected
                    if (!channel.isOpen()) {
                        latch.countDown();
                        return;
                    }
                    final HttpResponse resp = httpResponse;
                    try {
                        
                        Callable<ChannelFuture> c = new Callable<ChannelFuture>() {
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
                        };
                        if (response.getDelay() == null) {
                            c.call();
                        } else {
                            scheduler.schedule(c, response.getDelay().getMillis(), TimeUnit.MILLISECONDS);
                        }
//                        // Ensure we don't write to the channel before the
//                        // headers are sent
//                        fut.addListener(new ChannelFutureListener() {
//                            volatile boolean first = true;
//                            @Override
//                            public void operationComplete(ChannelFuture fut) throws Exception {
//                                if (first) {
//                                    fut = fut.channel().flush();
//                                    first = false;
//                                    fut.addListener(this);
//                                    // Send the response
//                                } else {
//                                }
//                            }
//                        });
                    } finally {
                        latch.countDown();
                    }
                } catch (ThreadDeath | OutOfMemoryError ee) {
                    Exceptions.chuck(ee);
                } catch (Exception | Error e) {
                    e.printStackTrace();
                    application.internalOnError(e);
                    // Send an error message
                    Acteur err = Acteur.error(state.getLockedPage(), e);
                    // XXX this might recurse badly
                    receive(err, err.getState(), err.getResponse());
                }
            } else {
                // Do we have more pages?
                if (pages.hasNext()) {
//                    if (debug) {
//                        System.out.println("Try next page");
//                    }
                    try {
                        call();
                    } catch (Exception ex) {
                        application.internalOnError(ex);
                    }
                } else {
                    // Otherwise, we're done - no page handled the request
                    try {
//                        if (debug) {
//                            System.out.println("Send 404");
//                        }
                        application.send404(id, event, channel);
                    } finally {
                        latch.countDown();
                    }
                }
            }
        }

        @Override
        public void uncaughtException(Thread thread, Throwable thrwbl) {
            application.internalOnError(thrwbl);
        }
    }
}
