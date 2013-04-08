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

import com.mastfrog.jackson.JacksonModule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.name.Names;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.MutableSettings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.ImplicitBindings;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.server.ServerModule.TF;
import com.mastfrog.util.ConfigurationError;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
@Defaults("realm=Users")
public class ServerModule<A extends Application> extends AbstractModule {

    /**
     * Property name for setting which byte buffer allocator Netty uses (heap,
     * direct, pooled)
     */
    public static final String BYTEBUF_ALLOCATOR_SETTINGS_KEY = "propeller.bytebuf.allocator";
    /**
     * Property value for telling the server to use the direct byte buffer
     * allocator (non-heap)
     */
    public static final String DIRECT_ALLOCATOR = "direct";
    /**
     * Property value for telling the server to use the heap byte buffer
     * allocator
     */
    public static final String HEAP_ALLOCATOR = "heap";
    /**
     * Property value for telling the server to use the pooled byte buffer
     * allocator
     */
    public static final String POOLED_ALLOCATOR = "pooled";
    /**
     * The default allocator to use if none is specified
     */
    public static final String DEFAULT_ALLOCATOR = POOLED_ALLOCATOR;
    private final Class<A> appType;
    private ReentrantScope scope = new ReentrantScope();
    private final int eventThreads;
    private final int workerThreads;
    private final int backgroundThreads;
    private List<Module> otherModules = new ArrayList<>();

    public ServerModule(Class<A> appType, int workerThreadCount, int eventThreadCount, int backgroundThreadCount) {
        this.appType = appType;
        this.workerThreads = workerThreadCount;
        this.eventThreads = eventThreadCount;
        this.backgroundThreads = backgroundThreadCount;
    }

    public ServerModule(Class<A> appType) {
        this(appType, -1, -1, -1);
    }

    public ReentrantScope applicationScope() {
        return scope;
    }

