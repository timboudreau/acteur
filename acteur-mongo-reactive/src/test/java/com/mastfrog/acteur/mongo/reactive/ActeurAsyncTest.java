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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.mime.MimeType;
import static com.mastfrog.mime.MimeType.JSON_UTF_8;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.annotations.Concluders;
import com.mastfrog.acteur.annotations.GenericApplication;
import com.mastfrog.acteur.annotations.GenericApplicationModule;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.debug.Probe;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.DELETE;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.mongo.reactive.ActeurAsyncTest.M;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteurbase.ActeurState;
import com.mastfrog.giulius.mongodb.reactive.DynamicCodecs;
import com.mastfrog.giulius.mongodb.reactive.MongoAsyncInitializer;
import com.mastfrog.giulius.mongodb.reactive.MongoHarness;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.collections.StringObjectMap;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import org.bson.Document;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, TestHarnessModule.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class ActeurAsyncTest {

    private static final long TEST_TIMEOUT = 18000L;
    private static final Duration HTTP_TIMEOUT = Duration.ofMillis(TEST_TIMEOUT - 2000);

    static {
        /* for debugging */
        System.setProperty("acteur.debug", "false");
        System.setProperty("mongo.tmplog", "false");
    }

//    @Test(timeout = TEST_TIMEOUT)
    public void testSanityCheck(TestHarness harn) throws Throwable {
        harn.get("/hello").go().assertStatus(OK).assertContent("Hello world");
    }

//    @Test(timeout = TEST_TIMEOUT)
    public void testDynamicCodecsBound(DynamicCodecs c) {
        assertTrue("Wrong codecs: " + c.getClass().getName() + ": " + c,
                c instanceof JacksonCodecs);
    }

//    @Test(timeout = TEST_TIMEOUT)
    public void testEmptyCollectionReturnsEmptyArray(TestHarness harn, ObjectMapper mapper, @Named("stuff") MongoCollection<Document> stuff) throws Throwable {
        String s = harn.get("/emptiness")
                .setTimeout(HTTP_TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .throwIfError()
                .content();

        assertEquals("Did not get empty array", "[]", s.trim());
    }

//    @Test(timeout = TEST_TIMEOUT)
    public void testThingCollection(TestHarness harn, ObjectMapper mapper, @Named("stuff") MongoCollection<Document> stuff) throws Throwable {
        String s = harn.get("/stuff")
                .setTimeout(HTTP_TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .throwIfError()
                .content();

        assertNotNull("Got null string from /stuff", s);
        Thing[] objs = mapper.readValue(s, Thing[].class);

        assertEquals(Arrays.toString(objs), objs.length, 200);
        maybeFail();
    }

//    @Test(timeout = TEST_TIMEOUT)
    public void testOneThing(TestHarness harn, ObjectMapper mapper, @Named("stuff") MongoCollection<Document> stuff) throws Throwable {
        Thing one = harn.get("/oneThing")
                .setTimeout(HTTP_TIMEOUT)
                .go().assertStatus(OK).throwIfError().content(Thing.class);

        assertNotNull("Null thing", one);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void test(TestHarness harn, ObjectMapper mapper, @Named("stuff") MongoCollection<Document> stuff, Subscribers subscribers) throws Throwable {
        Thing one = harn.get("/oneThing")
                .setTimeout(HTTP_TIMEOUT)
                .go().assertStatus(OK).throwIfError().content(Thing.class);

        assertNotNull("Null thing", one);

        StringObjectMap m = harn.delete("oneThing", one._id.toHexString())
                .setTimeout(HTTP_TIMEOUT)
                .go().assertStatus(OK).throwIfError().content(StringObjectMap.class);

        assertEquals(1, m.get("count"));
        assertEquals(Boolean.TRUE, m.get("acknowledged"));

        String s = harn.get("/stuff")
                .setTimeout(HTTP_TIMEOUT)
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
                .setBody(more, MimeType.JSON_UTF_8)
                .setTimeout(HTTP_TIMEOUT)
                .go()
                .await()
                .assertStatus(CREATED).throwIfError().content(Thing[].class);

        assertTrue(manythingsCalled);

        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] th = new Throwable[1];
        Document[] d = new Document[1];
        stuff.find(new Document("name", "skiddoo")).subscribe(subscribers.first((Document t, Throwable thrwbl) -> {
            th[0] = thrwbl;
            d[0] = t;
            latch.countDown();
        }));
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

        Thing retrievdUpdated = harn.get("/oneThing/" + things3[0]._id.toHexString())
                .setTimeout(HTTP_TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .throwIfError()
                .content(Thing.class);

        assertNotNull(retrievdUpdated);
        assertEquals("gerbil", retrievdUpdated.name);
        assertEquals(things3[0].rand, retrievdUpdated.rand);

        s = harn.get("/stuff")
                .setTimeout(Duration.ofSeconds(4))
                .go()
                .await()
                .assertStatus(OK).throwIfError().content();

        Thing[] things4 = mapper.readValue(s, Thing[].class);

        System.out.println("Have " + things4.length + " things");

        assertEquals(201, things3.length);
        for (Thing t : things4) {
            if (things3[0]._id.equals(t._id)) {
                assertEquals("wrong name", "gerbil", t.name);
                assertEquals("wrong int value", things3[0].rand, t.rand);
            }
        }

        System.out.println("Now do the problem thing");

        CallResult cr = harn.get("/etagstuff")
                .setTimeout(HTTP_TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertHasHeader(Headers.ETAG)
                .assertHasHeader(Headers.LAST_MODIFIED)
                .throwIfError();
        s = cr.content();
        Thing[] things5 = mapper.readValue(s, Thing[].class);

        assertSets(
                new HashSet<>(Arrays.asList(things4)),
                new HashSet<>(Arrays.asList(things5)));

        String etag = cr.getHeader(Headers.ETAG).toString();

        harn.get("/etagstuff")
                .addHeader(Headers.IF_NONE_MATCH, etag)
                .setTimeout(Duration.ofSeconds(4))
                .go()
                .await()
                .assertStatus(NOT_MODIFIED)
                .throwIfError();

    }

    private static <T extends Comparable<T>> void assertSets(Set<T> expect, Set<T> got) {
        if (!expect.equals(got)) {
            Set<T> absent = new TreeSet<>(expect);
            absent.removeAll(got);
            Set<T> surprises = new TreeSet<>(got);
            surprises.removeAll(expect);
            fail("Sets do not match.  Absent: " + absent + " and unexpected " + surprises);
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
                    .bindCollection("emptiness", "emptiness")
                    .withInitializer(Populator.class));
            install(new GenericApplicationModule<>(scope, settings, GenericApplication.class));
            bind(ErrorInterceptor.class).to(TestHarness.class);
            install(new JacksonModule(true)
                    .withConfigurer2(ObjectIdToJSONConfigurer.class));
            bind(HttpClient.class).toProvider(HttpClientProvider.class)
                    .in(Scopes.SINGLETON);
            
//            bind(Probe.class).to(ProbeImpl.class);
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
        System.setProperty("acteur.debug", "true");
        System.setProperty("cursorwriter.newlines", "true");

        ReentrantScope scope = new ReentrantScope();
        Settings settings = new SettingsBuilder("async")
                //                .add(GiuliusMongoReactiveStreamsModule.SETTINGS_KEY_DATABASE_NAME, "demo")
                //                .add(GiuliusMongoReactiveStreamsModule.SETTINGS_KEY_MONGO_HOST, "localhost")
                //                .add(GiuliusMongoReactiveStreamsModule.SETTINGS_KEY_MONGO_PORT, 27017)
                .build();

        Server server = new ServerBuilder("async", scope)
                .add(new MongoHarness.Module())
                .add(new ActeurMongoModule(scope)
                        .bindCollection("stuff", "stuff")
                        .bindCollection("emptiness", "emptiness")
                        .registerJacksonType(Thing.class)
                        .withInitializer(Populator.class)
                )
                .add(settings)
                .build();
        server.start().await();
    }

    static Throwable failure;

    static class Populator extends MongoAsyncInitializer {

        private final Subscribers subscribers;

        @Inject
        Populator(Registry reg, Subscribers subscribers) {
            super(reg);
            this.subscribers = subscribers;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onCreateCollection(String name, MongoCollection<?> collection) {
            if ("emptiness".equals(name)) {
                return;
            }
            List<Document> docs = new LinkedList<>();
            Random r = new Random(209024);
            for (int i = 0; i < 200; i++) {
                Document doc = new Document("name", "Thing-" + i).append("rand", r.nextInt(100));
                docs.add(doc);
            }
            final CountDownLatch latch = new CountDownLatch(1);
            collection.withDocumentClass(Document.class)
                    .insertMany(docs).subscribe(
                    subscribers.first((t, thrwbl) -> {
                        if (thrwbl != null) {
                            failure = thrwbl;
                            thrwbl.printStackTrace();
                        }
                        latch.countDown();
                    }));
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Exceptions.chuck(ex);
            }
            if (failure != null) {
                Exceptions.chuck(failure);
            }
        }
    }

    @HttpCall
    @Path("/stuff")
    @Methods(GET)
    @Concluders(WriteCursorContentsAsJSON.class)
    static class GetAllStuff extends Acteur {

        @Inject
        GetAllStuff(@Named("stuff") MongoCollection<Document> stuff,
                CursorControl ctrl) {
            next(stuff.withDocumentClass(ByteBuf.class).find(),
                    ctrl.batchSize(10).limit(500).projection(
                            new Document("name", 1).append("rand", 1).append("_id", 1)));
        }
    }

    @HttpCall
    @Path("/emptiness")
    @Methods(GET)
    @Concluders(WriteCursorContentsAsJSON.class)
    static class GetEmptiness extends Acteur {

        @Inject
        GetEmptiness(@Named("emptiness") MongoCollection<Document> stuff,
                CursorControl ctrl) {
            next(stuff.withDocumentClass(ByteBuf.class).find(),
                    ctrl.batchSize(10).limit(500));
        }
    }

    @HttpCall
    @Path("/etagstuff")
    @Methods(GET)
    @Concluders(QueryWithCacheHeadersActeur.class)
    static class GetAllStuffWithETag extends Acteur {

        @Inject
        GetAllStuffWithETag(@Named("stuff") MongoCollection<Document> stuff, CursorControl ctrl) {
            next(stuff.withDocumentClass(ByteBuf.class), new Document(),
                    ctrl.batchSize(10).limit(500),
                    new CacheHeaderInfo("name", "_id")
                            .setLastModifiedField("lastModified"));
        }
    }

    @HttpCall
    @Path("/oneThing")
    @Methods(GET)
    @Concluders(QueryActeur.class)
    static class GetOneStuff extends Acteur {

        @Inject
        GetOneStuff(@Named("stuff") MongoCollection<Document> stuff) {
            next(stuff, new Document(), new CursorControl().findOne(true).projection(
                    new Document("name", 1).append("rand", 1).append("_id", 1)));
        }
    }

    @HttpCall
    @Path("/oneThing/*")
    @Methods(GET)
    @Concluders(QueryActeur.class)
    static class GetOneStuffById extends Acteur {

        @Inject
        GetOneStuffById(@Named("stuff") MongoCollection<Document> stuff, HttpEvent evt) {
            if (!ObjectId.isValid(evt.path().lastElement().toString())) {
                badRequest(Err.badRequest("Invalid id"));
                return;
            }
            ObjectId id = new ObjectId(evt.path().lastElement().toString());
            next(stuff, new Document("_id", id), new CursorControl().findOne(true).projection(
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
            ud.withCollection(stuff.withWriteConcern(WriteConcern.ACKNOWLEDGED)).updateOne(new Document("_id", what), body);
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
            ud.withCollection(stuff.withWriteConcern(WriteConcern.ACKNOWLEDGED)).deleteOne(new Document("_id", what));
            next();
        }
    }

    static boolean manythingsCalled;

    @HttpCall
    @Path("/manythings")
    @Methods(PUT)
    static class InsertManyStuff extends Acteur {

        @Inject
        InsertManyStuff(HttpEvent evt, MongoUpdater up, @Named("stuff") MongoCollection<Document> stuff) throws Exception {
            Thing[] things = evt.jsonContent(Thing[].class);
            if (things.length == 0) {
                badRequest("Not enough things");
                return;
            }
            up.withCollection(stuff.withDocumentClass(Thing.class))
                    .onSuccess(() -> {
                        manythingsCalled = true;
                        return null;
                    }).insertMany(Arrays.asList(things), null, CREATED, (Throwable t) -> {
                if (t != null) {
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
            add(Headers.CONTENT_TYPE, MimeType.PLAIN_TEXT_UTF_8);
            ok("Hello world");
        }
    }

    static long when = 1496247701503L;

    static Date nextLastModified() {
        Date result = new Date(when);
        when = Instant.ofEpochMilli(when).plus(Duration.ofDays(1))
                .toEpochMilli();
        return result;
    }

    public static class Thing implements Comparable<Thing> {

        public final String name;
        public final int rand;
        public final ObjectId _id;
        public final Date lastModified;

        public Thing(String name, int rand) {
            this.name = name;
            this.rand = rand;
            this._id = new ObjectId();
            this.lastModified = nextLastModified();
        }

        @Override
        public int compareTo(Thing other) {
            int result = name.compareTo(other.name);
            if (result == 0) {
                result = Integer.compare(rand, other.rand);
            }
            if (result == 0) {
                Date lm1 = lastModified == null ? new Date(0) : lastModified;
                Date lm2 = other.lastModified == null ? new Date(0) : other.lastModified;
                result = lm1.compareTo(lm2);
            }
            if (result == 0) {
                result = _id.compareTo(other._id);
            }
            return result;
        }

        @JsonCreator
        public Thing(@JsonProperty("name") String name,
                @JsonProperty("rand") int rand,
                @JsonProperty(value = "_id", required = true) ObjectId _id,
                @JsonProperty("lastModified") Date lastModified) {
            this.name = name;
            this.rand = rand;
            this._id = notNull("_id", _id);
            this.lastModified = lastModified;
        }

        @Override
        public String toString() {
            return name + ":" + rand + ":" + (lastModified == null ? "--" : Instant.ofEpochMilli(lastModified.getTime()))
                    + ":" + (_id == null ? "--" : _id.toHexString());
        }

        @Override
        public int hashCode() {
            int hash = 5;
//            hash = 37 * hash + Objects.hashCode(this.name);
//            hash = 37 * hash + this.rand;
            hash = 37 * hash + Objects.hashCode(this._id);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || obj.getClass() != Thing.class) {
                return false;
            }
//            if (getClass() != obj.getClass()) {
//                return false;
//            }
            final Thing other = (Thing) obj;
            return Objects.equals(_id, other._id);
//            if (this.rand != other.rand) {
//                return false;
//            }
//            if (!Objects.equals(this.name, other.name)) {
//                return false;
//            }
//            if (!Objects.equals(this._id, other._id)) {
//                return false;
//            }
//            return true;
        }
    }

    static class ProbeImpl implements Probe {

        @Override
        public void onBeforeProcessRequest(RequestID id, Event<?> req) {

        }

        @Override
        public void onBeforeRunPage(RequestID id, Event<?> evt, Page page) {

        }

        @Override
        public void onActeurWasRun(RequestID id, Event<?> evt, Page page, Acteur acteur, ActeurState result) {
            System.out.println("wasRun " + ((HttpEvent) evt).path() + " " + acteur.getClass().getSimpleName() + " state " + result);
        }

        @Override
        public void onFallthrough(RequestID id, Event<?> evt) {
            System.out.println("FALLTHROUGH! " + ((HttpEvent) evt).path());
        }

        @Override
        public void onInfo(String info, Object... objs) {
            System.out.println(MessageFormat.format(info, objs));
        }

        @Override
        public void onThrown(RequestID id, Event<?> evt, Throwable thrown) {
            System.out.println("THROWN! " + ((HttpEvent) evt).path());
            thrown.printStackTrace();
        }

        @Override
        public void onBeforeSendResponse(RequestID id, Event<?> evt, Acteur acteur,
                HttpResponseStatus status, boolean hasListener, Object message) {
            System.out.println("BEFORE SEND RESP! " + ((HttpEvent) evt).path()
                    + " " + acteur.getClass().getSimpleName() + status + " lis " + hasListener
                    + " msg " + message);
        }

    }
}
