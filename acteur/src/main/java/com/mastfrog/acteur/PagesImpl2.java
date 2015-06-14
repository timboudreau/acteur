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

import com.google.common.net.MediaType;
import com.google.inject.name.Named;
import com.mastfrog.acteur.errors.ResponseException;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.server.ServerModule.DELAY_EXECUTOR;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.ArrayChain;
import com.mastfrog.acteurbase.ChainCallback;
import com.mastfrog.acteurbase.ChainRunner;
import com.mastfrog.acteurbase.ChainsRunner;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.netbeans.validation.api.InvalidInputException;

/**
 *
 * @author Tim Boudreau
 */
class PagesImpl2 {

    private final Application application;

    private final ScheduledExecutorService scheduler;

    private final ChainsRunner ch;

    private final boolean debug;

    @Inject
    PagesImpl2(Application application, Settings settings, @Named(DELAY_EXECUTOR) ScheduledExecutorService scheduler) {
        this.application = application;
        this.scheduler = scheduler;
        debug = settings.getBoolean("acteur.debug", false);
        ChainRunner chr = new ChainRunner(application.getWorkerThreadPool(), application.getRequestScope());
        ch = new ChainsRunner(application.getWorkerThreadPool(), application.getRequestScope(), chr);
    }

    public CountDownLatch onEvent(RequestID id, Event<?> event, Channel channel) {
        CountDownLatch latch = new CountDownLatch(1);

        Closables clos = new Closables(channel, application.control());
        ChainToPageConverter chainConverter = new ChainToPageConverter(id, event, clos);
        Iterator<Page> pageIterator = new ScopeWrapIterator<Page>(application.getRequestScope(), application.iterator(), id, event, channel, clos);
        Iterable<PageChain> pagesIterable
                = CollectionUtils.toIterable(CollectionUtils.convertedIterator(chainConverter, pageIterator));

        CB callback = new CB(id, event, latch, channel);

        CancelOnChannelClose closer = new CancelOnChannelClose();
        channel.closeFuture().addListener(closer);
        ch.submit(pagesIterable, callback, closer.cancelled, id, event, clos);

        return latch;
    }

