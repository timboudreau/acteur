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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.net.MediaType;
import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.annotations.Concluders;
import com.mastfrog.acteur.annotations.GenericApplication;
import com.mastfrog.acteur.annotations.GenericApplicationModule;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.mongo.async.ActeurAsyncTest.M;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoAsyncInitializer;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.Exceptions;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoCollection;
import io.netty.buffer.ByteBuf;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.bson.Document;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, TestHarnessModule.class, MongoHarness.Module.class})
public class ActeurAsyncTest {

    @Test(timeout = 20000L)
    public void test(TestHarness harn) throws Throwable {
        harn.get("/hello").go().assertStatus(OK).assertContent("Hello world");
        Thing[] objs = harn.get("/stuff")
                .setTimeout(Duration.standardSeconds(18))
                .go().assertStatus(OK).throwIfError().content(Thing[].class);
        System.out.println("\n\nCT IS " + Arrays.toString(objs) + "\n\n");

        assertEquals(objs.length, 200);
        maybeFail();

        Thing one = harn.get("/oneThing")
                .setTimeout(Duration.standardSeconds(18))
                .go().assertStatus(OK).throwIfError().content(Thing.class);

        System.out.println("THING ONE: " + one);

    }

    private static void maybeFail() throws Throwable {
        if (failure != null) {
            throw failure;
        }
    }

    static class M extends AbstractModule {

        private final ReentrantScope scope = new ReentrantScope();
        private final Settings settings;

        public M(Settings settings) {
            this.settings = settings;
        }

        @Override
        protected void configure() {
            install(new ActeurMongoModule(scope).bindCollection("stuff", "stuff").withInitializer(Populator.class));
            install(new GenericApplicationModule<>(scope, settings, GenericApplication.class));
            bind(ErrorInterceptor.class).to(TestHarness.class);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ReentrantScope scope = new ReentrantScope();
        Settings settings = new SettingsBuilder("async")
                .add(GiuliusMongoAsyncModule.SETTINGS_KEY_DATABASE_NAME, "demo")
                .add(GiuliusMongoAsyncModule.SETTINGS_KEY_MONGO_HOST, "localhost")
                .add(GiuliusMongoAsyncModule.SETTINGS_KEY_MONGO_PORT, 27001)
                .build();

        Server server = new ServerBuilder("async", scope)
                .add(new ActeurMongoModule(scope)
                        .bindCollection("stuff", "stuff")
                        .withInitializer(Populator.class))
                .add(settings)
                .build();
        server.start().await();
    }

    static Throwable failure;

    static class Populator extends MongoAsyncInitializer {

        @Inject
        Populator(Registry reg) {
            super(reg);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onCreateCollection(String name, MongoCollection<?> collection) {
            List<Document> docs = new LinkedList<>();
            Random r = new Random(209024);
            for (int i = 0; i < 200; i++) {
                Document doc = new Document("name", "Thing-" + i).append("rand", r.nextInt(100));
                docs.add(doc);
            }
            System.out.println("Initial doc insert start");
            final CountDownLatch latch = new CountDownLatch(1);
            ((MongoCollection) collection).insertMany(docs, new SingleResultCallback<Void>() {

                @Override
                public void onResult(Void t, Throwable thrwbl) {
                    if (thrwbl != null) {
                        failure = thrwbl;
                        thrwbl.printStackTrace();;
                    }
                    System.out.println("Initial documents inserted");
                    latch.countDown();
                }
            });
            try {
                latch.await(10, TimeUnit.SECONDS);
                System.out.println("Done waiting for insert");
            } catch (InterruptedException ex) {
                Exceptions.chuck(ex);
            }
        }
    }

    @HttpCall
    @Path("/stuff")
    @Methods(GET)
    @Concluders(WriteCursorContentsAsJSON.class)
    static class GetAllStuff extends Acteur {

        @Inject
        GetAllStuff(@Named("stuff") MongoCollection<Document> stuff) {
            next(stuff.withDocumentClass(ByteBuf.class).find(), new CursorControl().batchSize(20).projection(
                    new Document("name", 1).append("rand", 1).append("_id", 0)));
        }
    }

    @HttpCall
    @Path("/oneThing")
    @Methods(GET)
    @Concluders(QueryActeur.class)
    static class GetOneStuff extends Acteur {

        @Inject
        GetOneStuff(@Named("stuff") MongoCollection<Document> stuff) {
//            next(stuff.withDocumentClass(ByteBuf.class).find(), new CursorControl().findOne(true).projection(
//                    new Document("name", 1).append("rand", 1).append("_id", 0)));
            next(stuff, new Document(), new CursorControl().findOne(true).projection(
                    new Document("name", 1).append("rand", 1).append("_id", 0)));
        }
    }

    @HttpCall
    @Path("/hello")
    @Methods(GET)
    static class Hello extends Acteur {

        Hello() {
            add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
            ok("Hello world");
        }
    }

    public static class Thing {

        public final String name;
        public final int rand;

        @JsonCreator
        public Thing(@JsonProperty("name") String name, @JsonProperty("rand") int rand) {
            this.name = name;
            this.rand = rand;
        }

        public String toString() {
            return name + ":" + rand;
        }
    }
}
