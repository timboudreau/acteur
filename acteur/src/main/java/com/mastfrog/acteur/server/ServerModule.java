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
package com.mastfrog.acteur.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.ImplicitBindings;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluator;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import com.mastfrog.acteur.server.ServerModule.TF;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.parameters.KeysValues;
import com.mastfrog.settings.MutableSettings;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.treadmill.Treadmill;
import com.mastfrog.util.Codec;
import com.mastfrog.util.ConfigurationError;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.netbeans.validation.api.InvalidInputException;

/**
 *
 * @author Tim Boudreau
 */
@Defaults("realm=Users")
public class ServerModule<A extends Application> extends AbstractModule {

    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests.
     */
    public static final String BACKGROUND_THREAD_POOL_NAME = "background";
    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests.
     */
    public static final String WORKER_THREAD_POOL_NAME = "workers";
    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests, but wrappered so that all runnables are run within the
     * application's request scope and have whatever context they were submitted
     * with.
     */
    public static final String SCOPED_WORKER_THREAD_POOL_NAME = "scopedWorkers";
    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests, but wrappered so that all runnables are run within the
     * application's request scope and have whatever context they were submitted
     * with.
     */
    public static final String SCOPED_BACKGROUND_THREAD_POOL_NAME = "scopedBackground";

    /**
     * Property name for setting which byte buffer allocator Netty uses (heap,
     * direct, pooled).
     */
    public static final String BYTEBUF_ALLOCATOR_SETTINGS_KEY = "acteur.bytebuf.allocator";
    /**
     * Property value for telling the server to use the direct byte buffer
     * allocator (non-heap).
     */
    public static final String DIRECT_ALLOCATOR = "direct";
    /**
     * Property value for telling the server to use the heap byte buffer
     * allocator.
     */
    public static final String HEAP_ALLOCATOR = "heap";
    /**
     * Property value for telling the server to use the pooled byte buffer
     * allocator.
     */
    public static final String POOLED_ALLOCATOR = "pooled";
    /**
     * Property value for telling the server to use the heap or direct byte
     * buffer allocator as decided by Netty's PlatformDependent class.
     */
    public static final String DIRECT_OR_HEAP_BY_PLATFORM = "directOrHeap";
    /**
     * The default allocator to use if none is specified
     */
    public static final String DEFAULT_ALLOCATOR = POOLED_ALLOCATOR;
    /**
     * Settings key for the nnumber of worker threads to use.
     */
    public static final String WORKER_THREADS = "workerThreads";
    /**
     * Number of event threads
     */
    public static final String EVENT_THREADS = "eventThreads";
    /**
     * Number of background thread pool threads. The background thread pool is
     * used by a few things which chunk responses.
     */
    public static final String BACKGROUND_THREADS = "backgroundThreads";
    /**
     * The port to run on
     */
    public static final String PORT = "port";

    /**
     * If true, the return value of Event.getRemoteAddress() will prefer the
     * headers X-Forwarded-For or X-Real-IP if present, so that running an
     * acteur application behind a reverse proxy does not mask the actual IP
     * address.
     */
    public static final String SETTINGS_KEY_DECODE_REAL_IP = "decodeRealIP";
    public static final String SETTINGS_KEY_CORS_ENABLED = "cors.enabled";
    protected final Class<A> appType;
    protected final ReentrantScope scope = new ReentrantScope();
    private final int eventThreads;
    private final int workerThreads;
    private final int backgroundThreads;
    private final List<Module> otherModules = new ArrayList<>();

    public ServerModule(Class<A> appType, int workerThreadCount, int eventThreadCount, int backgroundThreadCount) {
        if (!Application.class.isAssignableFrom(appType)) {
            throw new ClassCastException(appType.getName() + " is not a subclass of " + Application.class.getName());
        }
        this.appType = appType;
        this.workerThreads = workerThreadCount;
        this.eventThreads = eventThreadCount;
        this.backgroundThreads = backgroundThreadCount;
    }

