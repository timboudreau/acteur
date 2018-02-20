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

import com.mastfrog.acteur.headers.Headers;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur.WrapperActeur;
import com.mastfrog.acteur.annotations.Concluders;
import com.mastfrog.acteur.annotations.Early;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.annotations.Precursors;
import com.mastfrog.acteur.debug.Probe;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.GUICE_BINDING_DEFAULT_CONTEXT_OBJECTS;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.ErrorHandlers;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.InstantiatingIterators;
import com.mastfrog.parameters.Param;
import com.mastfrog.parameters.Params;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Checks;
import com.mastfrog.util.perf.Benchmark;
import com.mastfrog.util.perf.Benchmark.Kind;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import org.netbeans.validation.api.Validator;
import org.netbeans.validation.api.builtin.stringvalidation.StringValidators;
import org.openide.util.Exceptions;

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
    private PagesImpl2 runner;
    @Inject(optional = true)
    private ErrorInterceptor errorHandler;
    @Inject
    private ErrorHandlers errorHandlers;
    @Inject
    private Charset charset;
    @Inject
    CORSResponseDecorator corsDecorator;
    @Inject(optional = true)
    private ResponseDecorator responseDecorator;

    @Inject(optional = true)
    @Named("application.name")
    String name;

    @Inject(optional = true)
    @Named("acteur.debug")
    private boolean debug = false;

    @Inject(optional = true)
    @Named(GUICE_BINDING_DEFAULT_CONTEXT_OBJECTS)
    private Object[] defaultContextObjects;

    @Inject
    Probe probe;

    private PagePathAndMethodFilter earlyPageMatcher;
    private List<Object> earlyPages = new ArrayList<>(10);

    private PagePathAndMethodFilter normalPageMatcher = new PagePathAndMethodFilter();
    private List<Object> normalPages = new ArrayList<>(30);

    private final RequestID.Factory ids = new RequestID.Factory();

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
        boolean result = earlyPageMatcher != null && earlyPageMatcher.match(req);
        return result;
    }

    List<Object> rawPages() {
        return this.pages;
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

    private boolean corsEnabled;

    public final boolean isDefaultCorsHandlingEnabled() {
        return corsEnabled;
    }

    protected final void enableDefaultCorsHandling() {
        if (!corsEnabled) {
            corsEnabled = true;
            pages.add(0, CORSResource.class);
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
        public void internalOnError(Throwable err) {
            Checks.notNull("err", err);
            Application.this.internalOnError(err);
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

    ExecutorService getWorkerThreadPool() {
        return exe;
    }

    private static String deConstantNameify(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
            } else {
                if (capitalize) {
                    c = Character.toUpperCase(c);
                    capitalize = false;
                } else {
                    c = Character.toLowerCase(c);
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void introspectAnnotation(Annotation a, Map<String, Object> into) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (a instanceof HttpCall) {
            return;
        }
        if (a instanceof Precursors) {
            Precursors p = (Precursors) a;
            for (Class<?> t : p.value()) {
                for (Annotation anno : t.getAnnotations()) {
                    introspectAnnotation(anno, into);
                }
            }
        } else if (a instanceof Concluders) {
            Concluders c = (Concluders) a;
            for (Class<?> t : c.value()) {
                for (Annotation anno : t.getAnnotations()) {
                    introspectAnnotation(anno, into);
                }
            }
        } else if (a instanceof Params) {
            Params p = (Params) a;
            for (Param par : p.value()) {
                String name = par.value();
                Map<String, Object> desc = new LinkedHashMap<>();
                desc.put("type", par.type().toString());
                if (!par.defaultValue().isEmpty()) {
                    desc.put("Default value", par.defaultValue());
                }
                if (!par.example().isEmpty()) {
                    desc.put("Example", par.example());
                }
                desc.put("required", par.required());
                List<String> constraints = new LinkedList<>();
                for (StringValidators validator : par.constraints()) {
                    constraints.add(deConstantNameify(validator.name()));
                }
                for (Class<? extends Validator<String>> c : par.validators()) {
                    Description des = c.getAnnotation(Description.class);
                    if (des == null) {
                        constraints.add(c.getSimpleName());
                    } else {
                        constraints.add(des.value());
                    }
                }
                if (!constraints.isEmpty()) {
                    desc.put("constraints", constraints);
                }
                into.put(name, desc);
            }
        } else {
            Class<? extends Annotation> type = a.annotationType();
            for (java.lang.reflect.Method m : type.getMethods()) {
                switch (m.getName()) {
                    case "annotationType":
                    case "toString":
                    case "hashCode":
                        break;
                    default:
                        if (m.getParameterTypes().length == 0 && m.getReturnType() != null) {
                            into.put(m.getName(), m.invoke(a));
                        }
                }
            }
            if (type.getAnnotation(Description.class) != null) {
                Description d = type.getAnnotation(Description.class);
                into.put("Description", d.value());
            }
        }
    }

    Map<String, Object> describeYourself() {
        Map<String, Object> m = new HashMap<>();
        List<Object> allPagesAndPageTypes = new ArrayList<>(this.earlyPages);
        allPagesAndPageTypes.addAll(this.pages);
        for (Object o : allPagesAndPageTypes) {
            if (o instanceof Class<?>) {
                Class<?> type = (Class<?>) o;
                Map<String, Object> pageDescription = new HashMap<>();
                String typeName = type.getName();
                if (typeName.endsWith(HttpCall.GENERATED_SOURCE_SUFFIX)) {
                    typeName = typeName.substring(0, typeName.length() - HttpCall.GENERATED_SOURCE_SUFFIX.length());
                }
                pageDescription.put("type", type.getName());
                String className = type.getSimpleName();
                if (className.endsWith(HttpCall.GENERATED_SOURCE_SUFFIX)) {
                    className = className.substring(0, className.length() - HttpCall.GENERATED_SOURCE_SUFFIX.length());
                }
                m.put(className, pageDescription);
                Annotation[] l = type.getAnnotations();
                for (Annotation a : l) {
                    if (a instanceof HttpCall) {
                        continue;
                    }
                    Map<String, Object> annoDescription = new HashMap<>();
                    pageDescription.put(a.annotationType().getSimpleName(), annoDescription);
                    try {
                        introspectAnnotation(a, annoDescription);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    if (annoDescription.size() == 1 && "value".equals(annoDescription.keySet().iterator().next())) {
                        pageDescription.put(a.annotationType().getSimpleName(), annoDescription.values().iterator().next());
                    }
                }
                try {
                    Page p = (Page) deps.getInstance(type);
                    p.application = this;
                    for (Object acteur : p.contents()) {
                        Class<?> at = null;
                        if (acteur instanceof Acteur.WrapperActeur) {
                            at = ((WrapperActeur) acteur).type();
                        } else if (acteur instanceof Class<?>) {
                            at = (Class<?>) acteur;
                        }
                        if (at != null) {
                            Map<String, Object> callFlow = new HashMap<>();
                            for (Annotation a1 : at.getAnnotations()) {
                                introspectAnnotation(a1, callFlow);
                            }
                            if (!className.equals(at.getSimpleName())) {
                                if (!callFlow.isEmpty()) {
                                    pageDescription.put(at.getSimpleName(), callFlow);
                                }
                            }
                        } else if (acteur instanceof Acteur) {
                            Map<String, Object> callFlow = new HashMap<>();
                            for (Annotation a1 : acteur.getClass().getAnnotations()) {
                                introspectAnnotation(a1, callFlow);
                            }
                            ((Acteur) acteur).describeYourself(callFlow);
                            if (!callFlow.isEmpty()) {
                                pageDescription.put(acteur.toString(), callFlow);
                            }
                        }
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    // A page may legitiimately be uninstantiable
                }
            } else if (o instanceof Page) {
                ((Page) o).describeYourself(m);
            }
        }
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
            if (earlyPageMatcher == null) {
                earlyPageMatcher = new PagePathAndMethodFilter();
            }
            earlyPageMatcher.add(page);
            earlyPages.add(page);
        } else {
            normalPageMatcher.add(page);
            normalPages.add(page);
            pages.add(page);
        }
    }

    @SuppressWarnings("unchecked")
    protected final void add(Page page) {
        if (page.getClass().getAnnotation(Early.class) != null) {
            if (earlyPageMatcher == null) {
                earlyPageMatcher = new PagePathAndMethodFilter();
            }
            earlyPageMatcher.add(page);
            earlyPages.add(page);
        } else {
            normalPageMatcher.add(page);
            normalPages.add(page);
            pages.add(page);
        }
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
        return name == null ? getClass().getSimpleName() : name;
    }

    /**
     * Add any custom headers or other attributes - override to intercept all
     * requests.
     *
     * @param event
     * @param page
     * @param action
     * @param response
     * @deprecated Use onBeforeSendResponse instead
     * @return
     */
    @Deprecated
    protected HttpResponse decorateResponse(Event<?> event, Page page, Acteur action, HttpResponse response) {
        return response;
    }

    private static final HeaderValueType<CharSequence> X_REQ_PATH = Headers.header(new AsciiString("X-Req-Path"));
    private static final HeaderValueType<CharSequence> X_ACTEUR = Headers.header(new AsciiString("X-Acteur"));
    private static final HeaderValueType<CharSequence> X_PAGE = Headers.header(new AsciiString("X-Page"));

    HttpResponse _decorateResponse(Event<?> event, Page page, Acteur action, HttpResponse response) {
        Headers.write(Headers.SERVER, getName(), response);
        Headers.write(Headers.DATE, ZonedDateTime.now(), response);
        if (debug) {
            String pth = event instanceof HttpEvent ? ((HttpEvent) event).path().toString() : "";
            Headers.write(X_REQ_PATH, pth, response);
            Headers.write(X_ACTEUR, action.getClass().getName(), response);
            Headers.write(X_PAGE, page.getClass().getName(), response);
        }
        if (corsEnabled) {
            corsDecorator.decorateApplicationResponse(response);
        }
        return decorateResponse(event, page, action, response);
    }

    @Inject(optional = true)
    FailureResponseFactory failureResponses;

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
        buf.writeBytes(msg.getBytes(charset));
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_FOUND, buf);
        Headers.write(Headers.CONTENT_TYPE, MediaType.HTML_UTF_8.withCharset(charset), resp);
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
    @Benchmark(value = "uncaughtExceptions", publish = Kind.CALL_COUNT)
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
    public void onError(Throwable err) {
    }

    /**
     * Called when an event occurs
     *
     * @param event
     * @param channel
     * @return
     */
    @Benchmark(value = "httpEvents", publish = Kind.CALL_COUNT)
    private CountDownLatch onEvent(final Event<?> event, final Channel channel) {
        // Create a new incremented id for this request
        final RequestID id = ids.next();
        probe.onBeforeProcessRequest(id, event);
        // Enter request scope with the id and the event
        try (QuietAutoCloseable cl = scope.enter(event, id)) {
            onBeforeEvent(id, event);
            return runner.onEvent(id, event, channel, defaultContextObjects);
        } catch (Exception e) {
            internalOnError(e);
            CountDownLatch latch = new CountDownLatch(1);
            latch.countDown();
            return latch;
        }
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

    Iterator<Page> iterator(HttpEvent evt) {
        HttpRequest req = evt.request();
        List<Object> filtered = new ArrayList<>(normalPageMatcher.listFor(req));
        filtered.sort((a, b) -> {
            int ai, bi;
            Class<?> ca, cb;
            HttpCall ac, bc;
            if (a instanceof Page) {
                ca = a.getClass();
            } else {
                ca = (Class<?>) a;
            }
            if (b instanceof Page) {
                cb = b.getClass();
            } else {
                cb = (Class<?>) b;
            }
            ac = ca.getAnnotation(HttpCall.class);
            bc = cb.getAnnotation(HttpCall.class);
            if (ac == null && bc == null) {
                return 0;
            }
            ai = ac == null ? 0 : ac.order();
            bi = bc == null ? 0 : bc.order();

            return ai == bi ? 0 : ai > bi ? 1 : -1;
        });
//        System.out.println("REGULAR PAGES " + evt.path() + ": " + Strings.join(',', pages));
//        System.out.println("FILTERED PAGES " + evt.path() + ": " + Strings.join(',', filtered));
        return iterators.iterable(filtered, Page.class).iterator();
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
        return iterators.iterable(earlyPages, Page.class).iterator();
    }

    @Inject
    private InstantiatingIterators iterators;

    protected void send404(RequestID id, Event<?> event, Channel channel) {
        HttpResponse response = createNotFoundResponse(event);
        onBeforeRespond(id, event, response.status());
        probe.onFallthrough(id, event);
        ChannelFuture fut = channel.writeAndFlush(response);
        boolean keepAlive = event instanceof HttpEvent ? ((HttpEvent) event).requestsConnectionStayOpen() : false;
        if (keepAlive) {
            fut.addListener(ChannelFutureListener.CLOSE);
        }
    }

    void _onBeforeSendResponse(HttpResponseStatus status, Event<?> event, Response response, Acteur acteur, Page page) {
        try {
            if (responseDecorator != null) {
                responseDecorator.onBeforeSendResponse(this, status, event, response, acteur, page);
            }
            onBeforeSendResponse(status, event, response, acteur, page);
        } catch (Exception e) {
            this.internalOnError(e);
        }
    }

    protected void onBeforeSendResponse(HttpResponseStatus status, Event<?> event, Response response, Acteur acteur, Page page) {
        // do nothing
    }
}
