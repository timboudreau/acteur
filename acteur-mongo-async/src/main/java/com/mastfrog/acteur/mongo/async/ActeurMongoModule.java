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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoAsyncConfig;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.acteur.mongo.async.WriteCursorContentsAsJSON.CursorResult;
import com.mastfrog.acteur.mongo.async.WriteCursorContentsAsJSON.SingleResult;
import com.mastfrog.giulius.mongodb.async.MongoAsyncInitializer;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoCollection;
import javax.inject.Provider;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.conversions.Bson;

/**
 *
 * @author Tim Boudreau
 */
public class ActeurMongoModule extends AbstractModule implements MongoAsyncConfig<ActeurMongoModule> {

    private final GiuliusMongoAsyncModule base = new GiuliusMongoAsyncModule();
    private final ReentrantScope scope;

    public ActeurMongoModule(ReentrantScope scope) {
        this.scope = scope;
        withCodec(ByteBufCodec.class);
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
        scope.bindTypes(binder(), Bson.class, Document.class, MongoCollection.class, SingleResult.class, CursorResult.class, FindIterable.class);
        Provider<CursorControl> ctrlProvider = scope.provider(CursorControl.class, Providers.<CursorControl>of(CursorControl.DEFAULT));
        bind(CursorControl.class).toProvider(ctrlProvider);
        bind(GenerifiedFindIterable.literal).toProvider(GenerifiedFindIterable.class);
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

    private static class GenerifiedFindIterable implements Provider<FindIterable<?>> {

        private final Provider<FindIterable> find;
        private static final TypeLiteral<FindIterable<?>> literal = new TypeLiteral<FindIterable<?>>() {
        };

        @Inject
        public GenerifiedFindIterable(Provider<FindIterable> find) {
            this.find = find;
        }

        @Override
        public FindIterable<?> get() {
            return find.get();
        }
    }
}