    public ServerModule(Class<A> appType) {
        this(appType, -1, -1, -1);
    }

    public final ReentrantScope applicationScope() {
        return scope;
    }

    @Override
    protected void configure() {
        bind(Server.class).to(ServerImpl.class);
        bind(ReentrantScope.class).toInstance(scope);
        bind(Application.class).to(appType).asEagerSingleton();
        bind(ChannelHandler.class).to(UpstreamHandlerImpl.class);
        bind(new CISC()).to(PipelineFactoryImpl.class);
        bind(ServerBootstrap.class).toProvider(new ServerBootstrapProvider(
                binder().getProvider(Settings.class),
                binder().getProvider(ByteBufAllocator.class)));

        scope.bindTypes(binder(), Event.class, HttpEvent.class,
                Page.class, BasicCredentials.class, Closables.class);

        ImplicitBindings implicit = appType.getAnnotation(ImplicitBindings.class);
        if (implicit != null) {
            scope.bindTypes(binder(), implicit.value());
        }
        // Acteurs can ask for a Deferral to pause execution while some
        // other operation completes, such as making an external HTTP request
        // to another server
        scope.bindTypes(binder(), Treadmill.Deferral.class);

        Provider<Application> appProvider = binder().getProvider(Application.class);
        Provider<ApplicationControl> appControlProvider = binder().getProvider(ApplicationControl.class);
        Provider<Settings> set = binder().getProvider(Settings.class);

        TF eventThreadFactory = new TF(EVENT_THREADS, appControlProvider);
        TF workerThreadFactory = new TF(WORKER_THREADS, appControlProvider);
        TF backgroundThreadFactory = new TF(BACKGROUND_THREAD_POOL_NAME, appControlProvider);

        bind(ThreadGroup.class).annotatedWith(Names.named(BACKGROUND_THREAD_POOL_NAME)).toInstance(backgroundThreadFactory.tg);
        bind(ThreadGroup.class).annotatedWith(Names.named(WORKER_THREADS)).toInstance(workerThreadFactory.tg);
        bind(ThreadGroup.class).annotatedWith(Names.named(EVENT_THREADS)).toInstance(eventThreadFactory.tg);

        ThreadCount workerThreadCount = new ThreadCount(set, 8, workerThreads, WORKER_THREADS);
        ThreadCount eventThreadCount = new ThreadCount(set, 8, eventThreads, EVENT_THREADS);
        ThreadCount backgroundThreadCount = new ThreadCount(set, 128, backgroundThreads, BACKGROUND_THREADS);

        bind(ThreadCount.class).annotatedWith(Names.named(EVENT_THREADS)).toInstance(eventThreadCount);
        bind(ThreadCount.class).annotatedWith(Names.named(WORKER_THREADS)).toInstance(workerThreadCount);
        bind(ThreadCount.class).annotatedWith(Names.named(BACKGROUND_THREAD_POOL_NAME)).toInstance(backgroundThreadCount);

        bind(ThreadFactory.class).annotatedWith(Names.named(WORKER_THREADS)).toInstance(workerThreadFactory);
        bind(ThreadFactory.class).annotatedWith(Names.named(EVENT_THREADS)).toInstance(eventThreadFactory);
        bind(ThreadFactory.class).annotatedWith(Names.named(BACKGROUND_THREAD_POOL_NAME)).toInstance(backgroundThreadFactory);

        Provider<ExecutorService> workerProvider
                = new ExecutorServiceProvider(workerThreadFactory, workerThreadCount);
        Provider<ExecutorService> backgroundProvider
                = new ExecutorServiceProvider(backgroundThreadFactory, backgroundThreadCount);

        bind(ExecutorService.class).annotatedWith(Names.named(
                WORKER_THREAD_POOL_NAME)).toProvider(workerProvider);

        bind(ExecutorService.class).annotatedWith(Names.named(
                BACKGROUND_THREAD_POOL_NAME)).toProvider(backgroundProvider);

        bind(ExecutorService.class).annotatedWith(Names.named(
                SCOPED_WORKER_THREAD_POOL_NAME)).toProvider(
                        new WrappedWorkerThreadPoolProvider(workerProvider, scope));

        bind(ExecutorService.class).annotatedWith(Names.named(
                SCOPED_BACKGROUND_THREAD_POOL_NAME)).toProvider(
                        new WrappedWorkerThreadPoolProvider(backgroundProvider, scope));

        bind(DateTime.class).toInstance(DateTime.now());
        bind(Duration.class).toProvider(UptimeProvider.class);
        bind(new CKTL()).toProvider(CookiesProvider.class);

//        bind(String.class).annotatedWith(Names.named("application")).toProvider(ApplicationNameProvider.class);
        bind(String.class).annotatedWith(Names.named("application")).toInstance(this.appType.getSimpleName());

        bind(ServerImpl.class).asEagerSingleton();
        for (Module m : otherModules) {
            install(m);
        }
        bind(Charset.class).toProvider(CharsetProvider.class);
        bind(ByteBufAllocator.class).toProvider(ByteBufAllocatorProvider.class);
        bind(new ETL()).toProvider(EventProvider.class).in(scope);
        bind(Codec.class).to(CodecImpl.class);
        bind(ApplicationControl.class).toProvider(ApplicationControlProvider.class).in(Scopes.SINGLETON);
        bind(ExceptionEvaluatorRegistry.class).asEagerSingleton();
        bind(KeysValues.class).toProvider(KeysValuesProvider.class);
        bind(InvalidInputExceptionEvaluator.class).asEagerSingleton();
    }

