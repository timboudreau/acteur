/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur;

import com.google.common.net.MediaType;
import com.google.inject.Provider;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.parameters.KeysValues;
import com.mastfrog.parameters.gen.Origin;
import com.mastfrog.parameters.validation.ParamChecker;
import com.mastfrog.util.Codec;
import com.mastfrog.util.time.TimeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import javax.inject.Inject;
import org.netbeans.validation.api.InvalidInputException;
import org.netbeans.validation.api.Problems;

/**
 * Converts byte buffers and maps to objects
 *
 * @author Tim Boudreau
 */
public class ContentConverter {

    protected final Codec codec;
    private final Provider<Charset> charset;
    private final ParamChecker checker;
    private final Dependencies deps;

    @Inject
    public ContentConverter(Codec codec, Provider<Charset> charset, ParamChecker checker, Dependencies deps) {
        this.codec = codec;
        this.charset = charset;
        this.checker = checker;
        this.deps = deps;
    }

    public String toString(ByteBuf content, Charset encoding) throws IOException {
        String result;
        content.resetReaderIndex();
        CharSequence cs = content.readCharSequence(content.readableBytes(), encoding);
        if (cs == null) {
            return null;
        }
        result = cs.toString();
        content.resetReaderIndex();
        if (result.length() > 0 && result.charAt(0) == '"') {
            result = result.substring(1);
        }
        if (result.length() > 1 && result.charAt(result.length() - 1) == '"') {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }

    private Charset findCharset(MediaType mt) {
        if (mt == null) {
            return charset.get();
        }
        if (mt.charset().isPresent()) {
            return mt.charset().get();
        }
        return charset.get();
    }

    @SuppressWarnings("unchecked")
    public <T> T toObject(ByteBuf content, MediaType mimeType, Class<T> type) throws IOException {
        if (mimeType == null) {
            mimeType = MediaType.ANY_TYPE;
        }
        // Special handling for strings
        if (type == String.class || type == CharSequence.class) {
            return type.cast(toString(content, findCharset(mimeType)));
        }

        if (type.isInterface()) {
            Map<String, Object> m;
            try (InputStream in = new ByteBufInputStream(content)) {
                m = codec.readValue(in, Map.class);
            }
            return toObject(m, type);
        }
        return readObject(content, mimeType, type);
    }

    protected <T> T readObject(ByteBuf buf, MediaType mimeType, Class<T> type) throws IOException, InvalidInputException {
        if (type == String.class || type == CharSequence.class) {
            return type.cast(toString(buf, findCharset(mimeType)));
        }
        Origin origin = type.getAnnotation(Origin.class);
        if (origin != null) {
            Map map;
            try (InputStream in = new ByteBufInputStream(buf)) {
                map = codec.readValue(in, Map.class);
                validate(origin, map).throwIfFatalPresent();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                throw ioe;
            } finally {
                buf.resetReaderIndex();
            }
        }
        buf.resetReaderIndex();
        try (InputStream in = new ByteBufInputStream(buf)) {
            T result = codec.readValue(in, type);
            return result;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw ioe;
        } finally {
            buf.resetReaderIndex();
        }
    }

    @SuppressWarnings("unchecked")
    private Problems validate(Origin origin, Map map) {
        Problems problems = new Problems();
        checker.check(origin.value(), new KeysValues.MapAdapter(map), problems);
        return problems;
    }

    public <T> T toObject(Map<String, ?> m, Class<T> type) throws InvalidInputException {
        if (type.isInterface()) {
            return createProxyFor(m, type);
        } else {
            return createObjectFor(m, type);
        }
    }

    protected <T> T createObjectFor(Map<String, ?> m, Class<T> type) {
        Origin origin = type.getAnnotation(Origin.class);
        if (origin != null) {
            validate(origin, m).throwIfFatalPresent();
        }
        return deps.getInstance(type);
    }

    @SuppressWarnings("unchecked")
    protected <T> T createProxyFor(Map<String, ?> m, Class<T> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Not an interface: " + type);
        }
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{type}, new IH(type, m));
    }

    static class IH implements InvocationHandler {

        private final Class<?> iface;
        private final Map<String, ?> map;

        IH(Class<?> iface, Map<String, ?> map) {
            this.iface = iface;
            this.map = map;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if ("equals".equals(method.getName())) {
                return false;
            }
            if ("hashCode".equals(method.getName())) {
                return map.hashCode();
            }
            if ("toString".equals(method.getName())) {
                return "Proxy " + iface.getSimpleName() + " over parameters " + map;
            }
            String nm = method.getName();
            String result = map.get(nm) instanceof String ? (String) map.get(nm) : map.containsKey(nm) ? map.get(nm) + "" : null;
            Class<?> ret = method.getReturnType();
            if (result == null) {
                return null;
            } else if (ret == Long.TYPE || ret == Long.class) {
                if (ret == Long.class && result == null) {
                    return null;
                }
                return Long.parseLong(result);
            } else if (ret == String.class || ret == CharSequence.class) {
                return result;
            } else if (ret == Integer.TYPE || ret == Integer.class) {
                if (ret == Integer.class && result == null) {
                    return null;
                }
                return Integer.parseInt(result);
            } else if (ret == Double.TYPE || ret == Double.class || ret == Number.class) {
                if (ret == Double.class && result == null) {
                    return null;
                }
                return Double.parseDouble(result);
            } else if (ret == Float.TYPE || ret == Float.class) {
                if (ret == Float.class && result == null) {
                    return null;
                }
                return Float.parseFloat(result);
            } else if (ret == char[].class) {
                return result.toCharArray();
            } else if (Byte.TYPE == ret || Byte.class == ret) {
                if (ret == Byte.class && result == null) {
                    return null;
                }
                return Byte.parseByte(result);
            } else if (Short.class == ret || Short.TYPE == ret) {
                if (ret == Short.class && result == null) {
                    return null;
                }
                return Short.parseShort(result);
            } else if (ret == Boolean.TYPE || ret == Boolean.class) {
                switch (result) {
                    case "0":
                        return false;
                    case "1":
                        return true;
                    default:
                        return result == null ? false : Boolean.parseBoolean(result);
                }
            } else if (method.getReturnType() == String.class) {
                return result.split(",");
            } else if (method.getReturnType() == Date.class) {
                long when = parseDate(result);
                if (when != Long.MIN_VALUE) {
                    return parseDate(result);
                }
                return null;
            } else if (method.getReturnType() == ZonedDateTime.class) {
                long when = parseDate(result);
                if (when == Long.MIN_VALUE) {
                    return null;
                }
                return TimeUtil.fromUnixTimestamp(parseDate(result));
            } else if (method.getReturnType() == Duration.class) {
                long amt;
                try {
                    amt = Long.parseLong(result);
                } catch (NumberFormatException nfe) {
                    return Duration.ZERO;
                }
                return TimeUtil.millis(amt);
            }
            throw new IllegalArgumentException("Unsupported type " + method.getReturnType());
        }
    }

    @SuppressWarnings("deprecation")
    private static long parseDate(String result) {
        long when;
        try {
            when = Long.parseLong(result);
        } catch (NumberFormatException nfe) {
            when = Date.parse(result);
        }
        return when;
    }
}
