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
import com.mastfrog.acteur.annotations.Early;
import com.mastfrog.acteur.debug.Probe;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.CacheControlTypes;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.GUICE_BINDING_DEFAULT_CONTEXT_OBJECTS;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.ErrorHandlers;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.InstantiatingIterators;
import com.mastfrog.function.misc.QuietAutoClosable;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.annotations.Setting;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.graal.annotation.Expose;
import com.mastfrog.mime.MimeType;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.ConfigurationError;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * A web application. Principally, the application is a collection of Page
 * types, which are instantiated per-request and offered the request, in the
 * order they are added, until one accepts it and takes responsibility for
 * responding.
 *
 * @author Tim Boudreau
 */
@Expose(methods = @Expose.MethodInfo(name = "control", parameterTypes = {}))
public class Application implements Iterable<Page> {

    private static final HeaderValueType<CharSequence> X_REQ_PATH = Headers.header(new AsciiString("X-Req-Path"));
    private static final HeaderValueType<CharSequence> X_REQ_ID = Headers.header(new AsciiString("X-Req-ID"));
    private static final HeaderValueType<CharSequence> X_ACTEUR = Headers.header(new AsciiString("X-Acteur"));
    private static final HeaderValueType<CharSequence> X_PAGE = Headers.header(new AsciiString("X-Page"));
    private static final Set<String> checkedTypes = Collections.synchronizedSet(new HashSet<>());

    /**
     * Optional setting which, if set, will be used in the Server: header of
     * responses.
     */
    @Setting(value = "Optional setting which, if set, will be used in the `Server:` header of responses.")
    public static final String SETTINGS_KEY_APPLICATION_NAME = "application.name";
    /**
     * System property that, if set to true, enables various sorts of console
     * debug logging.
     */
    public static final String SYSTEM_PROPERTY_ACTEUR_DEBUG = "acteur.debug";
    private final List<Object> pages = new ArrayList<>();
    @Inject
    private Dependencies deps;
    @Inject
    private RequestLogger logger;
    @Inject
    private ReentrantScope scope;
    private final Exception stackTrace = new Exception();
    @Inject
    private PagesImpl2 runner;
    @Inject(optional = true)
    private ErrorInterceptor errorHandler;
    @Inject
    private ErrorHandlers errorHandlers;
    @Inject
    Charset charset;
    @Inject
    CORSResponseDecorator corsDecorator;
    @Inject(optional = true)
    private ResponseDecorator responseDecorator;

    @Inject(optional = true)
    @Named(SETTINGS_KEY_APPLICATION_NAME)
    String name;

    private final boolean debug = Boolean.getBoolean(SYSTEM_PROPERTY_ACTEUR_DEBUG);

    @Inject(optional = true)
    @Named(GUICE_BINDING_DEFAULT_CONTEXT_OBJECTS)
    private Object[] defaultContextObjects;

    @Inject
    Probe probe;

    @Inject(optional = true)
    OnBeforeEvent onBeforeEvent;

    @Inject
    private InstantiatingIterators iterators;
    private boolean checkedEarlyHelp;
    private final RequestID.Factory ids = new RequestID.Factory();

    private final List<Object> earlyPages = new ArrayList<>(10);

    private final PathFilters filters = PathFilters.create(this::dependenciesUnsafe);
    @Inject(optional = true)
    FailureResponseFactory failureResponses;
    final ChannelFutureListener errorLoggingListener = future -> {
        if (!future.isSuccess() && future.cause() != null) {
            internalOnError(future.cause());
        }
    };

    /**
     * Create an application, optionally passing in an array of page types (you
     * can also call <code>add()</code> to add them).
     *
     * @param types
     */
    @SuppressWarnings("unchecked")
    protected Application(Class<?>... types) {
        this();
        for (Class<?> type : types) {
            add((Class<? extends Page>) type);
        }
    }

    protected Application() {
        Help help = getClass().getAnnotation(Help.class);
        if (help != null) {
            add(helpPageType());
        }
    }

    public boolean hasEarlyPages() {
        return !this.earlyPages.isEmpty();
    }

