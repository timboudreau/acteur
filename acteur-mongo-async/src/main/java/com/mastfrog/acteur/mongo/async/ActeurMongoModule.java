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
package com.mastfrog.acteur.mongo.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import com.mastfrog.acteur.mongo.async.MongoUpdater.MongoResult;
import com.mastfrog.acteur.mongo.async.QueryWithCacheHeadersActeur.EtagResult;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoAsyncConfig;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.acteur.mongo.async.WriteCursorContentsAsJSON.CursorResult;
import com.mastfrog.acteur.mongo.async.WriteCursorContentsAsJSON.SingleResult;
import com.mastfrog.giulius.mongodb.async.DynamicCodecs;
import com.mastfrog.giulius.mongodb.async.Java8DateTimeCodecProvider;
import com.mastfrog.giulius.mongodb.async.MongoAsyncInitializer;
import com.mastfrog.jackson.DurationSerializationMode;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.jackson.LocaleJacksonConfigurer;
import com.mastfrog.jackson.TimeSerializationMode;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoCollection;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Provider;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

/**
 *
 * @author Tim Boudreau
 */
public final class ActeurMongoModule extends AbstractModule implements MongoAsyncConfig<ActeurMongoModule> {

    public static final String JACKSON_BINDING_NAME = "mongo";
    private final GiuliusMongoAsyncModule base = new GiuliusMongoAsyncModule();
    private final ReentrantScope scope;
    private final Set<Class<?>> jacksonCodecs = new HashSet<>();
    private final JacksonModule jacksonModule = new JacksonModule(JACKSON_BINDING_NAME, false);

    public ActeurMongoModule(ReentrantScope scope) {
        this.scope = scope;
        base.withCodecProvider(Java8DateTimeCodecProvider.class);
        base.withCodec(ByteBufCodec.class);
        base.withDynamicCodecs(JacksonCodecs.class);
        base.withCodecProvider(AdditionalJacksonCodecs.class);
        withJacksonConfigurer(ObjectIdJacksonConfigurer.class);
        withJacksonConfigurer(LocaleJacksonConfigurer.class);
    }

    public ActeurMongoModule withJavaTimeSerializationMode(TimeSerializationMode timeMode, DurationSerializationMode durationMode) {
        jacksonModule.withJavaTimeSerializationMode(timeMode, durationMode);
        return this;
    }

    public ActeurMongoModule withJacksonConfigurer(JacksonConfigurer configurer) {
        jacksonModule.withConfigurer(configurer);
        return this;
    }

    public ActeurMongoModule withJacksonConfigurer(Class<? extends JacksonConfigurer> configurer) {
        jacksonModule.withConfigurer(configurer);
        return this;
    }

    public ActeurMongoModule registerJacksonType(Class<?> type) {
        jacksonCodecs.add(type);
        return this;
    }

    public ActeurMongoModule withCodecProvider(CodecProvider prov) {
        base.withCodecProvider(prov);
        return this;
    }

    public ActeurMongoModule withCodec(Codec<?> prov) {
        base.withCodec(prov);
        return this;
    }

    public ActeurMongoModule withCodecProvider(Class<? extends CodecProvider> prov) {
        base.withCodecProvider(prov);
        return this;
    }

    public ActeurMongoModule withCodec(Class<? extends Codec<?>> prov) {
        base.withCodec(prov);
        return this;
    }

    public ActeurMongoModule withClientSettings(MongoClientSettings settings) {
        base.withClientSettings(settings);
        return this;
    }

    public ActeurMongoModule bindCollection(String bindingName, String collectionName) {
        base.bindCollection(bindingName, collectionName);
        return this;
    }

    public <T> ActeurMongoModule bindCollection(String bindingName, String collectionName, Class<T> type) {
        base.bindCollection(bindingName, collectionName, type);
        return this;
    }

    @Override
    protected void configure() {
        install(base);
        scope.bindTypes(binder(), Bson.class, Document.class, MongoResult.class, MongoCollection.class, SingleResult.class, 
                CursorResult.class, FindIterable.class, EtagResult.class, CacheHeaderInfo.class);
        Provider<CursorControl> ctrlProvider = scope.provider(CursorControl.class, Providers.<CursorControl>of(CursorControl.DEFAULT));
        bind(CursorControl.class).toProvider(ctrlProvider);
        bind(GenerifiedFindIterable.literal).toProvider(GenerifiedFindIterable.class);
        bind(GenerifiedMongoCollection.literal).toProvider(GenerifiedMongoCollection.class);
        bind(new TypeLiteral<Set<Class<?>>>() {
        }).annotatedWith(Names.named("__jm")).toInstance(jacksonCodecs);
        install(jacksonModule);
    }

    static class AdditionalJacksonCodecs implements CodecProvider {

        final Set<Class<?>> types;
        private final Provider<ObjectMapper> mapper;
        private final Provider<ByteBufCodec> codec;

        @Inject
        public AdditionalJacksonCodecs(@Named("__jm") Set<Class<?>> types, @Named(JACKSON_BINDING_NAME) Provider<ObjectMapper> mapper, Provider<ByteBufCodec> codec) {
            this.types = types;
            this.mapper = mapper;
            this.codec = codec;
        }

        @Override
        public <T> Codec<T> get(Class<T> type, CodecRegistry cr) {
            if (types.contains(type)) {
                return new JacksonCodec<T>(mapper, codec, type);
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

    private static class GenerifiedFindIterable implements Provider<FindIterable<?>> {

        @SuppressWarnings("unchecked")
        private final Provider<FindIterable> find;
        private static final TypeLiteral<FindIterable<?>> literal = new TypeLiteral<FindIterable<?>>() {
        };

        @Inject
        @SuppressWarnings("unchecked")
        public GenerifiedFindIterable(Provider<FindIterable> find) {
            this.find = find;
        }

        @Override
        public FindIterable<?> get() {
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
}
