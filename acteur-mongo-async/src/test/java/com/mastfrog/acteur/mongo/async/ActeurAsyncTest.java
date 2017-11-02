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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import static com.google.common.net.MediaType.JSON_UTF_8;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.Concluders;
import com.mastfrog.acteur.annotations.GenericApplication;
import com.mastfrog.acteur.annotations.GenericApplicationModule;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.DELETE;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.mongo.async.ActeurAsyncTest.M;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
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
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.util.Checks.notNull;
import com.mastfrog.util.Exceptions;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.collections.StringObjectMap;
import com.mongodb.WriteConcern;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoCollection;
import io.netty.buffer.ByteBuf;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, TestHarnessModule.class, MongoHarness.Module.class})
public class ActeurAsyncTest {

//    static {
    // for debugging
//        System.setProperty("acteur.debug", "true");
//        System.setProperty("mongo.tmplog", "true");
//    }

    @Test(timeout = 20000L)
    public void test(TestHarness harn, ObjectMapper mapper, @Named("stuff") MongoCollection<Document> stuff) throws Throwable {
        harn.get("/hello").go().assertStatus(OK).assertContent("Hello world");
        String s = harn.get("/stuff")
                .setTimeout(Duration.ofSeconds(18))
                .go().await().assertStatus(OK).throwIfError().content();

        Thing[] objs = mapper.readValue(s, Thing[].class);

        assertEquals(objs.length, 200);
        maybeFail();

        Thing one = harn.get("/oneThing")
                .setTimeout(Duration.ofSeconds(18))
                .go().assertStatus(OK).throwIfError().content(Thing.class);

        assertNotNull("Null thing", one);

        StringObjectMap m = harn.delete("oneThing", one._id.toHexString())
                .setTimeout(Duration.ofMinutes(2))
                .go().assertStatus(OK).throwIfError().content(StringObjectMap.class);

        assertEquals(1, m.get("count"));
        assertEquals(Boolean.TRUE, m.get("acknowledged"));

        s = harn.get("/stuff")
                .setTimeout(Duration.ofSeconds(18))
                .go()
                .await()
                .assertStatus(OK).throwIfError().content();
        Thing[] things2 = mapper.readValue(s, Thing[].class);
        assertEquals(199, things2.length);

        for (Thing t : things2) {
            assertNotEquals(one.name, t.name);
        }

        List<Thing> more = Arrays.asList(new Thing("skiddoo", 23), new Thing("meaning", 42));

        harn.put("manythings")
                .setBody(more, MediaType.JSON_UTF_8)
                .setTimeout(Duration.ofSeconds(20))
                .go()
                .await()
                .assertStatus(CREATED).throwIfError().content(Thing[].class);

        assertTrue(manythingsCalled);

        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] th = new Throwable[1];
        Document[] d = new Document[1];
        stuff.find(new Document("name", "skiddoo")).first((Document t, Throwable thrwbl) -> {
            th[0] = thrwbl;
            d[0] = t;
            latch.countDown();
        });
        latch.await(10, TimeUnit.SECONDS);
        if (th[0] != null) {
            throw th[0];
        }
        assertNotNull("Documents not added", d[0]);

        s = harn.get("/stuff")
                .setTimeout(Duration.ofSeconds(4))
                .go()
                .await()
                .assertStatus(OK).throwIfError().content();
        Thing[] things3 = mapper.readValue(s, Thing[].class);
        assertEquals(201, things3.length);

        int foundCount = 0;
        for (Thing t : things3) {
            if ("skiddoo".equals(t.name)) {
                assertEquals(23, t.rand);
                foundCount++;
            }
            if ("meaning".equals(t.name)) {
                assertEquals(42, t.rand);
                foundCount++;
            }
        }
        assertEquals(Arrays.asList(things3).toString(), 2, foundCount);

        StringObjectMap updateResults = harn.put("/stuff/" + things3[0]._id.toHexString())
                .setBody(map("name").to("gerbil").build(), JSON_UTF_8)
                .setTimeout(Duration.ofSeconds(5))
                .go()
                .await()
                .assertStatus(OK).throwIfError().content(StringObjectMap.class);

        assertEquals(1, updateResults.get("matched"));
        assertEquals(1, updateResults.get("updated"));
        assertTrue((Boolean) updateResults.get("acknowledged"));

