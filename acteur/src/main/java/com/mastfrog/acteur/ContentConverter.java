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

import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.mime.MimeType;
import com.mastfrog.util.codec.Codec;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.netbeans.validation.api.InvalidInputException;

/**
 * Converts byte buffers and maps to objects
 *
 * @author Tim Boudreau
 */
@Singleton
public class ContentConverter {

    protected final Codec codec;
    private final Provider<Charset> charset;
    private final Dependencies deps;

    @Inject
    public ContentConverter(Codec codec, Provider<Charset> charset, Dependencies deps) {
        this.codec = codec;
        this.charset = charset;
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

    private Charset findCharset(MimeType mt) {
        if (mt == null) {
            return charset.get();
        }
        if (mt.charset().isPresent()) {
            return mt.charset().get();
        }
        return charset.get();
    }

    @SuppressWarnings("unchecked")
    public <T> T toObject(ByteBuf content, MimeType mimeType, Class<T> type) throws Exception {
        if (mimeType == null) {
            mimeType = MimeType.ANY_TYPE;
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

    private final Map<Class<?>, ContentValidationPlugin> plugins = new HashMap<>();

    private <T> void register(ContentValidationPlugin plugin, Class<T> type) {
        plugins.put(type, plugin);
    }

    public static abstract class ContentValidationPlugin {

        private final ContentConverter converter;

        protected ContentValidationPlugin(ContentConverter converter, Class<?>... types) {
            this.converter = converter;
            for (Class<?> t : types) {
                register(t);
            }
        }

        protected ContentValidationPlugin(ContentConverter converter, Iterable<Class<?>> types) {
            this.converter = converter;
            for (Class<?> t : types) {
                register(t);
            }
        }

        protected ContentValidationPlugin(ContentConverter converter, String... types) throws ClassNotFoundException {
            this.converter = converter;
            for (String t : types) {
                register(Class.forName(t));
            }
        }

        protected ContentValidationPlugin(ContentConverter converter, Set<String> types) throws ClassNotFoundException {
            this.converter = converter;
            for (String t : types) {
                register(Class.forName(t));
            }
        }

        protected final <T> ContentValidationPlugin register(Class<T> type) {
            converter.register(this, type);
            return this;
        }

        /**
         * Validate the contents of a bytebuf without necessarily instantiating an object.
         * Throw an exception of some known type (may want to install an ExceptionEvaluator to turn
         * that into a meaningful response code).
         *
         * @param <T> The type
         * @param buf The buffer
         * @param mimeType
         * @param type
         * @param codec
         * @throws Exception
         */
        protected abstract <T> void validate(ByteBuf buf, MimeType mimeType, Class<T> type, Codec codec) throws Exception;

        /**
         * Validate a map which will be further deserialized into an object of type T.
         * Throw an exception of some known type (may want to install an ExceptionEvaluator to turn
         * that into a meaningful response code).
         *
         * @param <T> The type
         * @param type The type
         * @param map The map
         */
        protected abstract <T> void validate(Class<T> type, Map<String,?> map);
    }


    protected <T> T readObject(ByteBuf buf, MimeType mimeType, Class<T> type) throws Exception {
        if (type == String.class || type == CharSequence.class) {
            return type.cast(toString(buf, findCharset(mimeType)));
        }
        ContentValidationPlugin plugin = plugins.get(type);
        if (plugin != null) {
            plugin.validate(buf, mimeType, type, codec);
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

    public <T> T toObject(Map<String, ?> m, Class<T> type) throws InvalidInputException {
        if (type.isInterface()) {
            return createProxyFor(m, type);
        } else {
            return createObjectFor(m, type);
        }
    }

    protected <T> T createObjectFor(Map<String, ?> m, Class<T> type) {
        ContentValidationPlugin plugin = plugins.get(type);
        if (plugin != null) {
            plugin.validate(type, m);
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
