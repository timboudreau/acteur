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
package com.mastfrog.acteur;

import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur.BaseState;
import com.mastfrog.acteur.errors.ResponseException;
import static com.mastfrog.acteur.server.ServerModule.DELAY_EXECUTOR;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.AbstractChain;
import com.mastfrog.acteurbase.ChainCallback;
import com.mastfrog.acteurbase.ChainRunner;
import com.mastfrog.acteurbase.ChainsRunner;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
class PagesImpl2 {

    private final Application application;

    private final ScheduledExecutorService scheduler;

    private final Settings settings;

    private final ChainsRunner ch;

    private final InstantiatingIterators iters;

    private final boolean debug;

    @Inject
    PagesImpl2(Application application, Settings settings, @Named(DELAY_EXECUTOR) ScheduledExecutorService scheduler) {
        this.application = application;
        this.settings = settings;
        this.scheduler = scheduler;
        debug = settings.getBoolean("acteur.debug", false);
        iters = new InstantiatingIterators(application.getDependencies());
//        AbstractChain<Page> pages = new AbstractChain(application.getDependencies(), Page.class);
        ChainRunner chr = new ChainRunner(application.getWorkerThreadPool(), application.getRequestScope());
        ch = new ChainsRunner(application.getWorkerThreadPool(), application.getRequestScope(), chr);
    }

    public CountDownLatch onEvent(RequestID id, Event<?> event, Channel channel) {
        CountDownLatch latch = new CountDownLatch(1);

        System.out.println("PI2 EVENT " + id + " - " + event);

        Closables clos = new Closables(channel, application.control());
        ToChain chainConverter = new ToChain(id, event, clos);
        Iterable<PageChain> pagesIterable
                = CollectionUtils.toIterable(CollectionUtils.convertedIterator(chainConverter, application.iterator()));

        CB callback = new CB(id, event, latch, channel);

        System.out.println("Submit " + pagesIterable + " to " + ch);
        CancelOnChannelClose closer = new CancelOnChannelClose();
        channel.closeFuture().addListener(closer);
        ch.run(pagesIterable, callback, closer.cancelled, id, event, clos);

        return latch;
    }

    static class CancelOnChannelClose implements ChannelFutureListener {

        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            cancelled.set(true);
        }

    }

    class CB implements ChainCallback<Acteur, BaseState, PageChain, Response, ResponseImpl>, ResponseSender {

        private final Event<?> event;

        private final CountDownLatch latch;
        private final Channel channel;
        private final RequestID id;

        CB(RequestID id, Event<?> event, CountDownLatch latch, Channel channel) {
            this.event = event;
            this.latch = latch;
            this.channel = channel;
            this.id = id;
        }

        @Override
        public void onBeforeRunOne(PageChain chain) {
            System.out.println("ON BEFORE RUN ONE " + chain.page);
            Page.set(chain.page);
        }

        @Override
        public void onAfterRunOne(PageChain chain, Acteur acteur) {
            System.out.println("ON AFTER ONE " + chain.page + " - " + acteur);
            if (Page.get() == chain.page) {
                Page.clear();
            }
        }

        @Override
        public void onDone(BaseState state, List<ResponseImpl> responses) {
            ResponseImpl finalR = new ResponseImpl();
            for (ResponseImpl r : responses) {
                finalR.merge(r);
            }
            receive(state.getActeur(), state, finalR);
            latch.countDown();
        }

        @Override
        public void onRejected(BaseState state) {
            throw new UnsupportedOperationException("Should not ever be called from ChainsRunner");
        }

        @Override
        public void onNoResponse() {
            application.send404(id, event, channel);
            latch.countDown();
        }

        @Override
        public void onFailure(Throwable ex) {
            System.out.println("ON FAILURE " + ex.getClass().getName());
//            application.internalOnError(ex);
            uncaughtException(Thread.currentThread(), ex);
            latch.countDown();
        }

        @Override
        public void receive(final Acteur acteur, final BaseState state, final ResponseImpl response) {
            System.out.println("RECEIVE " + acteur + " with " + state);
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
                    uncaughtException(Thread.currentThread(), e);
                }
            } else {
                onNoResponse();
            }
        }

        @Override
        public void uncaughtException(Thread thread, Throwable thrwbl) {
            System.out.println("UCAUGHT EX ON " + thread.getName());
            try {
                if (!(thrwbl instanceof ResponseException)) {
                    thrwbl.printStackTrace();
                    application.internalOnError(thrwbl);
                }
                if (thrwbl instanceof ThreadDeath || thrwbl instanceof OutOfMemoryError) {
                    Exceptions.chuck(thrwbl);
                }
                System.out.println("Send internal on error");
                application.internalOnError(thrwbl);

                System.out.println("Create error acteur");

                ErrorPage pg = application.getDependencies().getInstance(ErrorPage.class);
                pg.setApplication(application);
                try (AutoCloseable ac = Page.set(pg)) {
                    try (AutoCloseable ac2 = application.getRequestScope().enter(id, event, channel)) {
                        Acteur err = Acteur.error(null, pg, thrwbl,
                                application.getDependencies().getInstance(HttpEvent.class), true);

                        System.out.println("SEND ERR TO RESPONSE");
                        // XXX this might recurse badly
                        receive(err, err.getState(), err.getResponse());
                    }
                }
            } catch (Throwable ex) {
                System.err.println("WHOA!");
                ex.printStackTrace();
            }
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

    static class ErrorPage extends Page {

    }

    class ToChain implements Converter<PageChain, Page> {

        private final RequestID id;
        private final Event<?> event;
        private final Closables clos;

        private ToChain(RequestID id, Event<?> event, Closables clos) {
            this.id = id;
            this.event = event;
            this.clos = clos;
        }

        @Override
        public PageChain convert(Page r) {
            r.setApplication(application);
            if (event instanceof HttpEvent) {
                Path pth = ((HttpEvent) event).getPath();
                Thread.currentThread().setName(pth + " for " + r.getClass().getName());
            }
            System.out.println("create page chain for " + r);
            PageChain result = new PageChain(application.getDependencies(), Acteur.class, r, r, id, event, clos);
            return result;
        }

        @Override
        public Page unconvert(PageChain t) {
            return t.page;
        }
    }

    static class PageChain extends AbstractChain<Acteur> {

        private final Page page;
        private final Object[] ctx;

        public PageChain(Dependencies deps, Class<? super Acteur> type, Page page, Object... ctx) {
            super(deps, type, page.acteurs());
            this.page = page;
            this.ctx = ctx;
        }

        @Override
        public Object[] getContextContribution() {
            return ctx;
        }

        public String toString() {
            return "Chain for " + page;
        }
    }
}
