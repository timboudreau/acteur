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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mastfrog.jackson.JacksonConfigurer;
import java.io.IOException;
import org.bson.types.ObjectId;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = JacksonConfigurer.class)
public class ObjectIdToJSONConfigurer implements JacksonConfigurer {

    @Override
    public ObjectMapper configure(ObjectMapper om) {
        SimpleModule sm = new SimpleModule();
        sm.addDeserializer(ObjectId.class, new ObjectIdDeserializer());
        sm.addSerializer(ObjectId.class, new ObjectIdSerializer());
        return om.registerModule(sm);
    }

    static final class ObjectIdDeserializer extends JsonDeserializer<ObjectId> {

        @Override
        public Class<?> handledType() {
            return ObjectId.class;
        }

        @Override
        public boolean isCachable() {
            return true;
        }

        @Override
        public ObjectId deserialize(JsonParser jp, DeserializationContext dc) throws IOException,
                JsonProcessingException {
            String idString = jp.readValueAs(String.class);
            if (ObjectId.isValid(idString)) {
                return new ObjectId(idString);
            } else {
                dc.handleInstantiationProblem(ObjectId.class, idString, new IllegalArgumentException(
                        "Not a valid object id: '" + idString + "' at " + dc.getParser().getCurrentName() + " byte "
                        + dc.getParser().getCurrentLocation().getByteOffset() + " line "
                        + dc.getParser().getCurrentLocation().getLineNr()
                        + " pos " + dc.getParser().getCurrentLocation().getCharOffset()
                        + " text " + dc.getParser().getText()
                ));
                return null;
            }
        }
    }

    static final class ObjectIdSerializer extends JsonSerializer<ObjectId> {

        @Override
        public Class<ObjectId> handledType() {
            return ObjectId.class;
        }

        @Override
        public void serialize(ObjectId t, JsonGenerator jg, SerializerProvider sp) throws IOException,
                JsonProcessingException {
            jg.writeString(t.toHexString());
        }
    }
}
