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

import com.google.common.net.MediaType;
import com.mastfrog.acteur.Acteur.BaseState;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorRenderer;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteurbase.AbstractActeur;
import com.mastfrog.acteurbase.ActeurResponseFactory;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Codec;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Invokable;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A single piece of logic which can
 * <ul>
 * <li>Reject an HTTP request</li>
 * <li>Validate an HTTP request and allow the next Acteur in the chain to
 * process it</li>
 * <li>Initiate an HTTP response</li>
 * </ul>
 * Acteurs are aggregated into a list in a {@link Page}. All of an Acteur's work
 * happens either in its constructor, prior to a call to setState(), or in its
 * overridden getState() method. The state determines whether processing of the
 * current list of Acteurs will continue, or if not, what happens to it.
 * <p/>
 * Acteurs are constructed by Guice - in fact, what a Page has is usually just a
 * list of classes. Objects they need, such as the current request
 * {@link HttpEvent} can simply be constructor parameters if the constructor is
 * annotated with Guice's &#064;Inject.
 * <p/>
 * An Acteur may construct some objects which will then be included in the set
 * of objects the next Acteur in the chain can request for injection in its
 * constructor parameters.
 * <p/>
 * A number of inner classes are provided which can be used as standard states.
 * <p/>
 * Acteurs may be - in fact, are likely to be - called asynchronously. For a
 * given page, they will always be called in the sequence that page lists them
 * in, but there is no guarantee that any two adjacent Acteurs will be called on
 * the same thread. Any shared state should take the form of objects put into
 * the context when the output State is created.
 * <p/>
 * This makes it possible to incrementally respond to a request, for example,
 * doing just enough computation to determine if a NOT MODIFIED response is
 * possible without computing the complete response (which in that case would be
 * thrown away).
 * <p/>
 * Their asynchronous nature means that many requests can be handled
 * simultaneously and run small bits of logic, interleaved, on fewer threads,
 * for maximum throughput.
 *
 * @author Tim Boudreau
 */
public abstract class Acteur extends AbstractActeur<Response, ResponseImpl, State> {

    class BaseState extends com.mastfrog.acteur.State {

        protected final Page page;

        public BaseState(Object... context) {
            super(context);
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Page not set");
            }
        }