    public boolean isEarlyPageMatch(HttpRequest req) {
        return filters.isEarlyPageMatch(req);
    }

    List<Object> rawPages() {
        return this.pages;
    }

    /**
     * Get the type of the built in help page class, which uses
     * Acteur.describeYourself() and annotations to generate a JSON description
     * of all URLs the application responds to
     *
     * @return A page type
     */
    public static Class<? extends Page> helpPageType() {
        return HelpPage.class;
    }

    private boolean corsEnabled;

    public final boolean isDefaultCorsHandlingEnabled() {
        return corsEnabled;
    }

    protected final void enableDefaultCorsHandling() {
        if (!corsEnabled) {
            corsEnabled = true;
            if (!this.earlyPages.isEmpty()) {
                earlyPages.add(CORSResource.class);
            }
            pages.add(0, CORSResource.class);
            filters.addNormalPage(CORSResource.class);
        }
    }

    private final ApplicationControl control = new ApplicationControl() {
        @Override
        public void enableDefaultCorsHandling() {
            Application.this.enableDefaultCorsHandling();
        }

        @Override
        public CountDownLatch onEvent(Event<?> event, Channel channel) {
            return Application.this.onEvent(event, channel);
        }

        @Override
        @SuppressWarnings("ThrowableResultIgnored")
        public void internalOnError(Throwable err) {
            if (err != null) {
                Application.this.internalOnError(err);
            }
        }

        public ChannelFuture logFailure(ChannelFuture future) {
            Checks.notNull("future", future).addListener(Application.this.errorLoggingListener);
            return future;
        }
    };

    ApplicationControl control() {
        return control;
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

    @Inject
    private HelpGenerator helpGenerator;

    Map<String, Object> describeYourself() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> allPagesAndPageTypes = new ArrayList<>(this.earlyPages);
        allPagesAndPageTypes.addAll(this.pages);
        helpGenerator.generate(this, allPagesAndPageTypes, m);
        return m;
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
        if (page.getAnnotation(Early.class) != null) {
            filters.addEarlyPage(page);
            earlyPages.add(page);
        } else {
            filters.addNormalPage(page);
            pages.add(page);
        }
    }

    @SuppressWarnings("unchecked")
    protected final void add(Page page) {
        if (page.getClass().getAnnotation(Early.class) != null) {
            filters.addEarlyPage(page);
            earlyPages.add(page);
        } else {
            filters.addNormalPage(page);
            pages.add(page);
        }
    }

    static boolean checkConstructor(Class<?> type) {
        Checks.notNull("type", type);
        if (checkedTypes.contains(type.getName())) {
            return true;
        }
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        boolean found = constructors.length == 0;

        for (Constructor c : constructors) {
            if (c.getParameterTypes() == null || c.getParameterTypes().length == 0) {
                found = true;
            } else {
                if (c.getAnnotation(Inject.class) != null || c.getAnnotation(javax.inject.Inject.class) != null) {
                    found = true;
                }
            }
            if (found) {
                break;
            }
        }
        if (!found) {
            throw new ConfigurationError(type + " does not have an injectable constructor");
        }
        checkedTypes.add(type.getName());
        return true;
    }

    public String getName() {
        return name == null ? getClass().getSimpleName() : name;
    }

