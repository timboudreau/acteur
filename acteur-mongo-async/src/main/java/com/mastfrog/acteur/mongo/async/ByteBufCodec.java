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

import com.mastfrog.util.time.TimeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.ZonedDateTime;
import javax.inject.Inject;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.json.JsonReader;
import org.bson.json.JsonWriterSettings;

/**
 *
 * @author Tim Boudreau
 */
public class ByteBufCodec implements Codec<ByteBuf> {

    private final ByteBufAllocator alloc;

    @Inject
    ByteBufCodec(ByteBufAllocator alloc) {
        this.alloc = alloc;
    }

    public void writeDateTime(BsonWriter writer, ZonedDateTime t, EncoderContext ec) {
        writer.writeDateTime(TimeUtil.toUnixTimestamp(t));
    }

    @Override
    public void encode(BsonWriter writer, ByteBuf t, EncoderContext ec) {
        CharSequence seq = t.readCharSequence(t.readableBytes(), UTF_8);
        JsonReader reader = new JsonReader(seq.toString());
        writer.pipe(reader);
    }

    @Override
    public Class<ByteBuf> getEncoderClass() {
        return ByteBuf.class;
    }

    @Override
    public ByteBuf decode(BsonReader reader, DecoderContext dc) {
        ByteBuf buf = alloc.buffer();
        JsonWriterSettings settings = JsonWriterSettings.builder()
                .indent(false).build();
        PlainJsonWriter json = new PlainJsonWriter(buf, settings);
        json.pipe(reader);
        return buf;
    }
}
