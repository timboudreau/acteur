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
package com.mastfrog.acteur.mongo.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.mongo.reactive.MongoUpdater.MongoResult;
import com.mastfrog.acteur.mongo.reactive.WriteCursorContentsAsJSON.CursorResult;
import com.mastfrog.acteur.mongo.reactive.WriteCursorContentsAsJSON.SingleResult;
import com.mastfrog.function.misc.QuietAutoClosable;
import com.mastfrog.giulius.mongodb.reactive.DynamicCodecs;
import com.mastfrog.giulius.mongodb.reactive.GiuliusMongoReactiveStreamsModule;
import com.mastfrog.giulius.mongodb.reactive.Java8DateTimeCodecProvider;
import com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig;
import com.mastfrog.giulius.mongodb.reactive.MongoAsyncInitializer;
import com.mastfrog.giulius.mongodb.reactive.MongoFutureCollection;
import com.mastfrog.giulius.mongodb.reactive.util.SubscriberContext;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.reactivestreams.Publisher;

/**
 * Sets up Acteur with the mongo-reactive-streams bindings and convenience
 * acteurs to write out cursor contents, etc.
 * <p>
 * The methods for configuring Jackson on this class are specifically for
 * configuring how Java classes are mapped to BSON - you still want to install
 * JacksonModule and configure that for how HTTP output is handled.
 * </p><p>
 * The most efficient way to use cursors and collections is to apply a type of
 * ByteBuf to the collection before passing it to a cursor - this module
 * installs codecs that can bypass instantiating Java objects in a lot of cases
 * and directly translate BSON to JSON in a buffer ready to emit to the socket.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class ActeurMongoModule extends AbstractModule implements MongoAsyncConfig<ActeurMongoModule> {

    public static final String JACKSON_BINDING_NAME = "mongo";
    private final GiuliusMongoReactiveStreamsModule base = new GiuliusMongoReactiveStreamsModule();
    private final ReentrantScope scope;
    private final Set<Class<?>> jacksonCodecs = new HashSet<>();
    private final JacksonModule jacksonModule = new JacksonModule(JACKSON_BINDING_NAME, false);
    private CursorControl defaultCursorControl = CursorControl.DEFAULT;
    private boolean bindSubscribers = true;

    public ActeurMongoModule(ReentrantScope scope) {
        this.scope = scope;
        base.withCodecProvider(Java8DateTimeCodecProvider.class);
        base.withCodec(ByteBufCodec.class);
        base.withDynamicCodecs(JacksonCodecs.class);
        base.withCodecProvider(AdditionalJacksonCodecs.class);
        withJacksonConfigurer2(ObjectIdJacksonConfigurer.class);
        withJacksonConfigurer(JacksonConfigurer.localeConfigurer());
    }

    public ActeurMongoModule dontBindSubscribers() {
        bindSubscribers = false;
        return this;
    }

    public ActeurMongoModule withJavaTimeSerializationMode(com.mastfrog.jackson.configuration.TimeSerializationMode timeMode, com.mastfrog.jackson.configuration.DurationSerializationMode durationMode) {
        jacksonModule.withJavaTimeSerializationMode(timeMode, durationMode);
        return this;
    }

    @SuppressWarnings("deprecation")
    public ActeurMongoModule withJavaTimeSerializationMode(com.mastfrog.jackson.TimeSerializationMode timeMode, com.mastfrog.jackson.DurationSerializationMode durationMode) {
        jacksonModule.withJavaTimeSerializationMode(timeMode, durationMode);
        return this;
    }

    @SuppressWarnings("deprecation")
    public ActeurMongoModule withJacksonConfigurer(com.mastfrog.jackson.JacksonConfigurer configurer) {
        jacksonModule.withConfigurer(configurer);
        return this;
    }

    public ActeurMongoModule withJacksonConfigurer(JacksonConfigurer configurer) {
        jacksonModule.withConfigurer(configurer);
        return this;
    }

    @SuppressWarnings("deprecation")
    public ActeurMongoModule withJacksonConfigurer(Class<? extends com.mastfrog.jackson.JacksonConfigurer> configurer) {
        jacksonModule.withConfigurer(configurer);
        return this;
    }

    public ActeurMongoModule withJacksonConfigurer2(Class<? extends com.mastfrog.jackson.configuration.JacksonConfigurer> configurer) {
        jacksonModule.withConfigurer2(configurer);
        return this;
    }

    public ActeurMongoModule registerJacksonTypes(Class<?>... types) {
        for (Class<?> type : types) {
            registerJacksonType(type);
        }
        return this;
    }

    public ActeurMongoModule registerJacksonType(Class<?> type) {
        jacksonCodecs.add(type);
        return this;
    }

    @Override
    public ActeurMongoModule withCodecProvider(CodecProvider prov) {
        base.withCodecProvider(prov);
        return this;
    }

    @Override
    public ActeurMongoModule withCodec(Codec<?> prov) {
        base.withCodec(prov);
        return this;
    }

    @Override
    public ActeurMongoModule withCodecProvider(Class<? extends CodecProvider> prov) {
        base.withCodecProvider(prov);
        return this;
    }

    @Override
    public ActeurMongoModule withCodec(Class<? extends Codec<?>> prov) {
        base.withCodec(prov);
        return this;
    }

    @Override
    public ActeurMongoModule withClientSettings(MongoClientSettings settings) {
        base.withClientSettings(settings);
        return this;
    }

    @Override
    public ActeurMongoModule bindCollection(String bindingName, String collectionName) {
        base.bindCollection(bindingName, collectionName);
        return this;
    }

    @Override
    public <T> ActeurMongoModule bindCollection(String bindingName, String collectionName, Class<T> type) {
        base.bindCollection(bindingName, collectionName, type);
        return this;
    }

    public ActeurMongoModule withDefaultCursorSettings(CursorControl ctrl) {
        this.defaultCursorControl = notNull("ctrl", ctrl).lock();
        return this;
    }

    public ActeurMongoModule loadJacksonConfigurersFromMetaInfServices() {
        jacksonModule.loadFromMetaInfServices();
        return this;
    }

    @Override
    protected void configure() {
        install(base);
        scope.bindTypes(binder(), Bson.class, Document.class, MongoResult.class, MongoCollection.class, SingleResult.class,
                CursorResult.class, FindPublisher.class, ETagResult.class, CacheHeaderInfo.class, AggregatePublisher.class, Publisher.class,
                MongoFutureCollection.class);
        Provider<CursorControl> ctrlProvider = scope.provider(CursorControl.class, () -> defaultCursorControl.copy());
        bind(CursorControl.class).toProvider(ctrlProvider);
        bind(GenerifiedFindIterable.literal).toProvider(GenerifiedFindIterable.class);
        bind(GenerifiedMongoIterable.literal).toProvider(GenerifiedMongoIterable.class);
        bind(GenerifiedAggregateIterable.literal).toProvider(GenerifiedAggregateIterable.class);
        bind(GenerifiedMongoCollection.literal).toProvider(GenerifiedMongoCollection.class);
        bind(GenerifiedMongoFutureCollection.literal).toProvider(GenerifiedMongoFutureCollection.class);
        bind(new TypeLiteral<Set<Class<?>>>() {
        }).annotatedWith(Names.named("__jm")).toInstance(jacksonCodecs);

        if (bindSubscribers) {
            bind(SubscriberContext.class).to(SubscriberContextImpl.class);
        }
        install(jacksonModule);
    }

    @Singleton
    static class AdditionalJacksonCodecs implements CodecProvider {

        final Set<Class<?>> types;
        private final Provider<ObjectMapper> mapper;
        private final Provider<ByteBufCodec> codec;
        private final Map<Class<?>, JacksonCodec<?>> cache = new ConcurrentHashMap<>();

        @Inject
        public AdditionalJacksonCodecs(@Named("__jm") Set<Class<?>> types, @Named(JACKSON_BINDING_NAME) Provider<ObjectMapper> mapper, Provider<ByteBufCodec> codec) {
            this.types = types;
            this.mapper = mapper;
            this.codec = codec;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Codec<T> get(Class<T> type, CodecRegistry cr) {
            if (types.contains(type)) {
                return (Codec<T>) cache.computeIfAbsent(type, t -> {
                    return new JacksonCodec<>(mapper, codec, t);
                });
            }
            return null;
        }
    }

    @Override
    public <T> ActeurMongoModule bindCollection(String bindingName, Class<T> type) {
        base.bindCollection(bindingName, type);
        return this;
    }

    @Override
    public ActeurMongoModule bindCollection(String bindingName) {
        base.bindCollection(bindingName);
        return this;
    }

    @Override
    public ActeurMongoModule withInitializer(Class<? extends MongoAsyncInitializer> initializerType) {
        base.withInitializer(initializerType);
        return this;
    }

    @Override
    public ActeurMongoModule withDynamicCodecs(Class<? extends DynamicCodecs> codecs) {
        throw new UnsupportedOperationException("Already using Jackson codecs.");
    }

    private static class GenerifiedFindIterable implements Provider<FindPublisher<?>> {

        @SuppressWarnings("unchecked")
        private final Provider<FindPublisher> find;
        private static final TypeLiteral<FindPublisher<?>> literal = new TypeLiteral<FindPublisher<?>>() {
        };

        @Inject
        @SuppressWarnings("unchecked")
        public GenerifiedFindIterable(Provider<FindPublisher> find) {
            this.find = find;
        }

        @Override
        public FindPublisher<?> get() {
            return find.get();
        }
    }

    private static class GenerifiedMongoIterable implements Provider<Publisher<?>> {

        @SuppressWarnings("unchecked")
        private final Provider<Publisher> find;
        private static final TypeLiteral<Publisher<?>> literal = new TypeLiteral<Publisher<?>>() {
        };

        @Inject
        @SuppressWarnings("unchecked")
        public GenerifiedMongoIterable(Provider<Publisher> find) {
            this.find = find;
        }

        @Override
        public Publisher<?> get() {
            return find.get();
        }
    }

    private static class GenerifiedAggregateIterable implements Provider<AggregatePublisher<?>> {

        @SuppressWarnings("unchecked")
        private final Provider<AggregatePublisher> find;
        private static final TypeLiteral<AggregatePublisher<?>> literal = new TypeLiteral<AggregatePublisher<?>>() {
        };

        @Inject
        @SuppressWarnings("unchecked")
        public GenerifiedAggregateIterable(Provider<AggregatePublisher> find) {
            this.find = find;
        }

        @Override
        public AggregatePublisher<?> get() {
            return find.get();
        }
    }

    private static class GenerifiedMongoCollection implements Provider<MongoCollection<?>> {

        private final Provider<MongoCollection> find;
        private static final TypeLiteral<MongoCollection<?>> literal = new TypeLiteral<MongoCollection<?>>() {
        };

        @Inject
        public GenerifiedMongoCollection(Provider<MongoCollection> find) {
            this.find = find;
        }

        @Override
        public MongoCollection<?> get() {
            return find.get();
        }
    }

    private static class GenerifiedMongoFutureCollection implements Provider<MongoFutureCollection<?>> {

        @SuppressWarnings("unchecked")
        private final Provider<MongoFutureCollection> find;
        private static final TypeLiteral<MongoFutureCollection<?>> literal = new TypeLiteral<MongoFutureCollection<?>>() {
        };

        @Inject
        @SuppressWarnings("unchecked")
        public GenerifiedMongoFutureCollection(Provider<MongoFutureCollection> find) {
            this.find = find;
        }

        @Override
        public MongoFutureCollection<?> get() {
            return find.get();
        }
    }

    static class SubscriberContextImpl extends SubscriberContext {

        private final ReentrantScope scope;
        private final UncaughtExceptionHandler handler;

        @Inject
        SubscriberContextImpl(ReentrantScope scope, UncaughtExceptionHandler handler) {
            this.scope = scope;
            this.handler = handler;
        }

        @Override
        protected void onThrow(Throwable thrown) {
            handler.uncaughtException(Thread.currentThread(), thrown);
        }

        @Override
        protected QuietAutoClosable beforeAfter() {
            try {
                Thread t = Thread.currentThread();
                UncaughtExceptionHandler old = t.getUncaughtExceptionHandler();
                t.setUncaughtExceptionHandler(handler);
                return () -> {
                    if (old != handler) {
                        Thread.currentThread().setUncaughtExceptionHandler(old);
                    }
                };
            } catch (Exception | Error e) {
                onThrow(e);
                return Exceptions.chuck(e);
            }
        }

        @Override
        public <T, R> BiConsumer<T, R> wrap(BiConsumer<T, R> runnable) {
            return scope.wrap(super.wrap(runnable));
        }

        @Override
        public <T> Consumer<T> wrap(Consumer<T> runnable) {
            return scope.wrap(super.wrap(runnable));
        }

        @Override
        public Runnable wrap(Runnable runnable) {
            return scope.wrap(super.wrap(runnable));
        }

    }

}