    HttpResponse _decorateResponse(RequestID id, Event<?> event, Page page, Acteur action, HttpResponse response) {
        Headers.write(Headers.SERVER, getName(), response);
        Headers.write(Headers.DATE, ZonedDateTime.now(), response);
        if (debug) {
            String pth = event instanceof HttpEvent ? ((HttpEvent) event).path().toString() : "-";
            if (pth.isEmpty()) {
                pth = "/";
            }
            Headers.write(X_REQ_PATH, pth, response);
            Headers.write(X_ACTEUR, action.getClass().getName(), response);
            Headers.write(X_PAGE, page.getClass().getName(), response);
        }
        Headers.write(X_REQ_ID, id.stringValue(), response);
        if (corsEnabled) {
            corsDecorator.decorateApplicationResponse(response, page);
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
        if (failureResponses != null) {
            return failureResponses.createNotFoundResponse(event);
        }
        String msg = "<html><head>"
                + "<title>Not Found</title></head><body><h1>Not Found</h1>"
                + event + " was not found\n<body></html>\n";
        ByteBuf buf = event.channel().alloc().ioBuffer(msg.length());
        buf.touch("application-create-not-found-response");
        buf.writeBytes(msg.getBytes(charset));
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_FOUND, buf);
        Headers.write(Headers.CONTENT_TYPE, MimeType.HTML_UTF_8.withCharset(charset), resp);
        Headers.write(Headers.CONTENT_LENGTH, buf.writerIndex(), resp);
        Headers.write(Headers.CONTENT_LANGUAGE, Locale.ENGLISH, resp);
        Headers.write(Headers.CACHE_CONTROL, new CacheControl(CacheControlTypes.no_cache), resp);
        Headers.write(Headers.DATE, ZonedDateTime.now(), resp);
        if (debug) {
            String pth = event instanceof HttpEvent ? ((HttpEvent) event).path().toString() : "";
            Headers.write(X_REQ_PATH, pth, resp);
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
     * Called before the response is sent RequestID
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
    final void internalOnError(Throwable err) {
        Checks.notNull("err", err);
        try {
            errorHandlers.onError(err);
        } finally {
            if (errorHandler != null) {
                errorHandler.onError(err);
            }
            onError(err);
        }
    }

    /**
     * Called when an exception is thrown
     *
     * @param err
     * @deprecated Implement ErrorHandler and bind it as an eager singleton
     */
    @Deprecated
    public void onError(Throwable err) {
    }

    /**
     * Called when an event occurs
     *
     * @param event
     * @param channel
     * @return
     */
    private CountDownLatch onEvent(final Event<?> event, final Channel channel) {
        assert scope != null : "Scope is null - Application members not injected?";
        // Create a new incremented id for this request
        final RequestID id = ids.next();
        probe.onBeforeProcessRequest(id, event);

        Object[] scopeContents = onBeforeEvent == null ? new Object[]{event, id}
                : onBeforeEvent.onBeforeEvent(event, channel, id);

        // Enter request scope with the id and the event
        try (QuietAutoClosable cl = scope.enter(scopeContents)) {
            onBeforeEvent(id, event);
            return runner.onEvent(id, event, channel, defaultContextObjects);
        } catch (Exception e) {
            internalOnError(e);
            CountDownLatch latch = new CountDownLatch(1);
            latch.countDown();
            return latch;
        }
    }

    Dependencies dependenciesUnsafe() {
        return deps;
    }

    @SuppressWarnings({"unchecked", "ThrowableInstanceNotThrown", "ThrowableInstanceNeverThrown"})
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

    private List<Object> filter(PagePathAndMethodFilter filter, HttpEvent evt) {
        HttpRequest req = evt.request();
        List<Object> filtered = new ArrayList<>(filter.listFor(req));
        filtered.sort(HttpCallOrOrderedComparator.INSTANCE);
        return filtered;
    }

    Iterator<Page> iterator(HttpEvent evt) {
        List<Object> all = filter(filters.normalPages(), evt);
        return all.isEmpty() ? Collections.emptyIterator()
                : iterators.iterable(all, Page.class).iterator();
    }

    Iterator<Page> earlyPagesIterator(HttpEvent evt) {
        // This is a hack
        if (!checkedEarlyHelp && deps.getInstance(Settings.class).getBoolean("help.early", false)) {
            checkedEarlyHelp = true;
            earlyPages.add(HelpPage.class);
            filters.earlyPages().addHelp(deps.getInstance(Settings.class).getString(Help.HELP_URL_PATTERN_SETTINGS_KEY, "^help$"));
        }
        List<Object> all = filter(filters.earlyPages(), evt);
        return all.isEmpty() ? Collections.emptyIterator() : iterators.iterable(all, Page.class).iterator();
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
        return iterators.iterable(pages, Page.class).iterator();
    }

    Iterator<Page> earlyPagesIterator() {
        // This is a hack
        if (!checkedEarlyHelp && deps.getInstance(Settings.class).getBoolean("help.early", false)) {
            checkedEarlyHelp = true;
            earlyPages.add(HelpPage.class);
            filters.earlyPages()
                    .addHelp(deps.getInstance(Settings.class)
                            .getString(Help.HELP_URL_PATTERN_SETTINGS_KEY,
                                    "^help$"));
        }
        return iterators.iterable(earlyPages, Page.class).iterator();
    }

    protected void send404(RequestID id, Event<?> event, Channel channel) {
        HttpResponse response = createNotFoundResponse(event);

        onBeforeRespond(id, event, response.status());
        if (responseDecorator != null) {
            try {
                try (QuietAutoClosable cl = Page.set(DummyNotFoundPage.INSTANCE)) {
                    _onBeforeSendResponse(HttpResponseStatus.NOT_FOUND, event,
                            new NotFoundResponseWrapper(response),
                            DummyNotFoundActeur.INSTANCE, DummyNotFoundPage.INSTANCE);
                }
            } catch (Throwable tt) {
                tt.printStackTrace();
            }
        }
        probe.onFallthrough(id, event);
        ChannelFuture fut = channel.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, response.headers()));
        if (response instanceof FullHttpResponse) {
            fut = channel.writeAndFlush(new DefaultLastHttpContent(((FullHttpResponse) response).content()));
        } else {
            fut = channel.writeAndFlush(DefaultLastHttpContent.EMPTY_LAST_CONTENT);
        }
        boolean keepAlive = event instanceof HttpEvent && ((HttpEvent) event).requestsConnectionStayOpen();
        if (!keepAlive) {
            fut.addListener(ChannelFutureListener.CLOSE);
        }
    }

