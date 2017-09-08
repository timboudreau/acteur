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
import java.time.ZonedDateTime;
import javax.inject.Named;
import javax.inject.Provider;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
class JacksonCodecs implements DynamicCodecs {
    private final Provider<ObjectMapper> mapper;
    private final ByteBufCodec bufCodec;
    
    @Inject
    JacksonCodecs(@Named(JACKSON_BINDING_NAME) Provider<ObjectMapper> mapper, ByteBufCodec bufCodec) {
        this.mapper = mapper;
        this.bufCodec = bufCodec;
    }

    @Override
    public <T> Codec<T> createCodec(Class<T> type, CodecConfigurationException ex) {
        if (ObjectId.class == type) { // Don't let jackson take over objectids and store them as strings
            return null;
        }
        if (ZonedDateTime.class == type) {
            return null;
        }
        return new JacksonCodec<>(mapper, Providers.of(bufCodec), type);
    }
}
