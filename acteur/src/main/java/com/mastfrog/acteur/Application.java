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

import com.google.common.base.Optional;
import com.mastfrog.acteur.headers.Headers;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.url.Path;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Invokable;
import com.mastfrog.util.Streams;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import org.joda.time.DateTime;

/**
 * A web application. Principally, the application is a collection of Page
 * types, which are instantiated per-request and offered the request, in the
 * order they are added, until one accepts it and takes responsibility for
 * responding.
 *
 * @author Tim Boudreau
 */
public class Application implements Iterable<Page> {

    private static final Set<String> checkedTypes = Collections.synchronizedSet(new HashSet<String>());
    private final List<Object> pages = new ArrayList<>();
    @Inject
    private Dependencies deps;
    @Inject
    @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME)
    private ExecutorService exe;
    @Inject
    private RequestLogger logger;
    @Inject
    private ReentrantScope scope;
    private final Exception stackTrace = new Exception();
    @Inject
    private Pages runner;
    @Inject(optional = true)
    private ErrorInterceptor errorHandler;
    @Inject
    private Charset charset;

    @Inject(optional = true)
    @Named("acteur.debug")
    private boolean debug = true;

    /**
     * Create an application, optionally passing in an array of page types (you
     * can also call <code>add()</code> to add them).
     *
     * @param types
     */
    protected Application(Class<?>... types) {
        for (Class<?> type : types) {
            add((Class<? extends Page>) type);
        }
    }

    /**
     * Get the type of the built in help page class, which uses
     * Acteur.describeYourself() to generate a JSON description of all URLs the
     * application responnds to
     *
     * @return A page type
     */
    public static Class<? extends Page> helpPageType() {
        return HelpPage.class;
    }

    /**
     * Create an application
     *
     * @param types
     * @return
     */
    public static Application create(Class<?>... types) {
        return new Application(types);
    }

    /**
     * Get the <code>Scope</code> which is holds per-request state.
     *
     * @return
     */
    public ReentrantScope getRequestScope() {
        return scope;
    }

    ExecutorService getWorkerThreadPool() {
        return exe;
    }

    Map<String, Object> describeYourself() {
        Map<String, Object> m = new HashMap<>();

        try (QuietAutoCloseable c = this.scope.enter(new FakeHttpEventForHelp())) {
            Page old = Page.get();
            try {
                for (Page page : this) {
                    Page.set(page);
                    try (QuietAutoCloseable c1 = this.scope.enter(page)) {
                        page.describeYourself(m);
                    }
                }
            } finally {
                Page.set(old);
            }
        }
        return m;
    }

    private static class FakeHttpEventForHelp implements HttpEvent {

        @Override
        public Method getMethod() {
            return Method.GET;
        }

        @Override
        public String getHeader(CharSequence nm) {
            return null;
        }

        @Override
        public String getParameter(String param) {
            return null;
        }

        @Override
        public Path getPath() {
            return Path.parse("help");
        }

        @Override
        public <T> T getHeader(HeaderValueType<T> value) {
            return null;
        }

        @Override
        public Map<String, String> getParametersAsMap() {
            return new HashMap<>();
        }

        @Override
        public <T> T getParametersAs(Class<T> type) {
            return null;
        }

        @Override
        public Optional<Integer> getIntParameter(String name) {
            return Optional.absent();
        }

        @Override
        public Optional<Long> getLongParameter(String name) {
            return Optional.absent();
        }

        @Override
        public boolean isKeepAlive() {
            return false;
        }

        @Override
        public Channel getChannel() {
            return null;
        }

        @Override
        public HttpRequest getRequest() {
            return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/help");
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("0.0.0.0", 0);
        }

        @Override
        public <T> T getContentAsJSON(Class<T> type) throws IOException {
            return null;
        }

        @Override
        public ByteBuf getContent() throws IOException {
            return Unpooled.buffer();
        }

        @Override
        public OutputStream getContentAsStream() throws IOException {
            return Streams.nullOutputStream();
        }
    }

    /**
     * Add a subtype of Page which should be instantiated on demand when
     * responding to requests
     *
     * @param page A page
     */
    protected final void add(Class<? extends Page> page) {
        if ((page.getModifiers() & Modifier.ABSTRACT) != 0) {
            throw new ConfigurationError(page + " is abstract");
        }
        if (page.isLocalClass()) {
            throw new ConfigurationError(page + " is not a top-level class");
        }
        if (!Page.class.isAssignableFrom(page)) {
            throw new ConfigurationError(page + " is not a subclass of " + Page.class.getName());
        }
        assert checkConstructor(page);
        pages.add(page);
    }
    
    protected final void add(Page page) {
        pages.add(page);
    }

    static boolean checkConstructor(Class<?> type) {
        Checks.notNull("type", type);
        if (checkedTypes.contains(type.getName())) {
            return true;
        }
        boolean found = true;
        for (Constructor c : type.getDeclaredConstructors()) {
            if (c.getParameterTypes() == null || c.getParameterTypes().length == 0) {
                found = true;
            }
            @SuppressWarnings("unchecked")
            Inject inj = (Inject) c.getAnnotation(Inject.class);
            if (inj != null) {
                found = true;
            }
        }
        if (!found) {
            throw new ConfigurationError(type + " does not have an injectable constructor");
        }
        checkedTypes.add(type.getName());
        return true;
    }

    public String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Add any custom headers or other attributes - override to intercept all
     * requests.
     *
     * @param event
     * @param page
     * @param action
     * @param response
     * @return
     */
    protected HttpResponse decorateResponse(Event<?> event, Page page, Acteur action, HttpResponse response) {
        Headers.write(Headers.SERVER, getName(), response);
        Headers.write(Headers.DATE, new DateTime(), response);
        if (debug) {
            String pth = event instanceof HttpEvent ? ((HttpEvent) event).getPath().toString() : "";
            Headers.write(Headers.custom("X-Req-Path"), pth, response);
        }
        return response;
    }

    /**
     * Create a 404 response
     *
     * @param event
     * @return
     */
    protected HttpResponse createNotFoundResponse(Event<?> event) {
        ByteBuf buf = Unpooled.copiedBuffer("<html><head>"
                + "<title>Not Found</title></head><body><h1>Not Found</h1>"
                + event + " was not found\n<body></html>\n", charset);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_FOUND, buf);
        Headers.write(Headers.CONTENT_TYPE, MediaType.HTML_UTF_8.withCharset(charset), resp);
        Headers.write(Headers.CONTENT_LENGTH, (long) buf.writerIndex(), resp);
        Headers.write(Headers.CONTENT_LANGUAGE, Locale.ENGLISH, resp);
        Headers.write(Headers.CACHE_CONTROL, new CacheControl(CacheControlTypes.no_cache), resp);
        Headers.write(Headers.DATE, new DateTime(), resp);
        if (debug) {
            String pth = event instanceof HttpEvent ? ((HttpEvent) event).getPath().toString() : "";
            Headers.write(Headers.custom("X-Req-Path"), pth, resp);
        }
        return resp;
    }

    /**
     * Override to do any post-response tasks
     *
     * @param id The incrementing ID of the request, for logging purposes
     * @param event The event, usually HttpEvent
     * @param acteur The final acteur in the chain
     * @param page The page which took responsibility for answering the request
     * @param state The state produced by the last acteur
     * @param status The status code for the HTTP response
     * @param resp The HTTP response, from Netty's HTTP codec
     */
    protected void onAfterRespond(RequestID id, Event<?> event, Acteur acteur, Page page, State state, HttpResponseStatus status, HttpResponse resp) {
    }

    /**
     * Called before the response is sent
     *
     * @param id
     * @param event
     * @param status
     */
    protected void onBeforeRespond(RequestID id, Event<?> event, HttpResponseStatus status) {
        logger.onRespond(id, event, status);
    }

    /**
     * Called before an event is processed
     *
     * @param id The request id
     * @param event The event
     */
    protected void onBeforeEvent(RequestID id, Event<?> event) {
        logger.onBeforeEvent(id, event);
    }

    /**
     * Called when an error is encountered
     *
     * @param err
     */
    public final void internalOnError(Throwable err) {
        try {
            if (errorHandler != null) {
                errorHandler.onError(err);
            }
        } finally {
            onError(err);
        }
    }

    /**
     * Called when an exception is thrown
     *
     * @param err
     */
    public void onError(Throwable err) {
        err.printStackTrace(System.err);
    }

    /**
     * Called when an event occurs
     *
     * @param event
     * @param channel
     * @return
     */
    public CountDownLatch onEvent(final Event<?> event, final Channel channel) {
        //XXX get rid of channel param?
        // Create a new incremented id for this request
        final RequestID id = new RequestID();
        // Enter request scope with the id and the event
        return scope.run(new Invokable<Event<?>, CountDownLatch, RuntimeException>() {
            @Override
            public CountDownLatch run(Event<?> argument) {
                // Set the thread name
//                Thread.currentThread().setName(event.getPath() + " " + event.getRemoteAddress());
                onBeforeEvent(id, event);
                try {
                    return runner.onEvent(id, event, channel);
                } catch (Exception e) {
                    internalOnError(e);
                }
                return null;
            }
        }, event, id);
    }

    @SuppressWarnings({"unchecked"})
    Dependencies getDependencies() {
        if (deps == null) {
            try {
                new IllegalArgumentException("Initializing dependencies backwards.  This instance was not created by Guice? " + this, stackTrace).printStackTrace();
                deps = new Dependencies(SettingsBuilder.createDefault().buildMutableSettings(), new ServerModule(getClass()));
                deps.getInjector().getMembersInjector(Application.class).injectMembers(this);
            } catch (IOException ex) {
                throw new ConfigurationError(ex);
            }
        }
        return deps;
    }

    /**
     * Get the set of page instances, constructing them dynamically. Note that
     * this should be called inside the application scope, with any objects
     * which need to be available for injection available in the scope.
     *
     * @return An iterator
     */
    @Override
    public Iterator<Page> iterator() {
        return new InstantiatingIterators(deps).iterable(pages, Page.class).iterator();
    }

    protected void send404(RequestID id, Event<?> event, Channel channel) {
        HttpResponse response = createNotFoundResponse(event);
        onBeforeRespond(id, event, response.getStatus());
        ChannelFutureListener closer = !ResponseImpl.isKeepAlive(event) ? ChannelFutureListener.CLOSE : null;
        ChannelFuture fut = channel.writeAndFlush(response);
        if (closer != null) {
            fut.addListener(closer);
        }
    }
}
