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
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import static com.mastfrog.acteur.mongo.async.ActeurMongoModule.JACKSON_BINDING_NAME;
import com.mastfrog.giulius.mongodb.async.DynamicCodecs;
import com.mastfrog.giulius.mongodb.async.Java8DateTimeCodecProvider;
import java.time.ZonedDateTime;
import javax.inject.Named;
import javax.inject.Provider;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
class JacksonCodecs implements DynamicCodecs {

    private final Provider<ObjectMapper> mapper;
    private final ByteBufCodec bufCodec;
    private final Java8DateTimeCodecProvider timeCodecs = new Java8DateTimeCodecProvider();

    @Inject
    JacksonCodecs(@Named(JACKSON_BINDING_NAME) Provider<ObjectMapper> mapper, ByteBufCodec bufCodec) {
        this.mapper = mapper;
        this.bufCodec = bufCodec;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Codec<T> createCodec(Class<T> type, CodecConfigurationException ex) {
        Codec<T> result = timeCodecs.createCodec(type, ex);
        if (result != null) {
            return result;
        }
        if (ObjectId.class == type) { // Don't let jackson take over objectids and store them as strings
            return (Codec<T>) OIDS;
        }
        if (ZonedDateTime.class == type) {
            return null;
        }
        return new JacksonCodec<>(mapper, Providers.of(bufCodec), type);
    }

    static final ObjectIdCodec OIDS = new ObjectIdCodec();

    static class ObjectIdCodec implements Codec<ObjectId> {

        @Override
        public void encode(BsonWriter writer, ObjectId t, EncoderContext ec) {
            writer.writeObjectId(t);
        }

        @Override
        public Class<ObjectId> getEncoderClass() {
            return ObjectId.class;
        }

        @Override
        public ObjectId decode(BsonReader reader, DecoderContext dc) {
            return reader.readObjectId();
        }
    }
}