    private static final class KeysValuesProvider implements Provider<KeysValues> {

        private final Provider<HttpEvent> evt;

        @Inject
        public KeysValuesProvider(Provider<HttpEvent> evt) {
            this.evt = evt;
        }

        @Override
        public KeysValues get() {
            return new KeysValues.MapAdapter(evt.get().getParametersAsMap());
        }
    }

    private static final class InvalidInputExceptionEvaluator extends ExceptionEvaluator {

        @Inject
        public InvalidInputExceptionEvaluator(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, HttpEvent evt) {
            if (t instanceof InvalidInputException) {
                InvalidInputException iie = (InvalidInputException) t;
                return Err.badRequest(iie.getProblems().toString());
            }
            return null;
        }
    }

    private static final class EventProvider implements Provider<Event<?>> {

        @SuppressWarnings("unchecked")
        private final Provider<Event> eventProvider;

        @SuppressWarnings("unchecked")
        @Inject
        public EventProvider(Provider<Event> eventProvider) {
            this.eventProvider = eventProvider;
        }

        @Override
        public Event<?> get() {
            return eventProvider.get();
        }
    }

    private static final class ETL extends TypeLiteral<Event<?>> {

    }

    static class ApplicationControlProvider implements Provider<ApplicationControl> {

        private final ApplicationControl control;

        @Inject
        public ApplicationControlProvider(Provider<Application> app) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            // In order to separate the API and SPI of Application, so Application
            // is not polluted with methods a subclasser should never call,
            // we do this:
            Application a = app.get();
            java.lang.reflect.Method method = Application.class.getDeclaredMethod("control");
            method.setAccessible(true);
            control = (ApplicationControl) method.invoke(a);
        }