        s = harn.get("/stuff")
                .setTimeout(Duration.ofSeconds(4))
                .go()
                .await()
                .assertStatus(OK).throwIfError().content();
        Thing[] things4 = mapper.readValue(s, Thing[].class);
        assertEquals(201, things3.length);
        for (Thing t : things4) {
            if (things3[0]._id.equals(t._id)) {
                assertEquals("gerbil", t.name);
            }
        }
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
            install(new ActeurMongoModule(scope)
                    .registerJacksonType(Thing.class)
                    .bindCollection("stuff", "stuff")
                    .withInitializer(Populator.class));
            install(new GenericApplicationModule<>(scope, settings, GenericApplication.class));
            bind(ErrorInterceptor.class).to(TestHarness.class);
            bind(HttpClient.class).toProvider(HttpClientProvider.class).in(Scopes.SINGLETON);
            install(new JacksonModule().withConfigurer(ObjectIdToJSONConfigurer.class));
        }
    }

    static final class HttpClientProvider implements Provider<HttpClient> {

        private final ObjectMapper mapper;
        private HttpClient client;

        @Inject
        HttpClientProvider(ObjectMapper m) {
            this.mapper = m;
        }

        @Override
        public synchronized HttpClient get() {
            if (client == null) {
                client = HttpClient.builder().setObjectMapper(mapper).build();
            }
            return client;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ReentrantScope scope = new ReentrantScope();
        Settings settings = new SettingsBuilder("async")
                .add(GiuliusMongoAsyncModule.SETTINGS_KEY_DATABASE_NAME, "demo")
                .add(GiuliusMongoAsyncModule.SETTINGS_KEY_MONGO_HOST, "localhost")
                .add(GiuliusMongoAsyncModule.SETTINGS_KEY_MONGO_PORT, 27017)
                .build();

        Server server = new ServerBuilder("async", scope)
                .add(new ActeurMongoModule(scope)
                        .bindCollection("stuff", "stuff")
                        .registerJacksonType(Thing.class)
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
            final CountDownLatch latch = new CountDownLatch(1);
            ((MongoCollection) collection).insertMany(docs, new SingleResultCallback<Void>() {

                @Override
                public void onResult(Void t, Throwable thrwbl) {
                    if (thrwbl != null) {
                        failure = thrwbl;
                        thrwbl.printStackTrace();;
                    }
                    latch.countDown();
                }
            });
            try {
                latch.await(10, TimeUnit.SECONDS);
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
            next(stuff.withDocumentClass(ByteBuf.class).find(), new CursorControl().batchSize(220).limit(500).projection(
                    new Document("name", 1).append("rand", 1).append("_id", 1)));
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
                    new Document("name", 1).append("rand", 1).append("_id", 1)));
        }
    }

    @HttpCall
    @Path("/stuff/*")
    @Methods(PUT)
    @InjectRequestBodyAs(StringObjectMap.class)
    static class UpdateOneStuff extends Acteur {

        @Inject
        UpdateOneStuff(HttpEvent evt, MongoUpdater ud, @Named("stuff") MongoCollection<Document> stuff, StringObjectMap body) {
            ObjectId what = new ObjectId(evt.path().lastElement().toString());
            ud.withCollection(stuff.withWriteConcern(WriteConcern.FSYNC_SAFE)).updateOne(new Document("_id", what), body);
            next();
        }
    }


    @HttpCall
    @Path("/oneThing/*")
    @Methods(DELETE)
    static class DeleteOneStuff extends Acteur {

        @Inject
        DeleteOneStuff(HttpEvent evt, MongoUpdater ud, @Named("stuff") MongoCollection<Document> stuff) {
            ObjectId what = new ObjectId(evt.path().lastElement().toString());
            ud.withCollection(stuff.withWriteConcern(WriteConcern.FSYNC_SAFE)).deleteOne(new Document("_id", what));
            next();
        }
    }


    static boolean manythingsCalled;
    @HttpCall
    @Path("/manythings")
    @Methods(PUT)
    static class InsertManyStuff extends Acteur {

        @Inject
        InsertManyStuff(HttpEvent evt, MongoUpdater up, @Named("stuff") MongoCollection<Document> stuff) throws IOException {
            Thing[] things = evt.jsonContent(Thing[].class);
            if (things.length == 0) {
                badRequest("Not enough things");
                return;
            }
            up.withCollection(stuff.withDocumentClass(Thing.class))
                    .onSuccess(() -> {
                        System.out.println("SUCCESS");
                        manythingsCalled = true;
                        return null;
                    }).insertMany(Arrays.asList(things), null, CREATED, (Throwable t) -> {
                System.out.println("INSERT MANY DONE");
                if (t != null) {
                    System.out.println("THROWN!");
                    t.printStackTrace();
                }

            });
            next();
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
        public final ObjectId _id;

        public Thing(String name, int rand) {
            this.name = name;
            this.rand = rand;
            this._id = new ObjectId();
        }

        @JsonCreator
        public Thing(@JsonProperty("name") String name, @JsonProperty("rand") int rand, @JsonProperty(value = "_id", required = false) ObjectId _id) {
            this.name = name;
            this.rand = rand;
            this._id = notNull("_id", _id);
        }

        public String toString() {
            return name + ":" + rand;
        }
    }
}
