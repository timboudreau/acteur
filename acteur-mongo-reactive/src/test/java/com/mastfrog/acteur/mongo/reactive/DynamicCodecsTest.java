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
import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.mastfrog.acteur.mongo.reactive.ByteBufCodecTest.CB;
import com.mastfrog.acteur.mongo.reactive.DynamicCodecsTest.ArrayThing.SubThing;
import com.mastfrog.acteur.mongo.reactive.DynamicCodecsTest.M;
import com.mastfrog.giulius.mongodb.reactive.MongoHarness;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.IfBinaryAvailable;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.jackson.configuration.DurationSerializationMode;
import com.mastfrog.jackson.configuration.TimeSerializationMode;
import com.mastfrog.util.time.TimeUtil;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.netty.buffer.ByteBufAllocator;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.bson.Document;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class DynamicCodecsTest {

    private static final ZonedDateTime ZDT = TimeUtil.fromUnixTimestamp(0);

    @Test
    public void testZonedDateTime(@Named("stuff") MongoCollection<Document> stuff, Subscribers subscribers) throws Throwable {
        ObjectId id = new ObjectId();
        Document a = new Document("one", "hello").append("zdt", ZDT).append("thing", id);
        CB<Object> waitForInsert = new CB<>();
        stuff.withWriteConcern(WriteConcern.ACKNOWLEDGED)
                .insertMany(Arrays.asList(a))
                .subscribe(subscribers.first(waitForInsert));
        waitForInsert.get();

        Document doc = SRC.run((BiConsumer<Document, Throwable> cb) -> {
            stuff.find().subscribe(subscribers.first(cb));
        });

        assertNotNull(doc);
        Object hello = doc.get("one");
        assertNotNull(hello);
        Object zdt = doc.get("zdt");
        assertNotNull(zdt);
        Object thing = doc.get("thing");
        assertNotNull(thing);
        assertTrue(hello instanceof String);
        assertEquals("hello", hello);
        assertTrue(thing + " not right type: " + thing.getClass().getSimpleName(), doc.get("thing") instanceof ObjectId);
        assertTrue(zdt + " not right type: " + zdt.getClass().getSimpleName(), doc.get("zdt") instanceof Date);
        assertEquals(TimeUtil.toUnixTimestamp(ZDT), ((Date) zdt).getTime());
    }

    @Test
    public void testJacksonCodecs(@Named("stuff") MongoCollection<Document> stuff,
            Subscribers subscribers) throws Throwable {
        Document a = new Document("one", "hello").append("two", "\tworldпрод\nhmm");
        Document b = new Document("one", "stuff").append("two", "moreStuff");
        CB<Object> waitForInsert = new CB<>();
        stuff.withWriteConcern(WriteConcern.ACKNOWLEDGED).insertMany(
                Arrays.asList(a, b))
                .subscribe(subscribers.first(waitForInsert));
        waitForInsert.get();

        FindPublisher<Thing> f = stuff.find(Thing.class).batchSize(2);
        final List<Thing> things = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> th = new AtomicReference<>();

        subscribers.multiple(f)
                .whenComplete((list, thrown) -> {
                    if (thrown != null) {
                        th.set(thrown);
                    }
                    if (list != null) {
                        things.addAll(list);
                    }
                    latch.countDown();
                });

        latch.await(10, TimeUnit.SECONDS);
        Throwable t = th.get();
        if (t != null) {
            throw t;
        }
        assertFalse(things.isEmpty());
        List<Thing> expect = Arrays.asList(new Thing(null, "hello", "\tworldпрод\nhmm"), new Thing(null, "stuff", "moreStuff"));
        assertEquals(expect, things);
    }

    /*
    @Test
    public void testArrayPush(@Named("arr") CollectionPromises<ArrayThing> promises) throws Throwable {
        assertNotNull(promises);
        ArrayThing at = at("first", 0);
        insertOne(at, promises);
        ArrayThing found = findOne(promises);
        assertEquals("Stored and retrieved do not match", at, found);

        SubThing toPush = new SubThing("skiddoo", 23);
        SyncTrigger<UpdateResult> su = new SyncTrigger<>();
        promises.updateWithQuery().equal("name", at.name).build().push("subThings", toPush).build().updateOne().then(su).onFailure(su).start();
        UpdateResult res = su.get();
        assertNotNull(res);
        assertEquals(1, res.getMatchedCount());
        assertEquals(1, res.getModifiedCount());

        ArrayThing found2 = findOne(promises);
        assertNotNull(found2);
        assertEquals(1, found2.subThings.length);
        assertNotEquals(at, found2);

        ArrayThing found3 = findOne(promises, new Document("_id", at._id));
        assertNotNull(found3);
        assertEquals(found2, found3);

        Document d = findOne(promises.withType(Document.class));
        Object id = d.get("_id");
        assertNotNull(id);
        assertTrue("Wrong type for _id: " + id.getClass().getName(), id instanceof ObjectId);

        promises.updateWithQuery().equal("name", at.name).build().push("subThings", new Object[]{new SubThing("foo", 12), new SubThing("answer", 42)}).build().updateOne().then(su).onFailure(su).start();
        UpdateResult res2 = su.get();
        assertNotNull(res2);
        assertEquals(1, res2.getMatchedCount());
        assertEquals(1, res2.getModifiedCount());

        ArrayThing found4 = findOne(promises);
        assertNotNull(found4);
        assertEquals(3, found4.subThings.length);
    }

    static class SyncTrigger<T> implements Trigger<T>, SimpleLogic<T, Void>, FailureHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<T> ref = new AtomicReference<>();
        private final AtomicReference<Throwable> th = new AtomicReference<>();

        @Override
        public void trigger(T obj, Throwable thrown) {
            ref.set(obj);
            th.set(thrown);
            latch.countDown();
        }

        T get() throws Throwable {
            if (ref.get() == null) {
                latch.await(20, SECONDS);
            }
            if (th.get() != null) {
                throw th.get();
            }
            return ref.get();
        }

        @Override
        public void run(T obj, Trigger<Void> next) throws Exception {
            ref.set(obj);
            next.trigger(null, null);
            latch.countDown();
        }

        @Override
        public <T> boolean onFailure(PromiseContext.Key<T> key, T input, Throwable thrown, PromiseContext context) {
            th.set(thrown);
            latch.countDown();
            return true;
        }

    }

    private <T> void insertOne(T obj, CollectionPromises<T> promises) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] th = new Throwable[1];
        promises.insertOne().start(obj, new Trigger<Void>() {
            public void trigger(Void obj, Throwable thrown) {
                th[0] = thrown;
                latch.countDown();
            }
        });
        latch.await(10, SECONDS);
        if (th[0] != null) {
            Exceptions.chuck(th[0]);
        }
    }

    private <T> T findOne(CollectionPromises<T> promises) throws Throwable {
        return findOne(promises, new Document());
    }

    private <T> T findOne(CollectionPromises<T> promises, Document query) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<T> ref = new AtomicReference<>();
        final Throwable[] th = new Throwable[1];
        promises.find().findOne().start(query, new Trigger<T>() {

            @Override
            public void trigger(T obj, Throwable thrown) {
                ref.set(obj);
                if (thrown != null) {
                    th[0] = thrown;
                }
                latch.countDown();
            }
        }).onFailure(new FailureHandler() {

            @Override
            public <T> boolean onFailure(PromiseContext.Key<T> key, T input, Throwable thrown, PromiseContext context) {
                th[0] = thrown;
                return true;
            }
        });
        latch.await(10, SECONDS);
        if (th[0] != null) {
            Exceptions.chuck(th[0]);
        }
        return ref.get();
    }
     */
    public static class Thing {

        private final String _id;
        public final String one;
        public final String two;

        @JsonCreator
        public Thing(@JsonProperty("_id") String _id, @JsonProperty("one") String one, @JsonProperty("two") String two) {
            this._id = _id;
            this.one = one;
            this.two = two;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + Objects.hashCode(this.one);
            hash = 67 * hash + Objects.hashCode(this.two);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Thing other = (Thing) obj;
            if (!Objects.equals(this.one, other.one)) {
                return false;
            }
            if (!Objects.equals(this.two, other.two)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "Thing{" + "one=" + one + ", two=" + two + '}';
        }
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            ActeurMongoModule m = new ActeurMongoModule(new ReentrantScope()).withCodec(ByteBufCodec.class)
                    .bindCollection("stuff")
                    .bindCollection("arr", ArrayThing.class)
                    .withJavaTimeSerializationMode(TimeSerializationMode.TIME_AS_ISO_STRING, DurationSerializationMode.DURATION_AS_MILLIS)
                    //                    .registerJacksonType(ZonedDateTime.class)
                    .registerJacksonType(SubThing.class);
            bind(ByteBufAllocator.class).toInstance(ByteBufAllocator.DEFAULT);
            bind(Thread.UncaughtExceptionHandler.class).toInstance((thread, thrown) -> {
                thrown.printStackTrace();
            });

//            install(new JacksonModule(ActeurMongoModule.JACKSON_BINDING_NAME, false).withConfigurer(ObjectIdJacksonConfigurer.class));
            install(m);
        }
    }

    static int ix;

    static ArrayThing at(String name, int count) {
        SubThing[] things = new SubThing[count];
        for (int i = 0; i < things.length; i++) {
            things[i] = new SubThing(name + "-" + i, i * 5);
        }
        return new ArrayThing(new ObjectId(), name, things);
    }

    public static class ArrayThing {

        public final String name;
        public final SubThing[] subThings;
        public final ObjectId _id;

        @JsonCreator
        public ArrayThing(@JsonProperty("_id") ObjectId _id, @JsonProperty("name") String name, @JsonProperty("subThings") SubThing[] subThings) {
            this.name = name;
            this.subThings = subThings;
            this._id = _id;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 17 * hash + Objects.hashCode(this.name);
            hash = 17 * hash + Arrays.deepHashCode(this.subThings);
            hash = 17 * hash + Objects.hashCode(this._id);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ArrayThing other = (ArrayThing) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Arrays.deepEquals(this.subThings, other.subThings)) {
                return false;
            }
            if (!Objects.equals(this._id, other._id)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "ArrayThing{" + "name=" + name + ", subThings=" + Arrays.toString(subThings) + ", _id=" + _id + '}';
        }

        public static class SubThing {

            public final String name;
            public final int index;

            @JsonCreator
            public SubThing(@JsonProperty("name") String name, @JsonProperty("index") int index) {
                this.name = name;
                this.index = index;
            }

            @Override
            public int hashCode() {
                int hash = 7;
                hash = 41 * hash + Objects.hashCode(this.name);
                hash = 41 * hash + this.index;
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final SubThing other = (SubThing) obj;
                if (this.index != other.index) {
                    return false;
                }
                if (!Objects.equals(this.name, other.name)) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                return "SubThing{" + "name=" + name + ", index=" + index + '}';
            }

        }
    }
}
