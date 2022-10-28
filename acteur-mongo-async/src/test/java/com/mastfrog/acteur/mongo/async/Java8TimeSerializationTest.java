/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.DurationSerializationMode;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.jackson.TimeSerializationMode;
import com.mastfrog.util.time.TimeUtil;
import static com.mastfrog.util.time.TimeUtil.ISO_INSTANT;
import com.mongodb.WriteConcern;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoCollection;
import io.netty.buffer.ByteBufAllocator;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({Java8TimeSerializationTest.M.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class Java8TimeSerializationTest {

    @Inject
    ObjectMapper mapper;

    private Map<String, Object> toMap(Object o) throws JsonProcessingException, IOException {
        return mapper.readValue(mapper.writeValueAsBytes(o), new TypeReference<Map<String, Object>>() {
        });
    }

    @Test(timeout=20000L)
    @SuppressWarnings("deprecation")
    public void testJava8ElementsInDocument(@Named("more") MongoCollection<Document> c) throws IOException, Throwable {
        Document d = new Document("now", ZonedDateTime.now()).append("offset", OffsetDateTime.now())
                .append("local", LocalDateTime.now());

        SRC.<Void>run((SingleResultCallback<Void> cb) -> {
            c.withWriteConcern(WriteConcern.FSYNC_SAFE).insertOne(d, cb);
        });

        Document g = SRC.<Document>run((SingleResultCallback<Document> cb) -> {
            c.find().first(cb);
        });
        assertNotNull(g);

        assertTrue(g.get("now") instanceof Date);
        assertTrue(g.get("offset") instanceof Date);
        assertTrue(g.get("local") instanceof Date);
    }

    @Test(timeout=20000L)
    public void testTimeSerialization(@Named("times") MongoCollection<TimeThing> c) throws IOException, Throwable {
        TimeThing tt = new TimeThing();
        Map<String, Object> m = toMap(tt);

        assertEquals("2017-10-28T21:53:56.988Z", m.get("when"));
        assertEquals("02:03.000", m.get("duration"));

        TimeThing tt2 = mapper.readValue(mapper.writeValueAsBytes(tt), TimeThing.class);

        assertEquals(tt, tt2);

        SRC.<Void>run((SingleResultCallback<Void> cb) -> {
            c.insertOne(tt, cb);
        });

        Document d = SRC.<Document>run((SingleResultCallback<Document> sd) -> {
            c.withDocumentClass(Document.class).find().first(sd);
        });

        assertNotNull(d);

        Object o = d.get("when");
        assertNotNull(o);
        assertTrue(o instanceof java.util.Date);

        TimeThing tt3 = SRC.<TimeThing>run((SingleResultCallback<TimeThing> cb) -> {
            c.find().first(cb);
        });
        assertNotNull(tt3);
        assertEquals(tt, tt3);

        LocalDateTime dateTime = LocalDateTime.ofInstant(tt.when.toInstant(), ZoneId.of("GMT"));
        long millis = dateTime.toInstant(ZoneId.of("GMT").getRules().getOffset(dateTime)).toEpochMilli();

        assertEquals(1509227636988L, millis);
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            ActeurMongoModule m = new ActeurMongoModule(new ReentrantScope()).withCodec(ByteBufCodec.class)
                    .bindCollection("times", TimeThing.class)
                    .bindCollection("more")
                    .withJavaTimeSerializationMode(TimeSerializationMode.TIME_AS_ISO_STRING, DurationSerializationMode.DURATION_AS_STRING);
            bind(ByteBufAllocator.class).toInstance(ByteBufAllocator.DEFAULT);
            install(new JacksonModule().withJavaTimeSerializationMode(TimeSerializationMode.TIME_AS_ISO_STRING, DurationSerializationMode.DURATION_AS_STRING));
            install(m);
        }
    }

    static final class TimeThing {

        public Duration duration = Duration.ofSeconds(3).plus(Duration.ofMinutes(2));
//        public ZonedDateTime when = ZonedDateTime.parse("2017-10-28T21:53:56.988Z", ISO_INSTANT); // 1509227636988
        public ZonedDateTime when = TimeUtil.fromUnixTimestamp(1509227636988L); // .withZoneSameLocal(ZoneId.of("GMT"));
        public ObjectId _id;

        public TimeThing() {
            _id = new ObjectId();
        }

        @JsonCreator
        public TimeThing(@JsonProperty(value = "duration", required = true) Duration duration,
                @JsonProperty(value = "when", required = true) ZonedDateTime when, @JsonProperty("_id") ObjectId _id) {
            this.duration = duration;
            this.when = when;
            this._id = _id;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + Objects.hashCode(this.duration);
            hash = 79 * hash + Objects.hashCode(this.when);
            hash = 79 * hash + Objects.hashCode(this._id);
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
            final TimeThing other = (TimeThing) obj;
            if (!Objects.equals(this.duration, other.duration)) {
                return false;
            }
            if (!Objects.equals(this._id, other._id)) {
                return false;
            }
            if (this.when != null && other.when != null) {
                return TimeUtil.toUnixTimestamp(this.when) == TimeUtil.toUnixTimestamp(other.when);
            }

            if (!Objects.equals(this.when, other.when)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "TimeThing{" + "duration=" + TimeUtil.format(duration) + ", when=" + when.format(ISO_INSTANT) + ", _id=" + _id + '}';
        }
    }
}
