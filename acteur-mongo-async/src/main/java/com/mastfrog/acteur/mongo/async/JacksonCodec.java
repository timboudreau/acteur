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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import static com.mastfrog.acteur.mongo.async.ActeurMongoModule.JACKSON_BINDING_NAME;
import static com.mastfrog.util.Exceptions.chuck;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Named;
import javax.inject.Provider;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;

/**
 * Uses Jackson to enable MongoDB to support types without writing custom codecs
 * for them.
 *
 * @author Tim Boudreau
 * @param <T>The type
 */
final class JacksonCodec<T> implements Codec<T> {

    private final Provider<ObjectMapper> mapper;
    private final Provider<ByteBufCodec> json;
    private final Class<T> type;

    @Inject
    JacksonCodec(@Named(JACKSON_BINDING_NAME) Provider<ObjectMapper> mapper, Provider<ByteBufCodec> json, Class<T> type) {
        this.mapper = mapper;
        this.json = json;
        this.type = type;
    }

    @Override
    public void encode(BsonWriter writer, T t, EncoderContext ec) {
        if (t instanceof ZonedDateTime) { // XXX hack
            json.get().writeDateTime(writer, (ZonedDateTime) t, ec);
            return;
        }
        if (t instanceof Enum<?>) {
            writer.writeString(((Enum<?>) t).name());
            return;
        }
        if (t instanceof ObjectId) {
            writer.writeObjectId((ObjectId) t);
            return;
        }
        try {
            byte[] bytes = mapper.get().writeValueAsBytes(t);
            json.get().encode(writer, Unpooled.wrappedBuffer(bytes), ec);
        } catch (JsonProcessingException ex) {
            ex.printStackTrace();
            chuck(ex);
        }
    }

    @Override
    public Class<T> getEncoderClass() {
        return type;
    }

    static AtomicInteger counter = new AtomicInteger();

    private void debugWrite(ByteBuf buf) {
        buf.resetReaderIndex();
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            File file = new File(System.getProperty("java.io.tmpdir") + File.separator + counter.incrementAndGet() + ".json");
            try (FileOutputStream out = new FileOutputStream(file)) {
                Streams.copy(in, out);
            }
            System.err.println("Wrote debug data " + file);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            buf.resetReaderIndex();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T decode(BsonReader reader, DecoderContext dc) {
//        switch(reader.getCurrentBsonType()) {
//            case DATE_TIME :
//                return (T) TimeUtil.fromUnixTimestamp(reader.readDateTime());
//            case TIMESTAMP :
//                return (T) TimeUtil.fromUnixTimestamp(reader.readTimestamp().asInt64().longValue());
//        }
        if (reader.getCurrentBsonType() != null) { // May be null with older MongoDB
            switch (reader.getCurrentBsonType()) {
                case OBJECT_ID:
                    return (T) reader.readObjectId();
            }
        }
        ByteBuf buf = json.get().decode(reader, dc);
        try {
            T result = mapper.get().readValue((InputStream) new ByteBufInputStream(buf), type);
            return result;
        } catch (JsonMappingException ex) {
            debugWrite(buf);
            buf.resetReaderIndex();
            try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
                JsonMappingException nue = new JsonMappingException(ex.getMessage() + " - JSON:\n" + Streams.readString(in));
                nue.initCause(ex);
                throw nue;
            } catch (IOException ex1) {
                if (ex1 instanceof JsonMappingException) {
                    return chuck(ex1);
                } else {
                    ex1.addSuppressed(ex);
                    return chuck(ex1);
                }
            }
        } catch (IOException ex) {
            return chuck(ex);
        }
    }
}
