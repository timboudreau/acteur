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
package com.mastfrog.marshallers.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.mastfrog.marshallers.ContentMarshallers;
import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import java.awt.image.RenderedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import com.mastfrog.marshallers.Marshaller;

/**
 * Registry of interpreters that can read and write objects into Netty ByteBuf
 * instances.
 *
 * @author Tim Boudreau
 */
public final class NettyContentMarshallers extends ContentMarshallers<ByteBuf, NettyContentMarshallers> {

    public NettyContentMarshallers() {
        add(ByteBuf.class, new ByteBufMarshaller())
                .add(ByteBuffer.class, new ByteBufferMarshaller())
                .add(byte[].class, new ByteArrayInterpreter());
    }

    /**
     * Adds a built-in marshaller for byte arrays.
     *
     * @return this
     */
    public NettyContentMarshallers withByteArrays() {
        return add(byte[].class, new ByteArrayInterpreter());
    }

    /**
     * Adds a built-in marshaller for Strings. The charset may be included in
     * the hints, either as a Charset or MediaType instance.
     *
     * @return this
     */
    public NettyContentMarshallers withStrings() {
        return add(String.class, new StringMarshaller());
    }

    /**
     * Adds a built-in marshaller for BufferedImage / RenderedImage. A MIME
     * (guava's MediaType) type or format name (recognized by ImageIO) will be
     * used if passed as a hint; in the event of an unsupported format, jpeg is
     * used.
     *
     * @return this
     */
    public NettyContentMarshallers withImages() {
        return add(RenderedImage.class, new ImageStreamInterpreter());
    }

    /**
     * Adds a built-in marshaller for InputStreams.
     *
     * @return this
     */
    public NettyContentMarshallers withInputStreams() {
        return add(InputStream.class, new InputStreamInterpreter());
    }

    /**
     * Adds a built-in marshaller for CharSequences. The charset may be included
     * in the hints, either as a Charset or MediaType instance.
     *
     * @return this
     */
    public NettyContentMarshallers withCharSequences() {
        return add(CharSequence.class, new CharSequenceInterperter());
    }

    /**
     * Adds a built-in marshaller for Maps to JSON using Jackson.
     *
     * @param mapper A Jackson ObjectMapper for JSON conversion
     * @return this
     */
    @SuppressWarnings("unchecked")
    public NettyContentMarshallers withJsonMaps(ObjectMapper mapper) {
        return add(Map.class, (Marshaller) new JsonMapMarshaller(mapper));
    }

    /**
     * Adds a built-in marshaller for Lists to JSON using Jackson.
     *
     * @param mapper A Jackson ObjectMapper for JSON conversion
     * @return this
     */
    @SuppressWarnings("unchecked")
    public NettyContentMarshallers withJsonLists(ObjectMapper mapper) {
        return add(List.class, (Marshaller) new JsonListMarshaller(mapper));
    }

    /**
     * Adds a built-in marshaller for Object to JSON using Jackson - read
     * objects will use whatever default type the ObjectMapper is configured to
     * use (for example, a double[] might be returned as a double[] or a
     * List&lt;Double&gt;.
     *
     * @param mapper A Jackson ObjectMapper for JSON conversion
     * @return this
     */
    @SuppressWarnings("unchecked")
    public NettyContentMarshallers withJsonObjects(ObjectMapper mapper) {
        return add(Object.class, (Marshaller) new JsonObjectMarshaller(mapper));
    }

    /**
     * Get a default content marshaller which includes all of the built in
     * marshallers.
     *
     * @param mapper A Jackson ObjectMapper for JSON conversion
     * @return
     */
    public static NettyContentMarshallers getDefault(ObjectMapper mapper) {
        return new NettyContentMarshallers().withByteArrays().withImages().withInputStreams().withJsonMaps(mapper).withStrings()
                .withJsonLists(mapper).withCharSequences().withJsonObjects(mapper);
    }

    static <T> T findHint(Class<T> type, Object[] hints, T defaultValue) {
        for (Object h : hints) {
            if (type.isInstance(h)) {
                return type.cast(h);
            }
        }
        return defaultValue;
    }

    static Charset findCharset(Object[] hints) {
        MediaType type = findHint(MediaType.class, hints, null);
        Charset charset = type == null ? null : type.charset().isPresent() ? type.charset().get() : null;
        if (charset == null) {
            charset = findHint(Charset.class, hints, CharsetUtil.UTF_8);
        }
        return charset;
    }
}