        public BaseState(boolean rejected) {
            super(rejected);
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Page not set");
            }
        }

        @Override
        public final Acteur getActeur() {
            return Acteur.this;
        }

    }

    /**
     * Create an acteur.
     *
     * @param async If true, the framework should prefer to run the <i>next</i>
     * action asynchronously
     */
    protected Acteur() {
        super(INSTANCE);
    }
    private static final RT INSTANCE = new RT();

    protected com.mastfrog.acteur.State getState() {
        return super.getState();
    }

    final Throwable creationStackTrace() {
        return creationStackTrace;
    }

    static class RT extends ActeurResponseFactory<Response, ResponseImpl> {

        @Override
        protected ResponseImpl create() {
            return new ResponseImpl();
        }

        @Override
        protected boolean isFinished(ResponseImpl obj) {
            return obj != null && obj.status != null;
        }

        @Override
        protected boolean isModified(ResponseImpl obj) {
            return obj != null && obj.isModified();
        }

    }

    /**
     * If you write an acteur which delegates to another one, implement this so
     * that that other one's changes to the response will be picked up. This
     * pattern is sometimes used where a choice is made about which acteur to
     * call next.
     */
    public interface Delegate {

        /**
         * Get the acteur being delegated to
         *
         * @return An acteur
         */
        Acteur getDelegate();
    }

    protected <T> Acteur add(HeaderValueType<T> decorator, T value) {
        response().add(decorator, value);
        return this;
    }

    protected ResponseImpl getResponse() {
        if (this instanceof Delegate) {
            return ((Delegate) this).getDelegate().getResponse();
        }
        return super.getResponse();
    }

    protected <T> T get(HeaderValueType<T> header) {
        return response().get(header);
    }

    public final Acteur setResponseCode(HttpResponseStatus status) {
        response().setResponseCode(status);
        return this;
    }

    public final Acteur setMessage(String message) {
        response().setMessage(message);
        return this;
    }

    public final Acteur setChunked(boolean chunked) {
        response().setChunked(chunked);
        return this;
    }

    protected final Response response() {
        if (this instanceof Delegate) {
            return ((Delegate) this).getDelegate().response();
        }
        return super.response();
    }

    static Acteur error(Acteur errSource, Page page, Throwable t, HttpEvent evt, boolean log) {
        try {
            return new ErrorActeur(errSource, evt, page, t, true, log);
        } catch (IOException ex) {
            page.application.internalOnError(t);
            try {
                return new ErrorActeur(errSource, evt, page, t, true, log);
            } catch (IOException ex1) {
                return Exceptions.chuck(ex1);
            }
        }
    }

    public void describeYourself(Map<String, Object> into) {
    }

    protected final Acteur noContent() {
        setState(new RespondWith(NO_CONTENT));
        return this;
    }

    protected final Acteur badRequest() {
        setState(new RespondWith(BAD_REQUEST));
        return this;
    }

    protected final Acteur badRequest(Object msg) {
        setState(new RespondWith(BAD_REQUEST, msg));
        return this;
    }

    protected final Acteur notFound() {
        setState(new RespondWith(NOT_FOUND));
        return this;
    }

    protected final Acteur notFound(Object msg) {
        setState(new RespondWith(NOT_FOUND, msg));
        return this;
    }

    protected final Acteur ok(Object msg) {
        setState(new RespondWith(OK, msg));
        return this;
    }

    protected final Acteur ok() {
        setState(new RespondWith(OK));
        return this;
    }

    protected final Acteur reply(HttpResponseStatus status) {
        setState(new RespondWith(status));
        return this;
    }

    protected final Acteur reply(HttpResponseStatus status, Object msg) {
        setState(new RespondWith(status, msg));
        return this;
    }
    
    protected final Acteur reply(Err err) {
        setState(new RespondWith(err));
        return this;
    }

    protected final Acteur reject() {
        setState(new RejectedState());
        return this;
    }

    protected final Acteur next(Object... context) {
        if (context == null || context.length == 0) {
            setState(new ConsumedState());
        } else {
            setState(new ConsumedLockedState(context));
        }
        return this;
    }

    /**
     * A shorthand state for responding with a particular http response code and
     * optional message, which if non-string, will be rendered as JSON.
     */
    public class RespondWith extends BaseState {

        public RespondWith(int status) {
            this(HttpResponseStatus.valueOf(status));
        }

        public RespondWith(HttpResponseStatus status) {
            this(status, null);
        }

        public RespondWith(int status, Object msg) {
            this(HttpResponseStatus.valueOf(status), msg);
        }

        public RespondWith(ErrorResponse err) {
            super(false);
            setResponseCode(err.status());
            ErrorRenderer ren = getLockedPage().getApplication().getDependencies().getInstance(ErrorRenderer.class);
            String s;
            try {
                s = ren.render(err, getLockedPage().getApplication().getDependencies().getInstance(HttpEvent.class));
                if (s != null) {
                    s = msgToString(err.message());
                }
                setMessage(s);
            } catch (IOException ex) {
                Exceptions.chuck(ex);
            }
        }

        /**
         * Acteur.this; Response which uses JSON
         *
         * @param status
         * @param msg
         */
        public RespondWith(HttpResponseStatus status, Object msg) {
            super(false);
            if (msg instanceof String) {
                setResponseCode(status);
                setMessage((String) msg);
            } else if (msg != null) {
                setResponseCode(status);
                setMessage(msgToString(msg));
            }
        }

        private String msgToString(Object msg) {
            try {
                Codec mapper = page.getApplication().getDependencies().getInstance(Codec.class);
                String m = msg instanceof String ? msg.toString() : msg != null
                        ? mapper.writeValueAsString(msg) + '\n' : null;
                if (m != null) {
                    return m;
                }
                return null;
            } catch (IOException ioe) {
                return Exceptions.chuck(ioe);
            }
        }

        public RespondWith(HttpResponseStatus status, String msg) {
            if (page == null) {
                IllegalStateException e = new IllegalStateException("Called outside ActionsImpl.onEvent");
                e.printStackTrace();
                throw e;
            }
            setResponseCode(status);
            if (msg != null) {
                setMessage(msg);
            }
        }

        @Override
        public String toString() {
            return "Respond with " + getResponse().getResponseCode() + " - "
                    + super.toString() + " - " + getResponse().getMessage();
        }
    }

    /**
     * A state indicating the acteur neither accepts nor definitively refuses a
     * request.
     */
    protected class RejectedState extends BaseState {


        public RejectedState() {
            super(true);
            if (page == null) {
                throw new IllegalStateException("Called outside ActionsImpl.onEvent");
            }
        }

        public RejectedState(HttpResponseStatus status) {
            super(true);
            setResponseCode(status);
        }
    }

    /**
     * State indicating that this acteur chain is taking responsibility for
     * responding to the request. It may optionally include objects which should
     * be available for injection into subsequent acteurs.
     */
    protected class ConsumedState extends BaseState {

        private final Page page;
        private final Object[] context;

        public ConsumedState(Object... context) {
            super(false);
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Called outside ActionsImpl.onEvent");
            }
            this.context = context;
        }
    }

    protected class ConsumedLockedState extends BaseState {

        private final Page page;

        public ConsumedLockedState(Object... context) {
            super(context);
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Called outside ActionsImpl.onEvent");
            }
        }
    }

    /**
     * Set a response writer which can iteratively be called back until the
     * response is completed. The writer will be created dynamically but any
     * object currently in scope can be injected into it.
     *
     * @param <T> The type of writer
     * @param writerType The writer class
     */
    protected final <T extends ResponseWriter> Acteur setResponseWriter(Class<T> writerType) {
        Page page = Page.get();
        Dependencies deps = page.getApplication().getDependencies();
        HttpEvent evt = deps.getInstance(HttpEvent.class);
        response();
        getResponse().setWriter(writerType, deps, evt);
        return this;
    }

    /**
     * Set a response writer which can iteratively be called back until the
     * response is completed.
     *
     * @param <T> The type of writer
     * @param writer The writer
     */
    protected final <T extends ResponseWriter> Acteur setResponseWriter(T writer) {
        Page page = Page.get();
        Dependencies deps = page.getApplication().getDependencies();
        HttpEvent evt = deps.getInstance(HttpEvent.class);
        response();
        getResponse().setWriter(writer, deps, evt);
        return this;
    }

    /**
     * Set a ChannelFutureListener which will be called after headers are
     * written and flushed to the socket; prefer
     * <code>setResponseWriter()</code> to this method unless you are not using
     * chunked encoding and want to stream your response (in which case, be sure
     * to setChunked(false) or you will have encoding errors).
     * <p/>
     * This method will dynamically construct the passed listener type using
     * Guice, and including all of the contents of the scope in which this call
     * was made.
     *
     * @param <T> a type
     * @param type The type of listener
     */
    protected final <T extends ChannelFutureListener> Acteur setResponseBodyWriter(final Class<T> type) {
        final Page page = Page.get();
        final Dependencies deps = page.getApplication().getDependencies();
        ReentrantScope scope = page.getApplication().getRequestScope();
        final AtomicReference<ChannelFuture> fut = new AtomicReference<>();

        // An object which can instantiate and run the listener
        class I extends Invokable<ChannelFuture, Void, Exception> {

            private ChannelFutureListener delegate;

            @Override
            public Void run(ChannelFuture argument) throws Exception {
                if (delegate == null) {
                    delegate = deps.getInstance(type);
                }
                delegate.operationComplete(argument);
                return null;
            }

            @Override
            public String toString() {
                return "Delegate for " + type;
            }
        }

        // A runnable-like object which takes an argument, and which can
        // be wrapped by the scope in order to reconstitute the scope contents
        // as they are now before constructing the actual listener
        final Invokable<ChannelFuture, Void, Exception> listenerInvoker
                = scope.wrap(new I(), fut);

        // Wrap this in a dummy listener which will create the real one on
        // demand
        class C implements ChannelFutureListener {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                try (AutoCloseable cl = Page.set(page)) {
                    fut.set(future);
                    listenerInvoker.run(future);
                }
            }

            @Override
            public String toString() {
                return "Delegate for " + listenerInvoker;
            }
        }

        ChannelFutureListener l = new C();
        setResponseBodyWriter(l);
        return this;
    }

    protected final Dependencies dependencies() {
        final Page p = Page.get();
        final Application app = p.getApplication();
        return app.getDependencies();
    }

    /**
     * Set a ChannelFutureListener which will be called after headers are
     * written and flushed to the socket; prefer
     * <code>setResponseWriter()</code> to this method unless you are not using
     * chunked encoding and want to stream your response (in which case, be sure
     * to setChunked(false) or you will have encoding errors).
     *
     * @param listener
     */
    public final Acteur setResponseBodyWriter(final ChannelFutureListener listener) {
        if (listener == ChannelFutureListener.CLOSE || listener == ChannelFutureListener.CLOSE_ON_FAILURE) {
            response();
            getResponse().setBodyWriter(listener);
            return this;
        }
        Page p = Page.get();
        final Application app = p.getApplication();
        class WL implements ChannelFutureListener, Callable<Void> {

            private ChannelFuture future;
            private Callable<Void> wrapper = app.getRequestScope().wrap(this);

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                this.future = future;
                wrapper.call();
            }

            @Override
            public Void call() throws Exception {
                listener.operationComplete(future);
                return null;
            }

            @Override
            public String toString() {
                return "Scope wrapper for " + listener;
            }
        }
        getResponse().setBodyWriter(new WL());
        return this;
    }