    @Override
    protected void configure() {
        bind(ReentrantScope.class).toInstance(scope);
        bind(Application.class).to(appType).asEagerSingleton();
        bind(ChannelHandler.class).to(UpstreamHandlerImpl.class);
        bind(new CISC()).to(PipelineFactoryImpl.class);
        install(new JacksonModule());
        bind(ServerBootstrap.class).toProvider(new ServerBootstrapProvider(binder().getProvider(Settings.class)));

        scope.bindTypes(binder(), Event.class,
                Page.class, BasicCredentials.class);

        ImplicitBindings implicit = appType.getAnnotation(ImplicitBindings.class);
        if (implicit != null) {
            scope.bindTypes(binder(), implicit.value());
        }

        Provider<Application> appProvider = binder().getProvider(Application.class);
        Provider<Settings> set = binder().getProvider(Settings.class);

        TF eventThreadFactory = new TF("event", appProvider);
        TF workerThreadFactory = new TF("worker", appProvider);
        TF backgroundThreadFactory = new TF(Server.BACKGROUND_THREAD_POOL_NAME, appProvider);

        ThreadCount workerThreadCount = new ThreadCount(set, 8, workerThreads, "workerThreads");
        ThreadCount eventThreadCount = new ThreadCount(set, 8, eventThreads, "eventThreads");
        ThreadCount backgroundThreadCount = new ThreadCount(set, 128, backgroundThreads, "backgroundThreads");

        bind(ThreadCount.class).annotatedWith(Names.named("event")).toInstance(eventThreadCount);
        bind(ThreadCount.class).annotatedWith(Names.named("workers")).toInstance(eventThreadCount);
        bind(ThreadCount.class).annotatedWith(Names.named(Server.BACKGROUND_THREAD_POOL_NAME)).toInstance(eventThreadCount);

        bind(ThreadFactory.class).annotatedWith(Names.named("workers")).toInstance(workerThreadFactory);
        bind(ThreadFactory.class).annotatedWith(Names.named("event")).toInstance(eventThreadFactory);
        bind(ThreadFactory.class).annotatedWith(Names.named(Server.BACKGROUND_THREAD_POOL_NAME)).toInstance(backgroundThreadFactory);

        Provider<ExecutorService> workerProvider =
                new ExecutorServiceProvider(workerThreadFactory, workerThreadCount);
        Provider<ExecutorService> backgroundProvider =
                new ExecutorServiceProvider(backgroundThreadFactory, backgroundThreadCount);

        bind(ExecutorService.class).annotatedWith(Names.named(
                Server.WORKER_THREAD_POOL_NAME)).toProvider(workerProvider);

        bind(ExecutorService.class).annotatedWith(Names.named(
                Server.BACKGROUND_THREAD_POOL_NAME)).toProvider(backgroundProvider);

        bind(ExecutorService.class).annotatedWith(Names.named(
                Server.SCOPED_WORKER_THREAD_POOL_NAME)).toProvider(
                new WrappedWorkerThreadPoolProvider(workerProvider, scope));

        bind(ExecutorService.class).annotatedWith(Names.named(
                Server.SCOPED_BACKGROUND_THREAD_POOL_NAME)).toProvider(
                new WrappedWorkerThreadPoolProvider(backgroundProvider, scope));

        bind(DateTime.class).toInstance(new DateTime());
        bind(Duration.class).toProvider(UptimeProvider.class);
        bind(new CKTL()).toProvider(CookiesProvider.class);

        bind(ServerImpl.class).asEagerSingleton();
        for (Module m : otherModules) {
            install(m);
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
        String s = settings.getString(BYTEBUF_ALLOCATOR_SETTINGS_KEY, DEFAULT_ALLOCATOR);
        switch (s) {
            case DIRECT_ALLOCATOR:
                // XXX where did direct go?
                bootstrap.childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
                break;
            case HEAP_ALLOCATOR:
                bootstrap.childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
                break;
            case POOLED_ALLOCATOR:
                bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                break;
            default:
                throw new ConfigurationError("Unknown value for " + BYTEBUF_ALLOCATOR_SETTINGS_KEY
                        + " '" + s + "'; valid values are " + DIRECT_ALLOCATOR + ", "
                        + HEAP_ALLOCATOR + ", " + POOLED_ALLOCATOR);
        }
        return bootstrap;
    }

    private final class ServerBootstrapProvider implements Provider<ServerBootstrap> {

        private final Provider<Settings> settings;

        public ServerBootstrapProvider(Provider<Settings> settings) {
            this.settings = settings;
        }

        @Override
        public ServerBootstrap get() {
            ServerBootstrap result = new ServerBootstrap();
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

        private final Provider<Event> ev;

        @Inject
        public CookiesProvider(Provider<Event> ev) {
            this.ev = ev;
        }

        @Override
        public Set<Cookie> get() {
            Event evt = ev.get();
            String h = evt.getHeader("Cookie");
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

        private final TF tf;
        private volatile ExecutorService svc;
        private final ThreadCount count;

        public ExecutorServiceProvider(TF tf, ThreadCount count) {
            this.tf = tf;
            this.count = count;
        }

        private ExecutorService create() {
            switch (tf.name()) {
                case Server.BACKGROUND_THREAD_POOL_NAME:
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
        private final Provider<Application> app;
        private final AtomicInteger count = new AtomicInteger();

        public TF(String name, Provider<Application> app) {
            this.name = name;
            this.app = app;
        }

        public String name() {
            return name;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.setUncaughtExceptionHandler(this);
            String nm = name + "-" + count.getAndIncrement();
            t.setName(nm);
            return t;
        }

        @Override
        public void uncaughtException(Thread on, Throwable error) {
            app.get().onError(error);
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

    public Server start() throws IOException, InterruptedException {
        return start(null);
    }

    public Server start(Integer port) throws IOException, InterruptedException {
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

        server.start();
        onAfterStart(server, dependencies);
        return server;
    }

    private static class CISC extends TypeLiteral<ChannelInitializer<SocketChannel>> {
    }

    private static class CKTL extends TypeLiteral<Set<Cookie>> {
    }
}
