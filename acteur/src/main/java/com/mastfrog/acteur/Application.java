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
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur.WrapperActeur;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.annotations.Precursors;
import com.mastfrog.acteur.base.Chain;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Invokable;
import com.mastfrog.util.perf.Benchmark;
import com.mastfrog.util.perf.Benchmark.Kind;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
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
import org.joda.time.Duration;
import org.openide.util.Exceptions;

/**
 * A web application. Principally, the application is a collection of Page
 * types, which are instantiated per-request and offered the request, in the
 * order they are added, until one accepts it and takes responsibility for
 * responding.
 *
 * @author Tim Boudreau
 */
public class Application extends Chain<Page> {

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
    @Named(CORSResource.SETTINGS_KEY_CORS_ALLOW_ORIGIN)
    String corsAllowOrigin = "*";

    @Inject(optional = true)
    @Named(CORSResource.SETTINGS_KEY_CORS_MAX_AGE_MINUTES)
    long corsMaxAgeMinutes = 5;

    @Inject(optional = true)
    @Named("acteur.debug")
    private boolean debug = true;

    private final RequestID.Factory ids = new RequestID.Factory();
    /**
     * Create an application, optionally passing in an array of page types (you
     * can also call <code>add()</code> to add them).
     *
     * @param types
     */
    protected Application(Class<?>... types) {
        super(Page.class);
        for (Class<?> type : types) {
            add((Class<? extends Page>) type);
        }
        init();
    }

    protected Application() {
        super(Page.class);
        init();
    }

    @Override
    protected Injector injector() {
        return deps.getInjector();
    }
    
    private void init() {
        Help help = getClass().getAnnotation(Help.class);
        if (help != null) {
            add(helpPageType());
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

    private boolean corsEnabled;

    public final boolean isDefaultCorsHandlingEnabled() {
        return corsEnabled;
    }

    final void enableDefaultCorsHandling() {
        if (!corsEnabled) {
            corsEnabled = true;
            add(CORSResource.class);
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
        for (Object o : this.pages) {
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
                } catch (Exception e) {
                    // A page may legitiimately be uninstantiable
                }
            } else if (o instanceof Page) {
                ((Page) o).describeYourself(m);
            }
        }
        return m;
    }

    @Override
    protected void onBeforeAdd(Class<? extends Page> itemType) {
        assert checkConstructor(itemType);
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
     * @deprecated Use onBeforeSendResponse instead
     * @return
     */
    @Deprecated
    protected HttpResponse decorateResponse(Event<?> event, Page page, Acteur action, HttpResponse response) {
        return response;
    }

    HttpResponse _decorateResponse(Event<?> event, Page page, Acteur action, HttpResponse response) {
        Headers.write(Headers.SERVER, getName(), response);
        Headers.write(Headers.DATE, new DateTime(), response);
        if (debug) {
            String pth = event instanceof HttpEvent ? ((HttpEvent) event).getPath().toString() : "";
            Headers.write(Headers.stringHeader("X-Req-Path"), pth, response);
            Headers.write(Headers.stringHeader("X-Acteur"), action.getClass().getName(), response);
            Headers.write(Headers.stringHeader("X-Page"), page.getClass().getName(), response);
        }
        if (corsEnabled && !response.headers().contains(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN)) {
            Headers.write(Headers.ACCESS_CONTROL_ALLOW_ORIGIN, corsAllowOrigin, response);
            if (!response.headers().contains(HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE)) {
                Headers.write(Headers.ACCESS_CONTROL_MAX_AGE, new Duration(corsMaxAgeMinutes), response);
            }
        }
        return decorateResponse(event, page, action, response);
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
     *RequestID
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
    @Benchmark(value = "httpEvents", publish = Kind.CALL_COUNT)
    private CountDownLatch onEvent(final Event<?> event, final Channel channel) {
        //XXX get rid of channel param?
        // Create a new incremented id for this request
        final RequestID id = ids.next();
        // Enter request scope with the id and the event
        // XXX is the scope entry here actually needed anymore?
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

    protected void send404(RequestID id, Event<?> event, Channel channel) {
        HttpResponse response = createNotFoundResponse(event);
        onBeforeRespond(id, event, response.getStatus());
        ChannelFutureListener closer = !ResponseImpl.isKeepAlive(event) ? ChannelFutureListener.CLOSE : null;
        ChannelFuture fut = channel.writeAndFlush(response);
        if (closer != null) {
            fut.addListener(closer);
        }
    }

    protected void onBeforeSendResponse(HttpResponseStatus status, Event<?> event, Response response, Acteur acteur, Page page) {
        // do nothing
    }
}
