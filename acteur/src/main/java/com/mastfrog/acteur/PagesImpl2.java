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
import com.mastfrog.acteur.errors.ResponseException;
import static com.mastfrog.acteur.server.ServerModule.DELAY_EXECUTOR;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.AbstractActeur;
import com.mastfrog.acteurbase.AbstractChain;
import com.mastfrog.acteurbase.ChainCallback;
import com.mastfrog.acteurbase.ChainRunner;
import com.mastfrog.acteurbase.ChainsRunner;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
public class PagesImpl2 implements Pages {

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

    @Override
    public CountDownLatch onEvent(RequestID id, Event<?> event, Channel channel) {
        CountDownLatch latch = new CountDownLatch(1);

        ToChain chainConverter = new ToChain();
        Iterable<PageChain> pagesIterable
                = CollectionUtils.toIterable(CollectionUtils.convertedIterator(chainConverter, application.iterator()));

        CB callback = new CB(id, event, latch, channel);

        ch.run(pagesIterable, callback, id, event, new Closables(channel, application.control()));

        return latch;
    }

    class CB implements ChainCallback<PageChain, Response, ResponseImpl>, ResponseSender {

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
            Page.set(chain.page);
        }

        @Override
        public void onAfterRunOne(PageChain chain, AbstractActeur<Response, ResponseImpl> acteur) {
            if (Page.get() == chain.page) {
                Page.clear();
            }
        }

        @Override
        public void onDone(AbstractActeur.State<Response, ResponseImpl> state, List<ResponseImpl> responses) {
            ResponseImpl finalR = new ResponseImpl();
            for (ResponseImpl r : responses) {
                finalR.merge(r);
            }
            latch.countDown();
        }

        @Override
        public void onRejected(AbstractActeur.State<Response, ResponseImpl> state) {
            throw new UnsupportedOperationException("Should not ever be called from ChainsRunner");
        }

        @Override
        public void onNoResponse() {
            application.send404(id, event, channel);
            latch.countDown();
        }

        @Override
        public void onFailure(Throwable ex) {
            ex.printStackTrace();
            application.internalOnError(ex);
            latch.countDown();
        }

        @Override
        public void receive(final Acteur acteur, final AbstractActeur.State<Response, ResponseImpl> state, final ResponseImpl response) {
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
                            channel.closeFuture().addListener(new PagesImpl.CancelOnClose(s));
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

    class ToChain implements Converter<PageChain, Page> {

        @Override
        public PageChain convert(Page r) {
            PageChain result = new PageChain(application.getDependencies(), Acteur.class, r);
            return result;
        }

        @Override
        public Page unconvert(PageChain t) {
            return t.page;
        }
    }

    static class PageChain extends AbstractChain<Acteur> {

        private final Page page;

        public PageChain(Dependencies deps, Class<? super Acteur> type, Page page) {
            super(deps, type, page.acteurs());
            this.page = page;
        }
    }
}
