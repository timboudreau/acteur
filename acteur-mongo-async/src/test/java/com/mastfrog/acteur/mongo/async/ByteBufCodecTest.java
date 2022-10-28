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
import com.google.inject.name.Named;
import com.mastfrog.acteur.mongo.async.ByteBufCodecTest.M;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.Streams;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoCollection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import java.io.IOException;
import static java.util.Arrays.asList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class ByteBufCodecTest {

    private static final CodecRegistry DEFAULT_CODEC_REGISTRY
            = fromProviders(asList(new ValueCodecProvider(),
                    new DocumentCodecProvider(),
                    new BsonValueCodecProvider()));

    @Test(timeout=20000L)
    public void test(@Named("stuff") MongoCollection<Document> stuff) throws InterruptedException, IOException {
        byte[] bytes = new byte[120];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (120 - i);
        }

        List<Object> list = new LinkedList<>();
        list.add(1);
        list.add(2);
        list.add(2);
        list.add("hello");
        list.add(new Document("in", "list"));

        Document document = new Document("hello", "world")
                .append("boolValue", true)
                .append("list", list)
                .append("intValue", 32)
                .append("dbl", 32.000235D)
                .append("subdoc", new Document("other", "stuff"))
                .append("longValue", Long.valueOf(Integer.MAX_VALUE + 1L))
                .append("_id", new ObjectId())
                .append("bytes", bytes);

        CB<Void> cb = new CB<Void>();
        stuff.insertOne(document, cb);
        cb.get();
        Thread.sleep(200);

        MongoCollection<ByteBuf> bbc = stuff.withDocumentClass(ByteBuf.class);
        CB<ByteBuf> bb = new CB<ByteBuf>();
        bbc.find(new Document("_id", document.get("_id")))
                .batchSize(1)
                .projection(new Document("hello", 1)
                        .append("boolValue", 1)
                        .append("intValue", 1)
                        .append("longValue", 1)
                        .append("dbl", 1)
                        .append("list", 1)
                        .append("subdoc", 1)
                        .append("bytes", 1)
                        .append("_id", 1))
                .first(bb);
        ByteBuf buf = bb.get();
        assertNotNull(buf);

        String content = Streams.readString(new ByteBufInputStream(buf));
        buf.resetReaderIndex();

        Map m = new ObjectMapper().readValue(content, Map.class);
        for (Map.Entry<String, Object> e : document.entrySet()) {
            Object other = m.get(e.getKey());
            assertNotNull(e.getKey() + " is null", other);
            Object value = e.getValue();
            assertNotNull(value);
            switch (e.getKey()) {
                case "_id":
                    assertTrue(value instanceof ObjectId);
                    assertTrue(other instanceof String);
                    value = value.toString();
                    break;
                case "bytes":
                    assertTrue(other.getClass().getName(), other instanceof String);
                    other = Base64.getDecoder().decode((String)other);
                    continue;
            }
            if (value instanceof byte[]) {
                assertTrue(other instanceof byte[]);
                assertTrue(value instanceof byte[]);
                assertArrayEquals((byte[]) value, (byte[]) other);
            } else {
                assertEquals(other, value);
            }
        }
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            GiuliusMongoAsyncModule m = new GiuliusMongoAsyncModule().withCodec(ByteBufCodec.class)
                    .bindCollection("stuff");
            bind(ByteBufAllocator.class).toInstance(ByteBufAllocator.DEFAULT);
            install(m);
        }
    }

    static class CB<T> implements SingleResultCallback<T> {

        private T result;
        private volatile boolean done;
        private Throwable thrown;

        public T get() throws InterruptedException {
            synchronized (this) {
                do {
                    T res = result;
                    if (res != null) {
                        return res;
                    }
                    if (thrown != null) {
                        Exceptions.chuck(thrown);
                    }
                    wait(500L);
                } while (!done);
            }
            return result;
        }

        @Override
        public synchronized void onResult(T t, Throwable thrwbl) {
            result = t;
            if (thrwbl != null) {
                thrwbl.printStackTrace();
            }
            done = true;
            thrown = thrwbl;
            notifyAll();
        }
    }
}
