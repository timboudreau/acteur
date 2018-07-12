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
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.DELAY_EXECUTOR;
import static com.mastfrog.acteur.server.ServerModule.X_INTERNAL_COMPRESS_HEADER;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.ActeurState;
import com.mastfrog.acteurbase.ArrayChain;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.ChainCallback;
import com.mastfrog.acteurbase.ChainRunner;
import com.mastfrog.acteurbase.ChainsRunner;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DeploymentMode;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Strings;
import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import com.mastfrog.util.thread.NonThrowingAutoCloseable;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AsciiString;
import io.netty.util.Attribute;
import static io.netty.util.CharsetUtil.UTF_8;
import java.io.PrintStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
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

    private final boolean disableFilterPathsAndMethods;

    private final boolean renderStackTraces;

    private final boolean httpCompressorEnabled;

    static final HeaderValueType<CharSequence> X_BODY_GENERATOR = Headers.header(new AsciiString("X-Body-Generator"));

    @Inject
    PagesImpl2(Application application, Settings settings, @Named(DELAY_EXECUTOR) ScheduledExecutorService scheduler,
            DeploymentMode mode, ReentrantScope scope, @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService exe) {
        this.application = application;
        this.scheduler = scheduler;
        disableFilterPathsAndMethods = settings.getBoolean("disable.filter", false);
        renderStackTraces = settings.getBoolean(ServerModule.SETTINGS_KEY_RENDER_STACK_TRACES, !mode.isProduction());
        debug = settings.getBoolean("acteur.debug", false);
        httpCompressorEnabled = settings.getBoolean(ServerModule.HTTP_COMPRESSION, true);
        ChainRunner chr = new ChainRunner(exe, scope);
        ch = new ChainsRunner(exe, scope, chr);
    }

    /**
     * Determine if we should use channel.write() rather than
     * channel.writeAndFlush() when sending the HTTP headers in response to a
     * request. Better performance can be had by delaying sending the headers
     * until the first wad of response body is available, BUT that means the
     * HttpContentEncoder will not get to compress the first chunk of the HTTP
     * response, which breaks the response. So, if the response is going to get
     * compressed by Netty's HttpContentCompressor, flush the headers
     * immediately.
     *
     * @param evt The event in question
     * @param response The response
     * @return True if the headers need not be flushed immediately
     */
    private boolean canPostponeFlush(Event<?> evt, ResponseImpl response) {
        if (!response.hasListener()) {
            // It is a full response or has no body - flush it immediately and
            // be done
            return false;
        }
        // Websocket event - irrelevant
        if (!(evt instanceof HttpEvent)) {
            return false;
        }
        HttpEvent httpEvent = (HttpEvent) evt;
        // If X-Internal-Compress is present, the compressor is not going to touch it anyway
        if (response.get(X_INTERNAL_COMPRESS_HEADER) != null) {
            return true;
        }
        // If compression is off, it's all a non-issue
        if (httpCompressorEnabled) {
            // If the content encoding is explicitly set to "identity",
            // the compressor will ignore it
            CharSequence contentEncoding = response.get(Headers.CONTENT_ENCODING);
            if (contentEncoding != null && (HttpHeaderValues.IDENTITY == contentEncoding || HttpHeaderValues.IDENTITY.contentEquals(contentEncoding))) {
                return true;
            }
            CharSequence seq = httpEvent.header(Headers.ACCEPT_ENCODING);
            // If the client does not acccept compressed responses we will not be sending one
            if (seq != null) {
                // Do the fast test first
                if (seq == HttpHeaderValues.GZIP_DEFLATE || seq == HttpHeaderValues.GZIP || seq == HttpHeaderValues.DEFLATE) {
                    return false;
                }
                // Test for gzip
                if (Strings.charSequenceContains(seq, HttpHeaderValues.GZIP, debug)) {
                    return false;
                }
                // Test for deflate
                if (Strings.charSequenceContains(seq, HttpHeaderValues.DEFLATE, debug)) {
                    return false;
                }
            }
        }
        return true;
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
            application.probe.onBeforeRunPage(id, event, pageChain.page);
            pagesIterable = Collections.singleton(pageChain);
        } else {
            clos = new Closables(channel, application.control());
            ChainToPageConverter chainConverter = new ChainToPageConverter(id, event, clos);
            boolean early = event instanceof HttpEvent && ((HttpEvent) event).isPreContent();
            Iterator<Page> pageIterator = disableFilterPathsAndMethods
                    ? (early ? application.earlyPagesIterator() : application.iterator())
                    : early ? application.earlyPagesIterator((HttpEvent) event) : application.iterator((HttpEvent) event);
            if (defaultContext != null && defaultContext.length > 0) {
                pageIterator = new ScopeWrapIterator<>(application.getRequestScope(), pageIterator, defaultContext);
            }
            pagesIterable = CollectionUtils.toIterable(CollectionUtils.convertedIterator(chainConverter, pageIterator));
        }

        CB callback = new CB(id, event, latch, channel, clos);
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
        private final Closables closables;

        CB(RequestID id, Event<?> event, CountDownLatch latch, Channel channel, Closables closeables) {
            this.event = event;
            this.latch = latch;
            this.channel = channel;
            this.id = id;
            this.closables = closeables;
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
            Page p = Page.get();
            if (p == chain.page) {
                Page.clear();
            }
            application.probe.onActeurWasRun(id, event, p, acteur, null);
        }

        @Override
        public void onAfterRunOne(PageChain chain, Acteur acteur, ActeurState state) {
            application.probe.onActeurWasRun(id, event, Page.get(), acteur, state);
            onAfterRunOne(chain, acteur);
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
            application.probe.onBeforeSendResponse(id, event, acteur, response.status, response.hasListener(), response.message());
            boolean isWebSocketResponse = event.request() instanceof WebSocketFrame && !(acteur instanceof WebSocketUpgradeActeur)
                    && response.isModified();
            if (isWebSocketResponse) {
                if (handleWebsocketResponse(response, state)) {
                    return;
                }
            } else if (response.isModified() && response.status != null) {
                handleHttpResponse(response, state, acteur);
            } else {
                onNoResponse();
            }
        }

        private void handleHttpResponse(final ResponseImpl response, final State state, final Acteur acteur) {
            // Actually send the response
            try (NonThrowingAutoCloseable clos = Page.set(application.getDependencies().getInstance(Page.class))) {
                // Abort if the client disconnected
                if (!channel.isOpen()) {
                    latch.countDown();
                    return;
                }

                application._onBeforeSendResponse(response.status, event, response, state.getActeur(), state.getLockedPage());
                // Create a netty response
                HttpResponse httpResponse = response.toResponse(event, application.charset);
                // Allow the application to add headers
                httpResponse = application._decorateResponse(id, event, state.getLockedPage(), acteur, httpResponse);
                if (debug && response.hasListener()) {
                    httpResponse.headers().add(X_BODY_GENERATOR.name(), response.listenerString());
                }

                // Abort if the client disconnected
                if (!channel.isOpen()) {
                    latch.countDown();
                    return;
                }
                final HttpResponse resp = httpResponse;
                try {
                    Callable<ChannelFuture> c = new ResponseTrigger(response, resp, state, acteur, closables, event);
                    Duration delay = response.getDelay();
                    if (delay == null) {
                        c.call();
                    } else {
                        if (debug) {
                            System.err.println("Response will be delayed for " + delay);
                        }
                        application.probe.onInfo("Response delayed {0}", delay);
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
        }

        private boolean handleWebsocketResponse(final ResponseImpl response, final State state) {
            if (response.getMessage() instanceof WebSocketFrame) {
                channel.writeAndFlush(response.getMessage());
                return true;
            } else if (response.getMessage() == null) {
                // If no message, that just means we don't have a reply to this
                // frame immediately - silently do not try to publish something
                // - this is not http request/response.
                return true;
            }
            // XXX consider response.getDelay()?
            // This is ugly - we create an HttpResponse just to extract a wad of binary
            // data and send that as a WebSocketFrame.
            try (NonThrowingAutoCloseable cl = Page.set(state.getLockedPage())) {
                HttpResponse resp = response.toResponse(event, application.charset);
                if (resp instanceof FullHttpResponse) {
                    BinaryWebSocketFrame frame = new BinaryWebSocketFrame(((FullHttpResponse) resp).content());
                    channel.writeAndFlush(frame);
                }
            } catch (Exception ex) {
                uncaughtException(Thread.currentThread(), ex);
            }
            return false;
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
                application.probe.onThrown(id, event, thrwbl);
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
                ErrorPage pg = new ErrorPage();
                pg.setApplication(application);
                // Build up a fake context for ErrorActeur to operate in
                try (NonThrowingAutoCloseable ac = Page.set(pg)) {
                    inUncaughtException = true;
                    try (AutoCloseable ac2 = application.getRequestScope().enter(id, event, channel)) {
                        Acteur err = Acteur.error(null, pg, thrwbl,
                                application.getDependencies().getInstance(Event.class), renderStackTraces);

                        receive(err, err.getState(), err.getResponse());
                    }
                } finally {
                    inUncaughtException = false;
                }
            } catch (Throwable ex) {
                if (ex != thrwbl) {
                    thrwbl.addSuppressed(ex);
                }
                application.probe.onThrown(id, event, thrwbl);
                try {
                    if (channel.isOpen()) {
                        HttpResponse resp;
                        if (application.failureResponses != null) {
                            resp = application.failureResponses.createFallbackResponse(ex);
                        } else {
                            ByteBuf buf;
                            if (renderStackTraces) {
                                buf = channel.alloc().ioBuffer();
                                try (PrintStream ps = new PrintStream(new ByteBufOutputStream(buf))) {
                                    thrwbl.printStackTrace(ps);
                                }
                            } else {
                                String msg = ex.getMessage();
                                if (msg == null) {
                                    msg = ex.getClass().getSimpleName();
                                }
                                byte[] bytes = msg.getBytes(UTF_8);
                                buf = Unpooled.wrappedBuffer(bytes);
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
                ChannelFuture fut;
                if (canPostponeFlush(evt, response)) {
                    // Better performance if we delay sending the headers until
                    // the first response chunk is flushed, which is the responsibility
                    // of the listener. So, here we'll just use write(), and let
                    // whatever write the listener does cause a flush
                    fut = channel.write(resp);
                } else {
                    fut = channel.writeAndFlush(resp);
                }

                fut.addListener(application.errorLoggingListener);

                final Page pg = state.getLockedPage();
                ChannelFuture bodyFuture = response.sendMessage(event, fut, resp, response.hasListener());
                if (bodyFuture == fut && resp instanceof FullHttpResponse) {
                    // In the case of keep-alive connections (at least where no listeners
                    // flushing responses later are involved), let database connections, etc.
                    // be closed when the response is flushed - the connection might be kept
                    // alive for some time.
                    // XXX need to solve for the case of flushing multiple chunks with listeners
                    closeables.closeOn(fut);
                }
                if (bodyFuture != fut && !response.hasListener()) {
                    bodyFuture.addListener(application.errorLoggingListener);
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
            application.probe.onBeforeRunPage(id, event, r);
            PageChain result = new PageChain(application, application.getDependencies(), application.getRequestScope(), Acteur.class, r, r, id, event, clos);
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
        private Application app;

        PageChain(Application app, Dependencies deps, ReentrantScope scope, Class<? super Acteur> type, Page page, Object... ctx) {
            super(deps, type, page.acteurs(app.isDefaultCorsHandlingEnabled()));
            this.page = page;
            this.ctx = ctx;
            this.scope = scope;
            this.app = app;
        }

        PageChain(Application app, Dependencies deps, ReentrantScope scope, Class<? super Acteur> type, List<Object> pages, Object[] ctx) {
            super(deps, type, pages);
            isReconstituted = true;
            this.scope = scope;
            this.ctx = ctx;
            this.page = null;
            this.app = app;
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

        @Override
        public Supplier<PageChain> remnantSupplier(Object... scopeContents) {
            Object[] context = ArrayUtils.concatenate(ctx, scopeContents);
            assert chainPosition != null : "Called out of sequence";
            int pos = chainPosition.get();
            final List<Object> rem = new ArrayList<>(types.size() - pos);
            for (int i = pos; i < types.size(); i++) {
                rem.add(types.get(i));
            }
            return () -> {
                List<Object> l = new ArrayList<>(rem);
                PageChain chain = new PageChain(app, deps, scope, type, l, context);
                chain.page = page;
                return chain;
            };
        }

        private void addToContext(Event<?> event) {
            ctx = ArrayUtils.concatenate(ctx, new Object[]{event});
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
            try (NonThrowingAutoCloseable clos = scope.enter(ctx)) {
                return delegate.next();
            }
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }
}