    void _onBeforeSendResponse(HttpResponseStatus status, Event<?> event, Response response, Acteur acteur, Page page) {
        try {
            if (responseDecorator != null) {
                responseDecorator.onBeforeSendResponse(this, status, event, response, acteur, page);
            }
            onBeforeSendResponse(status, event, response, acteur, page);
        } catch (Throwable e) {
            this.internalOnError(e);
        }
    }

    protected void onBeforeSendResponse(HttpResponseStatus status, Event<?> event, Response response, Acteur acteur, Page page) {
        // do nothing
    }

    private static final class DummyNotFoundPage extends Page {

        private static final DummyNotFoundPage INSTANCE = new DummyNotFoundPage();

        DummyNotFoundPage() {
            add(DummyNotFoundActeur.class);
        }
    }

    private static final class DummyNotFoundActeur extends Acteur {

        private static final DummyNotFoundActeur INSTANCE = new DummyNotFoundActeur();

        DummyNotFoundActeur() {
            notFound();
        }
    }

    private static final class NotFoundResponseWrapper extends Response {

        private final HttpResponse resp;

        public NotFoundResponseWrapper(HttpResponse resp) {
            this.resp = resp;
        }

        @Override
        public <T> Response add(HeaderValueType<T> headerType, T value) {
            resp.headers().add(headerType.name(), headerType.toCharSequence(value));
            return this;
        }

        @Override
        public Response content(Object message) {
            throw new UnsupportedOperationException("Decorator cannot set content on not found response.");
        }

        @Override
        public Response status(HttpResponseStatus status) {
            resp.setStatus(status);
            return this;
        }

        @Override
        public Response contentWriter(ChannelFutureListener listener) {
            throw new UnsupportedOperationException("Decorator cannot set content on not found response.");
        }

        @Override
        public Response contentWriter(ResponseWriter writer) {
            throw new UnsupportedOperationException("Decorator cannot set content on not found response.");
        }

        @Override
        public Response chunked(boolean chunked) {
            throw new UnsupportedOperationException("Decorator cannot set chunked on not found response.");
        }

        @Override
        public Response delayedBy(Duration delay) {
            throw new UnsupportedOperationException("Decorator cannot set delay on not found response.");
        }

        @Override
        protected <T> T get(HeaderValueType<T> header) {
            String s = resp.headers().get(header.name());
            if (s != null) {
                return header.convert(s);
            }
            return null;
        }

    }
}