    static class CancelOnChannelClose implements ChannelFutureListener {

        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            cancelled.set(true);
        }

    }

    class CB implements ChainCallback<Acteur, com.mastfrog.acteur.State, PageChain, Response, ResponseImpl>, ResponseSender {

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
        public void onAfterRunOne(PageChain chain, Acteur acteur) {
            if (Page.get() == chain.page) {
                Page.clear();
            }
        }

        @Override
        public void onDone(com.mastfrog.acteur.State state, List<ResponseImpl> responses) {
            ResponseImpl finalR = new ResponseImpl();
            // Coalesce the responses generated by individual acteurs
            for (ResponseImpl r : responses) {
                finalR.merge(r);
            }
            receive(state.getActeur(), state, finalR);
            latch.countDown();
        }

        @Override
        public void onRejected(com.mastfrog.acteur.State state) {
            throw new UnsupportedOperationException("Should not ever be called from ChainsRunner");
        }

        @Override
        public void onNoResponse() {
            application.send404(id, event, channel);
            latch.countDown();
        }

        @Override
        public void onFailure(Throwable ex) {
            uncaughtException(Thread.currentThread(), ex);
            latch.countDown();
        }

        @Override
        public void receive(final Acteur acteur, final com.mastfrog.acteur.State state, final ResponseImpl response) {
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
                            c.call();
                        } else {
                            if (debug) {
                                System.err.println("Response will be delayed for " + delay);
                            }
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

        boolean inUncaughtException = false;

        @Override
        public void uncaughtException(Thread thread, Throwable thrwbl) {
            try {
                if (inUncaughtException) {
                    // We have recursed - something was thrown from the ErrorActeur - 
                    // bail out and we'll be caught by the catch block below
                    // and write a plain response
                    throw thrwbl;
                }
                // Certain things we just bail out on
                if (thrwbl instanceof ThreadDeath || thrwbl instanceof OutOfMemoryError) {
                    Exceptions.chuck(thrwbl);
                }
                // These should not be logged - they can be thrown when validating input
                if (!(thrwbl instanceof ResponseException && !(thrwbl instanceof InvalidInputException))) {
                    thrwbl.printStackTrace();
                    application.internalOnError(thrwbl);
                }
                // V1.6 - we no longer have access to the page where the exception was
                // thrown
                ErrorPage pg = application.getDependencies().getInstance(ErrorPage.class);
                pg.setApplication(application);
                // Build up a fake context for ErrorActeur to operate in
                try (AutoCloseable ac = Page.set(pg)) {
                    inUncaughtException = true;
                    try (AutoCloseable ac2 = application.getRequestScope().enter(id, event, channel)) {
                        Acteur err = Acteur.error(null, pg, thrwbl,
                                application.getDependencies().getInstance(HttpEvent.class), true);

                        receive(err, err.getState(), err.getResponse());
                    }
                } finally {
                    inUncaughtException = false;
                }
            } catch (Throwable ex) {
                try {
                    if (channel.isOpen()) {
                        ByteBuf buf = channel.alloc().buffer();
                        try (PrintStream ps = new PrintStream(new ByteBufOutputStream(buf))) {
                            ex.printStackTrace(ps);
                        }
                        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, buf);
                        Headers.write(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8, resp);
                        Headers.write(Headers.CONTENT_LENGTH, (long) buf.writerIndex(), resp);
                        Headers.write(Headers.CONTENT_LANGUAGE, Locale.ENGLISH, resp);
                        Headers.write(Headers.CACHE_CONTROL, CacheControl.PRIVATE_NO_CACHE_NO_STORE, resp);
                        Headers.write(Headers.DATE, new DateTime(), resp);
                        channel.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
                    }
                } finally {
                    application.internalOnError(ex);
                }
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

    // A fake page for use with errors
    static class ErrorPage extends Page {

    }

    class ChainToPageConverter implements Converter<PageChain, Page> {

        private final RequestID id;
        private final Event<?> event;
        private final Closables clos;

        private ChainToPageConverter(RequestID id, Event<?> event, Closables clos) {
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
            PageChain result = new PageChain(application.getDependencies(), application.getRequestScope(), Acteur.class, r, r, id, event, clos);
            return result;
        }

        @Override
        public Page unconvert(PageChain t) {
            return t.page;
        }
    }

    static class PageChain extends ArrayChain<Acteur> {

        private final Page page;
        private final Object[] ctx;
        private AtomicBoolean first = new AtomicBoolean(true);
        private final ReentrantScope scope;

        public PageChain(Dependencies deps, ReentrantScope scope, Class<? super Acteur> type, Page page, Object... ctx) {
            super(deps, type, page.acteurs());
            this.page = page;
            this.ctx = ctx;
            this.scope = scope;
        }

        @Override
        public Object[] getContextContribution() {
            // First round we need to wrap the callable in the scope with
            // these objects;  they will already be in scope when it is
            // wrapped for a subsequent call
            if (first.compareAndSet(true, false)) {
                return ctx;
            } else {
                return new Object[0];
            }
        }

        public String toString() {
            return "Chain for " + page;
        }
    }

    static class ScopeWrapIterator<T> implements Iterator<T> {
        private final ReentrantScope scope;

        private final Iterator<T> delegate;
        private final Object[] ctx;

        public ScopeWrapIterator(ReentrantScope scope, Iterator<T> delegate, Object... ctx) {
            this.scope = scope;
            this.delegate = delegate;
            this.ctx = ctx;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public T next() {
            try (QuietAutoCloseable clos = scope.enter(ctx)) {
                return delegate.next();
            }
        }

        @Override
        public void remove() {
            delegate.remove();
        }
//
//        @Override
//        public void forEachRemaining(Consumer<? super T> cnsmr) {
//            delegate.forEachRemaining(cnsmr);
//        }
    }
}
