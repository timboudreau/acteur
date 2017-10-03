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

import com.mastfrog.acteur.websocket.WebSocketUpgradeActeur;
import com.google.common.net.MediaType;
import com.google.inject.name.Named;
import com.mastfrog.acteur.errors.ResponseException;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.server.ServerModule.DELAY_EXECUTOR;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.ArrayChain;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.ChainCallback;
import com.mastfrog.acteurbase.ChainRunner;
import com.mastfrog.acteurbase.ChainsRunner;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.scope.ReentrantScope;
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
import io.netty.handler.codec.http.FullHttpResponse;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import io.netty.handler.codec.http.HttpHeaders;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.Attribute;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.inject.Inject;
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

    public CountDownLatch onEvent(RequestID id, Event<?> event, Channel channel, Object[] defaultContext) {
        CountDownLatch latch = new CountDownLatch(1);

        Iterable<PageChain> pagesIterable;
        Closables clos = null;
        if (event.request() instanceof WebSocketFrame) {
            Attribute<Supplier<? extends Chain<? extends Acteur, ?>>> s = channel.attr(WebSocketUpgradeActeur.CHAIN_KEY);
            Supplier<? extends Chain<? extends Acteur, ?>> chainSupplier = s.get();
            if (chainSupplier == null) {
                throw new IllegalStateException("Got a WebSocketFrame on a channel with no websocket chain set up");
            }

            PageChain pageChain = (PageChain) chainSupplier.get();
            clos = pageChain.findInContext(Closables.class);
            if (clos == null) {
                clos = new Closables(channel, application.control());
            }
            pageChain.addToContext(event);
            pageChain.page = channel.attr(WebSocketUpgradeActeur.PAGE_KEY).get();

            pagesIterable = Collections.singleton(pageChain);
        } else {
            clos = new Closables(channel, application.control());
            ChainToPageConverter chainConverter = new ChainToPageConverter(id, event, clos);

            boolean early = event instanceof HttpEvent && ((HttpEvent) event).isPreContent();
            Iterator<Page> pageIterator = early ? application.earlyPagesIterator() : application.iterator();
            if (defaultContext != null && defaultContext.length > 0) {
                pageIterator = new ScopeWrapIterator<>(application.getRequestScope(), pageIterator, defaultContext);
            }
            pagesIterable = CollectionUtils.toIterable(CollectionUtils.convertedIterator(chainConverter, pageIterator));
        }

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
            if (chain.page != null) {
                Page.set(chain.page);
            }
        }

        @Override
        public void onBeforeRunOne(PageChain chain, List<ResponseImpl> responsesThusFar) {
            ResponseImpl.shadowResponses.set(responsesThusFar);
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
        @SuppressWarnings("deprecation")
        public void receive(final Acteur acteur, final com.mastfrog.acteur.State state, final ResponseImpl response) {
            boolean isWebSocketResponse = event.request() instanceof WebSocketFrame && !(acteur instanceof WebSocketUpgradeActeur)
                    && response.isModified();
            if (isWebSocketResponse) {
                if (response.getMessage() instanceof WebSocketFrame) {
                    channel.writeAndFlush(response.getMessage());
                    return;
                } else if (response.getMessage() == null) {
                    // If no message, that just means we don't have a reply to this
                    // frame immediately - silently do not try to publish something
                    // - this is not http request/response.
                    return;
                }
                // XXX consider response.getDelay()?
                // This is ugly - we create an HttpResponse just to extract a wad of binary
                // data and send that as a WebSocketFrame.
                Charset charset = application.getDependencies().getInstance(Charset.class);
                try (QuietAutoCloseable cl = Page.set(state.getLockedPage())) {
                    HttpResponse resp = response.toResponse(event, charset);
                    if (resp instanceof FullHttpResponse) {
                        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(((FullHttpResponse) resp).content());
                        channel.writeAndFlush(frame);
                    }
                } catch (Exception ex) {
                    uncaughtException(Thread.currentThread(), ex);
                }
            } else if (response.isModified() && response.status != null) {
                // Actually send the response
                try (QuietAutoCloseable clos = Page.set(application.getDependencies().getInstance(Page.class))) {
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

                    // As long as Page.decorateResponse exists we need to sanity check this here:
                    switch (httpResponse.status().code()) {
                        case 204:
                        case 304:
                            HttpHeaders hdrs = httpResponse.headers();
                            hdrs.remove(CONTENT_LENGTH);
                            hdrs.remove(TRANSFER_ENCODING);
                            hdrs.remove(CONTENT_ENCODING);
                    }

                    // Abort if the client disconnected
                    if (!channel.isOpen()) {
                        latch.countDown();
                        return;
                    }
                    final HttpResponse resp = httpResponse;
                    try {
                        Closables closables = application.getDependencies().getInstance(Closables.class);
                        Callable<ChannelFuture> c = new ResponseTrigger(response, resp, state, acteur, closables, event);
                        Duration delay = response.getDelay();
                        if (delay == null) {
                            c.call();
                        } else {
                            if (debug) {
                                System.err.println("Response will be delayed for " + delay);
                            }
                            final ScheduledFuture<?> s = scheduler.schedule(c, delay.toMillis(), TimeUnit.MILLISECONDS);
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
                                application.getDependencies().getInstance(Event.class), true);

                        receive(err, err.getState(), err.getResponse());
                    }
                } finally {
                    inUncaughtException = false;
                }
            } catch (Throwable ex) {
                thrwbl.addSuppressed(ex);
                try {
                    if (channel.isOpen()) {
                        HttpResponse resp;
                        if (application.failureResponses != null) {
                            resp = application.failureResponses.createFallbackResponse(ex);
                        } else {
                            ByteBuf buf = channel.alloc().ioBuffer();
                            try (PrintStream ps = new PrintStream(new ByteBufOutputStream(buf))) {
                                thrwbl.printStackTrace(ps);
                            }
                            resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, buf);
                            Headers.write(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8, resp);
                            Headers.write(Headers.CONTENT_LENGTH, (long) buf.writerIndex(), resp);
                            Headers.write(Headers.CONTENT_LANGUAGE, Locale.ENGLISH, resp);
                            Headers.write(Headers.CACHE_CONTROL, CacheControl.PRIVATE_NO_CACHE_NO_STORE, resp);
                            Headers.write(Headers.DATE, ZonedDateTime.now(), resp);
                        }
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
            private final Closables closeables;
            private final Event<?> evt;

            ResponseTrigger(ResponseImpl response, HttpResponse resp, State state, Acteur acteur, Closables closeables, Event<?> evt) {
                this.response = response;
                this.resp = resp;
                this.state = state;
                this.acteur = acteur;
                this.closeables = closeables;
                this.evt = evt;
            }

            @Override
            public ChannelFuture call() throws Exception {
                // Give the application a last chance to do something
                application.onBeforeRespond(id, event, response.internalStatus());

                // Send the headers
                ChannelFuture fut = channel.writeAndFlush(resp);

                fut.addListener((ChannelFutureListener) (ChannelFuture future) -> {
                    if (!future.isSuccess() && future.cause() != null) {
                        application.internalOnError(future.cause());
                    }
                });

                final Page pg = state.getLockedPage();
                ChannelFuture bodyFuture = response.sendMessage(event, fut, resp);
                if (bodyFuture == fut && resp instanceof FullHttpResponse) {
                    // In the case of keep-alive connections (at least where no listeners
                    // flushing responses later are involved), let database connections, etc.
                    // be closed when the response is flushed - the connection might be kept
                    // alive for some time.
                    // XXX need to solve for the case of flushing multiple chunks with listeners
                    closeables.closeOn(fut);
                }
                application.onAfterRespond(id, event, acteur, pg, state, HttpResponseStatus.OK, resp);
                return bodyFuture;
            }
        }
    }

    static class CancelOnClose implements ChannelFutureListener {

        private final ScheduledFuture future;

        CancelOnClose(ScheduledFuture future) {
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
                Path pth = ((HttpEvent) event).path();
                Thread.currentThread().setName(pth + " for " + r.getClass().getName());
            } else {
                Thread.currentThread().setName(id + " of " + r.getClass().getName());
            }
            PageChain result = new PageChain(application.getDependencies(), application.getRequestScope(), Acteur.class, r, r, id, event, clos);
            return result;
        }

        @Override
        public Page unconvert(PageChain t) {
            return t.page;
        }
    }

    static class PageChain extends ArrayChain<Acteur, PageChain> {

        private Page page;
        private Object[] ctx;
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final ReentrantScope scope;
        private static final Object[] EMPTY = new Object[0];
        boolean isReconstituted;

        PageChain(Dependencies deps, ReentrantScope scope, Class<? super Acteur> type, Page page, Object... ctx) {
            super(deps, type, page.acteurs());
            this.page = page;
            this.ctx = ctx;
            this.scope = scope;
        }

        PageChain(Dependencies deps, ReentrantScope scope, Class<? super Acteur> type, List<Object> pages, Object[] ctx) {
            super(deps, type, pages);
            isReconstituted = true;
            this.scope = scope;
            this.ctx = ctx;
            this.page = null;
        }

        public <T> T findInContext(Class<T> type) {
            if (ctx != null) {
                for (int i = ctx.length - 1; i >= 0; i--) {
                    if (type.isInstance(ctx[i])) {
                        return type.cast(ctx[i]);
                    }
                }
            }
            return null;
        }

        @Override
        public Iterator<Acteur> iterator() {
            Iterator<Acteur> orig = super.iterator();
            Iterator<Acteur> result = orig;
            if (isReconstituted) {
                result = new Iterator<Acteur>() {
                    @Override
                    public boolean hasNext() {
                        return orig.hasNext();
                    }

                    @Override
                    public Acteur next() {
                        // XXX why are we not getting the context here?
                        try (QuietAutoCloseable cl1 = scope.enter(ctx)) {
                            try (QuietAutoCloseable cl = Page.set(page)) {
                                return orig.next();
                            }
                        }
                    }
                };
            }
            return result;
        }

        @Override
        public Object[] getContextContribution() {
            // First round we need to wrap the callable in the scope with
            // these objects;  they will already be in scope when it is
            // wrapped for a subsequent call
            if (first.compareAndSet(true, false)) {
                return ctx;
            } else {
                return EMPTY;
            }
        }

        @Override
        public String toString() {
            return "Chain for " + page;
        }

        private Object[] combine(Object[] a, Object[] b) {
            if (a.length == 0) {
                return b;
            } else if (b.length == 0) {
                return a;
            } else {
                Object[] nue = new Object[a.length + b.length];
                System.arraycopy(a, 0, nue, 0, a.length);
                System.arraycopy(b, 0, nue, a.length, b.length);
                return nue;
            }
        }

        @Override
        public Supplier<PageChain> remnantSupplier(Object... scopeContents) {
            Object[] context = combine(ctx, scopeContents);
            assert chainPosition != null : "Called out of sequence";
            int pos = chainPosition.get();
            final List<Object> rem = new ArrayList<>(types.size() - pos);
            for (int i = pos; i < types.size(); i++) {
                rem.add(types.get(i));
            }
            final Dependencies d = deps;
            return () -> {
                List<Object> l = new ArrayList<>(rem);
                PageChain chain = new PageChain(d, scope, type, l, context);
                chain.page = page;
                return chain;
            };
        }

        private void addToContext(Event<?> event) {
            Object[] nue = new Object[ctx.length + 1];
            System.arraycopy(ctx, 0, nue, 0, ctx.length);
            nue[nue.length - 1] = event;
            ctx = nue;
        }
    }

    static class ScopeWrapIterator<T> implements Iterator<T> {

        private final ReentrantScope scope;

        private final Iterator<T> delegate;
        private final Object[] ctx;

        ScopeWrapIterator(ReentrantScope scope, Iterator<T> delegate, Object... ctx) {
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
    }
}