//    public <T extends State & com.mastfrog.acteur.State> State getState() {
//        return super.getState();
//    }
    public static Acteur wrap(final Class<? extends Acteur> type, final Dependencies deps) {
        Checks.notNull("type", type);
        final Charset charset = deps.getInstance(Charset.class);
        return new WrapperActeur(deps, charset, type);
    }

    static class WrapperActeur extends Acteur implements Delegate {

        private final Dependencies deps;
        private final Charset charset;
        private final Class<? extends Acteur> type;

        public WrapperActeur(Dependencies deps, Charset charset, Class<? extends Acteur> type) {
            this.deps = deps;
            this.charset = charset;
            this.type = type;
        }
        Acteur acteur;

        public Class<? extends Acteur> type() {
            return type;
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            try {
                delegate().describeYourself(into);
            } catch (Exception e) {
                //ok - we may be called without an event to play with
            }
        }

        boolean inOnError;

        protected void onError(Throwable t) throws UnsupportedEncodingException {
            if (inOnError) {
                Exceptions.chuck(t);
            }
            inOnError = true;
            try {
                if (!Dependencies.isProductionMode(deps.getInstance(Settings.class))) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    t.printStackTrace(new PrintStream(out));
                    add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                    this.setMessage(new String(out.toByteArray(), charset));
                }
                this.setResponseCode(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            } finally {
                inOnError = false;
            }
        }
        private com.mastfrog.acteur.State cachedState;

        Acteur delegate() {
            if (acteur == null) {
                try {
                    acteur = deps.getInstance(type);
                } catch (Exception e) {
                    try {
                        onError(e);
                        deps.getInstance(Application.class).internalOnError(e);
                    } catch (UnsupportedEncodingException ex) {
                        Exceptions.chuck(ex);
                    }
                }
            }
            return acteur;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            return cachedState == null ? cachedState = delegate().getState() : cachedState;
        }

        @Override
        public String toString() {
            return "Wrapper [" + (acteur == null ? type + " (type)" : acteur)
                    + " lastState=" + cachedState + "]";
        }

        @Override
        public Acteur getDelegate() {
            return delegate();
        }
    }
}
