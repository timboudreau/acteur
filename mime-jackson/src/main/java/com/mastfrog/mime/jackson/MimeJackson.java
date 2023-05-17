/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.mime.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import com.mastfrog.mime.MimeType;
import com.mastfrog.util.service.ServiceProvider;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(JacksonConfigurer.class)
public final class MimeJackson implements JacksonConfigurer {

    @Override
    public ObjectMapper configure(ObjectMapper m) {
        SimpleModule sm = new SimpleModule("java-optional", new Version(1, 0, 0, null, "com.mastfrog", "com-google-common-base-optional"));
        sm.addSerializer(MimeType.class, new MimeSerializer());
        sm.addDeserializer(MimeType.class, new MimeDeserializer());
        m.registerModule(sm);
        return m;
    }

    public String name() {
        return "MimeType";
    }

    private static class MimeSerializer extends JsonSerializer<MimeType> {

        @Override
        public Class<MimeType> handledType() {
            return MimeType.class;
        }

        @Override
        public void serialize(MimeType t, JsonGenerator jg, SerializerProvider sp) throws IOException {
            jg.writeString(t.toString());
        }
    }

    private static class MimeDeserializer extends JsonDeserializer<MimeType> {

        @Override
        public Class<?> handledType() {
            return MimeType.class;
        }

        @Override
        public MimeType deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            String type = jp.readValueAs(String.class);
            return MimeType.parse(type);
        }
    }

}
