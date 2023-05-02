/*
 * The MIT License
 *
 * Copyright 2018 tim.
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
import com.mastfrog.acteur.mongo.async.TestBsonArrays.ArraysModule;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.mongodb.async.TestSupport;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.IfBinaryAvailable;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.jackson.DurationSerializationMode;
import com.mastfrog.jackson.TimeSerializationMode;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.time.TimeUtil;
import com.mongodb.async.client.MongoCollection;
import io.netty.buffer.ByteBufAllocator;
import static java.lang.System.currentTimeMillis;
import java.util.Arrays;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({ArraysModule.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class TestBsonArrays {

    static final ArrayPayloadThing AP1 = new ArrayPayloadThing("one",
            new Inner("oneA", currentTimeMillis()), new Inner("oneB", currentTimeMillis()));
    static final ArrayPayloadThing AP2 = new ArrayPayloadThing("two",
            new Inner("twoA", currentTimeMillis()), new Inner("twoB", currentTimeMillis()), new Inner("twoC", currentTimeMillis()));

    static final ArrayPayloadThing AP3 = new ArrayPayloadThing("three",
            new Inner("threeA", currentTimeMillis()), new Inner("threeB", currentTimeMillis()), new Inner("threeC", currentTimeMillis()));

    @Test(timeout=20000L)
    public void test(@Named("aps") MongoCollection<ArrayPayloadThing> coll) {
        TestSupport.await(ts -> {
            coll.insertMany(Arrays.asList(AP1, AP2), ts.callback(ur -> {
                ts.done();
            }));
        });

        TestSupport.await(ts -> {
            coll.insertOne(AP3, ts.callback(ur -> {
                ts.done();
            }));
        });

        TestSupport.await(ts -> {
            coll.find(new Document("name", "one")).first(ts.callback(item -> {
                assertEquals(AP1, item);
                ts.done();
            }));
        });
        TestSupport.await(ts -> {
            Inner nue = new Inner("threeD", currentTimeMillis());
            coll.updateOne(new Document("name", "three"), new Document("$addToSet", new Document("inners", nue)), ts.callback(ur -> {
                ts.done();
            }));
        });

        TestSupport.await(ts -> {
            coll.find(new Document("name", "three")).first(ts.callback(item -> {
                assertNotNull(item);
                assertNotNull(item.inners);
                assertEquals(4, item.inners.length);
                assertNotEquals(AP3, item);
                ts.done();
            }));
        });

        TestSupport.await(ts -> {
            Inner[] nue = new Inner[]{new Inner("replacement", currentTimeMillis())};
            coll.updateOne(new Document("name", "one"), new Document("$set", new Document("inners", nue)), ts.callback(ur -> {
                ts.done();
            }));
        });
        TestSupport.await(ts -> {
            coll.find(new Document("name", "one")).first(ts.callback(item -> {
                assertNotNull(item);
                assertNotNull(item.inners);
                assertEquals(1, item.inners.length);
                assertNotEquals(AP1, item);
                ts.done();
            }));
        });
    }

    static class ArraysModule extends AbstractModule {

        private final ReentrantScope scope = new ReentrantScope();

        @Override
        protected void configure() {
            ActeurMongoModule m = new ActeurMongoModule(scope)
                    .withJavaTimeSerializationMode(TimeSerializationMode.TIME_AS_EPOCH_MILLIS, DurationSerializationMode.DURATION_AS_MILLIS)
                    .bindCollection("aps", ArrayPayloadThing.class)
                    .registerJacksonTypes(ArrayPayloadThing.class, Inner.class, Inner[].class);

            bind(ByteBufAllocator.class).toInstance(ByteBufAllocator.DEFAULT);
            install(m);
        }
    }

    public static class ArrayPayloadThing {

        public final Inner[] inners;
        public final String name;
        public final ObjectId _id;

        @JsonCreator
        public ArrayPayloadThing(
                @JsonProperty("_id") ObjectId _id,
                @JsonProperty("name") String name,
                @JsonProperty("inners") Inner[] inners) {
            this._id = notNull("_id", _id);
            this.name = notNull("name", name);
            this.inners = inners == null ? new Inner[0] : inners;
        }

        public ArrayPayloadThing(String name, Inner... inners) {
            this.name = name;
            this.inners = inners;
            this._id = new ObjectId();
        }

        public boolean equals(Object o) {
            return o instanceof ArrayPayloadThing
                    && ((ArrayPayloadThing) o)._id.equals(_id)
                    && ((ArrayPayloadThing) o).name.equals(name)
                    && Arrays.equals(((ArrayPayloadThing) o).inners, inners);
        }

        public int hashCode() {
            return Objects.hash(name, inners);
        }

        public String toString() {
            return name + ":" + Strings.join(',', (Object[]) inners);
        }
    }

    public static final class Inner {

        public final String value;
        public final long dateCreated;

        @JsonCreator
        public Inner(@JsonProperty("value") String value,
                @JsonProperty("dateCreated") long dateCreated) {
            this.value = notNull("value", value);
            this.dateCreated = dateCreated;
        }

//        public Inner(String value) {
//            this(value, now());
//        }
        public boolean equals(Object o) {
            if (o instanceof Inner) {
                Inner i = (Inner) o;
                return value.equals(i.value)
                        && i.dateCreated == dateCreated;
            }
            return false;
        }

        public int hashCode() {
            return value.hashCode() + (73 * Long.valueOf(dateCreated).hashCode());
        }

        public String toString() {
            return value + "-" + TimeUtil.toIsoFormat(dateCreated);
        }
    }
}