        @Override
        public ApplicationControl get() {
            return control;
        }
    }

    static class CodecImpl implements Codec {

        private final Provider<ObjectMapper> mapper;

        @Inject
        public CodecImpl(Provider<ObjectMapper> mapper) {
            this.mapper = mapper;
        }

        @Override
        public <T> String writeValueAsString(T object) throws IOException {
            return mapper.get().writeValueAsString(object);
        }

        @Override
        public <T> void writeValue(T object, OutputStream out) throws IOException {
            mapper.get().writeValue(out, object);
        }

        @Override
        public <T> T readValue(InputStream byteBufInputStream, Class<T> type) throws IOException {
            return mapper.get().readValue(byteBufInputStream, type);
        }

        @Override
        public <T> byte[] writeValueAsBytes(T object) throws IOException {
            return mapper.get().writeValueAsBytes(object);
        }
    }

    @Singleton
    private static final class CharsetProvider implements Provider<Charset> {

        private final Charset charset;

        @Inject
        CharsetProvider(Settings settings) {
            String set = settings.getString("charset");
            if (set == null) {
                charset = CharsetUtil.UTF_8;
            } else {
                charset = Charset.forName(set);
            }
        }

        @Override
        public Charset get() {
            return charset;
        }
    }

    private static final class ApplicationNameProvider implements Provider<String> {

        private final Provider<Application> app;

        @Inject
        ApplicationNameProvider(Provider<Application> app) {
            this.app = app;
        }

        @Override
        public String get() {
            return app.get().getName();
        }
    }

    /**
     * Add another module to be installed with this one
     *
     * @param module
     */
    public ServerModule<A> add(Module module) {
        otherModules.add(module);
        return this;
    }

    /**
     * Override to configure options on the bootstrap which will start the
     * server. The default implementation sets up the allocator.
     *
     * @param bootstrap The server bootstrap
     * @param settings The application settings
     * @return The same bootstrap or optionally another one
     */
    protected ServerBootstrap configureServerBootstrap(ServerBootstrap bootstrap, Settings settings) {
        return bootstrap;
    }

    @Singleton
    private static final class ByteBufAllocatorProvider implements Provider<ByteBufAllocator> {

        private final Settings settings;
        private volatile ByteBufAllocator allocator;

        @Inject
        public ByteBufAllocatorProvider(Settings settings) {
            this.settings = settings;
        }

        public ByteBufAllocator get() {
            String s = settings.getString(BYTEBUF_ALLOCATOR_SETTINGS_KEY, DEFAULT_ALLOCATOR);
            ByteBufAllocator result = this.allocator;
            if (result == null) {
                synchronized (this) {
                    if (result == null) {
                        switch (s) {
                            case DIRECT_OR_HEAP_BY_PLATFORM:
                                result = UnpooledByteBufAllocator.DEFAULT;
                                break;
                            case DIRECT_ALLOCATOR:
                                result = new UnpooledByteBufAllocator(true);
                                break;
                            case HEAP_ALLOCATOR:
                                result = new UnpooledByteBufAllocator(false);
                                break;
                            case POOLED_ALLOCATOR:
                                result = PooledByteBufAllocator.DEFAULT;
                                break;
                            default:
                                throw new ConfigurationError("Unknown value for " + BYTEBUF_ALLOCATOR_SETTINGS_KEY
                                        + " '" + s + "'; valid values are " + DIRECT_ALLOCATOR + ", "
                                        + HEAP_ALLOCATOR + ", " + POOLED_ALLOCATOR);
                        }
                        this.allocator = result;
                    }
                }
            }
            return this.allocator;
        }
    }

    private final class ServerBootstrapProvider implements Provider<ServerBootstrap> {

        private final Provider<Settings> settings;
        private final Provider<ByteBufAllocator> allocator;

        public ServerBootstrapProvider(Provider<Settings> settings, Provider<ByteBufAllocator> allocator) {
            this.settings = settings;
            this.allocator = allocator;
        }

        @Override
        public ServerBootstrap get() {
            ServerBootstrap result = new ServerBootstrap();
            result = result.childOption(ChannelOption.ALLOCATOR, allocator.get());
            return configureServerBootstrap(result, settings.get());
        }
    }

    private static final class WrappedWorkerThreadPoolProvider implements Provider<ExecutorService> {

        private final Provider<ExecutorService> svc;
        private final ReentrantScope scope;

        public WrappedWorkerThreadPoolProvider(Provider<ExecutorService> svc, ReentrantScope scope) {
            this.svc = svc;
            this.scope = scope;
        }

        @Override
        public ExecutorService get() {
            return scope.wrapThreadPool(svc.get());
        }
    }

    private static final class CookiesProvider implements Provider<Set<Cookie>> {

        private final Provider<HttpEvent> ev;

        @Inject
        public CookiesProvider(Provider<HttpEvent> ev) {
            this.ev = ev;
        }

        @Override
        public Set<Cookie> get() {
            HttpEvent evt = ev.get();
            String h = evt.getHeader(HttpHeaders.Names.COOKIE);
            if (h != null) {
                Set<Cookie> result = CookieDecoder.decode(h);
                if (result != null) {
                    return result;
                }
            }
            return Collections.emptySet();
        }
    }

    private static final class ExecutorServiceProvider implements Provider<ExecutorService> {

        final TF tf;
        private volatile ExecutorService svc;
        private final ThreadCount count;

        public ExecutorServiceProvider(TF tf, ThreadCount count) {
            this.tf = tf;
            this.count = count;
        }

        private ExecutorService create() {
            switch (tf.name()) {
                case BACKGROUND_THREAD_POOL_NAME:
//                    return LoggingExecutorService.wrap(tf.name(), Executors.newCachedThreadPool(tf));
                    return Executors.newCachedThreadPool(tf);
                default:
//                    return LoggingExecutorService.wrap(tf.name(), Executors.newFixedThreadPool(count.get(), tf));
                    return Executors.newFixedThreadPool(count.get(), tf);
            }
        }

        @Override
        public ExecutorService get() {
            if (svc == null) {
                synchronized (this) {
                    if (svc == null) {
                        svc = create();
                    }
                }
            }
            return svc;
        }
    }

    static final class TF implements ThreadFactory, UncaughtExceptionHandler {

        private final String name;
        private final Provider<ApplicationControl> app;
        private final AtomicInteger count = new AtomicInteger();
        private final ThreadGroup tg;

        public TF(String name, Provider<ApplicationControl> app) {
            this.name = name;
            this.app = app;
            tg = new ThreadGroup(Thread.currentThread().getThreadGroup(), name + "s");
            tg.setDaemon(true);
        }

        public String name() {
            return name;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            if ("event".equals(tg.getName())) {
                t.setPriority(Thread.MAX_PRIORITY);
            } else {
                t.setPriority(Thread.NORM_PRIORITY - 1);
            }
            t.setUncaughtExceptionHandler(this);
            String nm = name + "-" + count.getAndIncrement();
            t.setName(nm);
            return t;
        }

        @Override
        public void uncaughtException(Thread on, Throwable error) {
            app.get().internalOnError(error);
        }

        public String toString() {
            return "ThreadFactory " + name;
        }
    }

    private static class UptimeProvider implements Provider<Duration> {

        private final DateTime dt;

        @Inject
        UptimeProvider(DateTime dt) {
            this.dt = dt;
        }

        @Override
        public Duration get() {
            return new Duration(dt, new DateTime());
        }
    }

    protected void onInit(Settings settings) {
    }

    protected void onBeforeStart(Server server, Dependencies deps) {
    }

    protected void onAfterStart(Server server, Dependencies deps) {
    }

    public Condition start() throws IOException, InterruptedException {
        return start(null);
    }

    public Condition start(Integer port) throws IOException, InterruptedException {
        MutableSettings settings = SettingsBuilder.createDefault().buildMutableSettings();
        if (port != null) {
            settings.setInt("port", port);
        }
        Integer pt = settings.getInt("port");

        if (pt == null) {
            settings.setInt("port", port == null ? 8080 : port);
            pt = 8080;
        }
        onInit(settings);
        Dependencies dependencies = new Dependencies(settings, this);
        Server server = dependencies.getInstance(Server.class);
        onBeforeStart(server, dependencies);

        Condition result = server.start();
        onAfterStart(server, dependencies);
        return result;
    }

    private static class CISC extends TypeLiteral<ChannelInitializer<SocketChannel>> {
    }

    private static class CKTL extends TypeLiteral<Set<Cookie>> {
    }
}
