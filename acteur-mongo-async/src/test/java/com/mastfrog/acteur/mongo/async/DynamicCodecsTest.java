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
import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.mastfrog.acteur.mongo.async.ByteBufCodecTest.CB;
import com.mastfrog.acteur.mongo.async.DynamicCodecsTest.M;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoCollection;
import io.netty.buffer.ByteBufAllocator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, MongoHarness.Module.class})
public class DynamicCodecsTest {

    @Test
    public void testJacksonCodecs(@Named("stuff") MongoCollection<Document> stuff) throws Throwable {
        Document a = new Document("one", "hello").append("two", "world");
        Document b = new Document("one", "stuff").append("two", "moreStuff");
        CB<Void> waitForInsert = new CB<Void>();
        stuff.insertMany(Arrays.asList(a, b), waitForInsert);
        waitForInsert.get();

        FindIterable<Thing> f = stuff.find(Thing.class).batchSize(2);
        final List<Thing> things = Collections.synchronizedList(new ArrayList<Thing>());
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> th = new AtomicReference<>();
        f.batchCursor(new SingleResultCallback<AsyncBatchCursor<Thing>>() {

            @Override
            public void onResult(AsyncBatchCursor<Thing> t, Throwable thrwbl) {
                t.next(new SingleResultCallback<List<Thing>>() {

                    @Override
                    public void onResult(List<Thing> t, Throwable thrwbl) {
                        if (thrwbl != null) {
                            th.set(thrwbl);
                        }
                        things.addAll(t);
                        latch.countDown();;
                    }
                });
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        Throwable t = th.get();
        if (t != null) {
            throw t;
        }
        assertFalse(things.isEmpty());
        List<Thing> expect = Arrays.asList(new Thing(null, "hello", "world"), new Thing(null, "stuff", "moreStuff"));
        assertEquals(expect, things);
    }

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
            GiuliusMongoAsyncModule m = new GiuliusMongoAsyncModule().withCodec(ByteBufCodec.class)
                    .bindCollection("stuff").withDynamicCodecs(JacksonCodecs.class);
            bind(ByteBufAllocator.class).toInstance(ByteBufAllocator.DEFAULT);
            install(m);
        }

    }
}
