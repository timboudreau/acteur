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
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
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
public abstract class Acteur {

    private State state;
    Throwable creationStackTrace;

    /**
     * Create an acteur.
     *
     * @param async If true, the framework should prefer to run the <i>next</i>
     * action asynchronously
     */
    protected Acteur() {
        boolean asserts = false;
        assert asserts = true;
        if (asserts) {
            creationStackTrace = new Throwable();
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

    private volatile ResponseImpl response;

    protected <T> void add(HeaderValueType<T> decorator, T value) {
        getResponse().add(decorator, value);
    }

    ResponseImpl getResponse() {
        if (this instanceof Delegate) {
            return ((Delegate) this).getDelegate().getResponse();
        }
        if (response == null) {
            synchronized (this) {
                if (response == null) {
                    response = new ResponseImpl();
                }
            }
        }
        return response;
    }

    protected <T> T get(HeaderValueType<T> header) {
        return getResponse().get(header);
    }

    public void setResponseCode(HttpResponseStatus status) {
        getResponse().setResponseCode(status);
    }

    public void setMessage(String message) {
        getResponse().setMessage(message);
    }

    protected void setState(State state) {
        Checks.notNull("state", state);
        this.state = state;
    }

    public void setChunked(boolean chunked) {
        getResponse().setChunked(chunked);
    }

    protected Response response() {
        return getResponse();
    }

    static Acteur error(Page page, Throwable t) {
        return new ErrorActeur(page, t);
    }

    public void describeYourself(Map<String, Object> into) {
    }

    protected void noContent() {
        setState(new RespondWith(NO_CONTENT));
    }

    protected void badRequest() {
        setState(new RespondWith(BAD_REQUEST));
    }

    protected void badRequest(Object msg) {
        setState(new RespondWith(BAD_REQUEST, msg));
    }

    protected void notFound() {
        setState(new RespondWith(NOT_FOUND));
    }

    protected void notFound(Object msg) {
        setState(new RespondWith(NOT_FOUND, msg));
    }

    protected void ok(Object msg) {
        setState(new RespondWith(OK, msg));
    }

    protected void ok() {
        setState(new RespondWith(OK));
    }

    static final class ErrorActeur extends Acteur {

        ErrorActeur(Page page, Throwable t) {
            StringBuilder sb = new StringBuilder("Page " + page + " (" + page.getClass().getName() + " threw " + t.getMessage() + '\n');
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                t.printStackTrace(new PrintStream(out));
                sb.append(new String(out.toByteArray()));
            } catch (IOException ioe) {
            }
            setState(new RespondWith(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    sb.toString()));
        }
    }

    /**
     * A shorthand state for responding with a particular http response code and
     * optional message, which if non-string, will be rendered as JSON.
     */
    public class RespondWith extends State {

        private final Page page;

        public RespondWith(int status) {
            this(HttpResponseStatus.valueOf(status));
        }

        public RespondWith(HttpResponseStatus status) {
            this(status, null);
        }

        public RespondWith(int status, Object msg) {
            this(HttpResponseStatus.valueOf(status), msg);
        }

        /**
         * Response which uses JSON
         *
         * @param status
         * @param msg
         */
        public RespondWith(HttpResponseStatus status, Object msg) {
            page = Page.get();
            if (msg instanceof String) {
                setResponseCode(status);
                setMessage((String) msg);
            } else if (msg != null) {
                if (page == null) {
                    throw new IllegalStateException("No page set");
                }
                Codec mapper = page.getApplication().getDependencies().getInstance(Codec.class);
                try {
                    String m = msg instanceof String ? msg.toString() : msg != null
                            ? mapper.writeValueAsString(msg) + '\n' : null;
                    setResponseCode(status);
                    if (m != null) {
                        setMessage(m);
                    }
                } catch (IOException ioe) {
                    Exceptions.chuck(ioe);
                }
            }
        }

        public RespondWith(HttpResponseStatus status, String msg) {
            page = Page.get();
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
        protected boolean isLockedInChain() {
            return false;
        }

        @Override
        protected boolean isConsumed() {
            return false;
        }

        @Override
        protected Page getLockedPage() {
            return page;
        }

        @Override
        protected Acteur getActeur() {
            return Acteur.this;
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
    protected class RejectedState extends State {

        private final Page page;

        public RejectedState() {
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Called outside ActionsImpl.onEvent");
            }
        }

        public RejectedState(HttpResponseStatus status) {
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Called outside ActionsImpl.onEvent");
            }
            setResponseCode(status);
        }

        @Override
        protected boolean isLockedInChain() {
            return false;
        }

        @Override
        protected boolean isConsumed() {
            return false;
        }

        @Override
        protected Page getLockedPage() {
            return page;
        }

        @Override
        protected Acteur getActeur() {
            return Acteur.this;
        }
    }

    /**
     * State indicating that this acteur chain is taking responsibility for
     * responding to the request. It may optionally include objects which should
     * be available for injection into subsequent acteurs.
     */
    protected class ConsumedState extends State {

        private final Page page;
        private final Object[] context;

        public ConsumedState(Object... context) {
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Called outside ActionsImpl.onEvent");
            }
            this.context = context;
        }

        @Override
        protected Object[] getContext() {
            return context;
        }

        @Override
        protected boolean isLockedInChain() {
            return false;
        }

        @Override
        protected boolean isConsumed() {
            return true;
        }

        @Override
        protected Page getLockedPage() {
            return page;
        }

        @Override
        protected Acteur getActeur() {
            return Acteur.this;
        }
    }

    protected class ConsumedLockedState extends State {

        private final Page page;
        private final Object[] context;

        public ConsumedLockedState(Object... context) {
            page = Page.get();
            if (page == null) {
                throw new IllegalStateException("Called outside ActionsImpl.onEvent");
            }
            this.context = context;
        }

        @Override
        protected Object[] getContext() {
            return context;
        }

        @Override
        protected boolean isLockedInChain() {
            return true;
        }

        @Override
        protected boolean isConsumed() {
            return true;
        }

        @Override
        protected Page getLockedPage() {
            return page;
        }

        @Override
        protected Acteur getActeur() {
            return Acteur.this;
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
    protected <T extends ResponseWriter> void setResponseWriter(Class<T> writerType) {
        Page page = Page.get();
        Dependencies deps = page.getApplication().getDependencies();
        HttpEvent evt = deps.getInstance(HttpEvent.class);
        getResponse().setWriter(writerType, deps, evt);
    }

    /**
     * Set a response writer which can iteratively be called back until the
     * response is completed.
     *
     * @param <T> The type of writer
     * @param writer The writer
     */
    protected <T extends ResponseWriter> void setResponseWriter(T writer) {
        Page page = Page.get();
        Dependencies deps = page.getApplication().getDependencies();
        HttpEvent evt = deps.getInstance(HttpEvent.class);
        getResponse().setWriter(writer, deps, evt);
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
    protected final <T extends ChannelFutureListener> void setResponseBodyWriter(final Class<T> type) {
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
    }

    protected Dependencies dependencies() {
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
    public final void setResponseBodyWriter(final ChannelFutureListener listener) {
        if (listener == ChannelFutureListener.CLOSE || listener == ChannelFutureListener.CLOSE_ON_FAILURE) {
            getResponse().setBodyWriter(listener);
            return;
        }
        final Page p = Page.get();
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
    }

    public State getState() {
        return state;
    }

    static Acteur wrap(final Class<? extends Acteur> type, final Dependencies deps) {
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

        @Override
        ResponseImpl getResponse() {
            if (acteur != null) {
                return acteur.getResponse();
            }
            return super.getResponse();
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
        private State cachedState;

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
        public State getState() {
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
