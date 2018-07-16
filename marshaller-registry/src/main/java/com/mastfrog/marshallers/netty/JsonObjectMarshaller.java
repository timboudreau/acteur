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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastfrog.util.preconditions.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import java.io.DataInput;
import java.io.IOException;
import com.mastfrog.marshallers.Marshaller;
import static com.mastfrog.marshallers.netty.NettyContentMarshallers.findHint;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class JsonObjectMarshaller implements Marshaller<Object, ByteBuf> {

    private final ObjectMapper mapper;
    private final ObjectMapper wrappingMapper;
    private static final Set<Class<?>> wrapTypes = Collections.<Class<?>>unmodifiableSet(new HashSet<>(Arrays.asList(new Class<?>[]{
        Boolean.class, Boolean.TYPE, Double.class, Double.TYPE, Float.class, Float.TYPE, String.class, CharSequence.class,
        Byte.class, Byte.TYPE, Long.class, Long.TYPE, Integer.class, Integer.TYPE, Short.class, Short.TYPE,
        Number.class
    })));

    JsonObjectMarshaller(ObjectMapper mapper) {
        Checks.notNull("mapper", mapper);
        this.mapper = mapper;
        this.wrappingMapper = mapper.copy().configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
                .configure(SerializationFeature.WRAP_ROOT_VALUE, true);
    }

    @Override
    public Object read(ByteBuf data, Object[] hints) throws IOException {
        Class<?> type = findHint(Class.class, hints, Object.class);
        ObjectMapper m;
        boolean wrapped = false;
        if (typeNeedsWrapping(type)) {
            m = wrappingMapper;
            wrapped = true;
        } else {
            m = mapper;
        }
        try (final ByteBufInputStream in = new ByteBufInputStream(data)) {
            if (wrapped) {
                Map<String,Object> map = mapper.readValue((DataInput) in, Map.class);
                Object o = map.get(type.getSimpleName());
                if (o == null) {
                    return null;
                }
                return type.cast(o);
            }
            return m.readValue((DataInput) in, type);
        }
    }

    @Override
    public void write(Object obj, ByteBuf into, Object[] hints) throws IOException {
        ObjectMapper m;
        if (needsWrapping(obj)) {
            m = wrappingMapper;
        } else {
            m = mapper;
        }
        try (final ByteBufOutputStream out = new ByteBufOutputStream(into)) {
            m.writeValue((OutputStream) out, obj);
            into.resetReaderIndex();
        }
    }

    private boolean needsWrapping(Object o) {
        if (o == null) {
            return true;
        }
        return typeNeedsWrapping(o.getClass());
    }

    private boolean typeNeedsWrapping(Class<?> type) {
        boolean result = false;
        if (type.isArray() && wrapTypes.contains(type.getComponentType())) {
            result = true;
        } else {
            for (Class<?> t : wrapTypes) {
                if (type == t || type.isAssignableFrom(t)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }
}
