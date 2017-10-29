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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.util.time.TimeUtil;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.bson.types.ObjectId;

/**
 * Writes MongoDB enhanced JSON for ObjectId fields, so that indexes work the
 * way they are supposed to.
 *
 * @author Tim Boudreau
 */
class ObjectIdJacksonConfigurer implements JacksonConfigurer {

    @Override
    public ObjectMapper configure(ObjectMapper m) {
        SimpleModule sm = new SimpleModule();
        sm.addSerializer(OID);
        sm.addSerializer(ZDT);
        sm.addSerializer(LDT);
        sm.addSerializer(ODT);
        m.registerModule(sm);
        return m;
    }

    private static final OIDSerializer OID = new OIDSerializer();

    static final class OIDSerializer extends JsonSerializer<ObjectId> {

        @Override
        public Class<ObjectId> handledType() {
            return ObjectId.class;
        }

        @Override
        public void serialize(ObjectId t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            String raw = "ObjectId(\"" + t.toString() + "\")";
            jg.writeRawValue(raw);
        }
    }

    private static final ZDTSerializer ZDT = new ZDTSerializer();

    static final class ZDTSerializer extends JsonSerializer<ZonedDateTime> {

        @Override
        public Class<ZonedDateTime> handledType() {
            return ZonedDateTime.class;
        }

        @Override
        public void serialize(ZonedDateTime t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            // ISODate("2017-09-03T08:24:29.382Z")
            String raw = "ISODate(\"" + TimeUtil.toIsoFormat(t) + "\")";
            jg.writeRawValue(raw);
        }
    }

    private static final ODTSerializer ODT = new ODTSerializer();

    static final class ODTSerializer extends JsonSerializer<OffsetDateTime> {

        @Override
        public Class<OffsetDateTime> handledType() {
            return OffsetDateTime.class;
        }

        @Override
        public void serialize(OffsetDateTime t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            // ISODate("2017-09-03T08:24:29.382Z")
            String raw = "ISODate(\"" + TimeUtil.toIsoFormat(t) + "\")";
            jg.writeRawValue(raw);
        }
    }

    private static final LDTSerializer LDT = new LDTSerializer();

    static final class LDTSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public Class<LocalDateTime> handledType() {
            return LocalDateTime.class;
        }

        @Override
        public void serialize(LocalDateTime t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            // ISODate("2017-09-03T08:24:29.382Z")
            String raw = "ISODate(\"" + TimeUtil.toIsoFormat(t) + "\")";
            jg.writeRawValue(raw);
        }
    }

}
