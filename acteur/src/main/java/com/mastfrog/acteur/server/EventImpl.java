/*
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.acteur.headers.Method;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedMap;
import com.google.inject.util.Providers;
import com.mastfrog.url.Path;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.util.Codec;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
public final class EventImpl implements HttpEvent {

    private final HttpRequest req;
    private final PathFactory paths;
    private final SocketAddress address;
    private boolean neverKeepAlive = false;
    private final Channel channel;
    private final Codec codec;

    public EventImpl(HttpRequest req, PathFactory paths) {
        this.req = req;
        this.paths = paths;
        address = new InetSocketAddress("timboudreau.com", 8985); //XXX for tests
        this.channel = null;
        this.codec = new ServerModule.CodecImpl(Providers.of(new ObjectMapper()));
    }

    public EventImpl(HttpRequest req, SocketAddress addr, Channel channel, PathFactory paths, Codec codec) {
        this.req = req;
        this.paths = paths;
        address = addr;
        this.channel = channel;
        this.codec = codec;
    }

    @Override
    public String toString() {
        return req.getUri();
    }

    public void setNeverKeepAlive(boolean val) {
        neverKeepAlive = val;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public ByteBuf getContent() {
        return req instanceof FullHttpRequest ? ((FullHttpRequest) req).content()
                : Unpooled.EMPTY_BUFFER;
    }

    public OutputStream getContentAsStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(40);
        getChannel().read();
        ByteBuf inbound = getContent();
        int count;
        do {
            count = inbound.readableBytes();
            if (count > 0) {
                inbound.readBytes(out, count);
            }
        } while (count > 0);
        return out;
    }

    @Override
    public <T> T getContentAsJSON(Class<T> type) throws IOException {
        // Special handling for strings
        if (type == String.class || type == CharSequence.class) {
            String result = Streams.readString(new ByteBufInputStream(getContent()));
            if (result.length() > 0 && result.charAt(0) == '"') {
                result = result.substring(1);
            }
            if (result.length() > 1 && result.charAt(result.length() - 1) == '"') {
                result = result.substring(0, result.length() - 2);
            }
            return (T) result;
        }
        ByteBuf content = getContent();
        try {
            return codec.readValue(new ByteBufInputStream(content), type);
        } finally {
            content.resetReaderIndex();
        }
    }

    @Override
    public HttpRequest getRequest() {
        return req;
    }

    @Override
    public Method getMethod() {
        try {
            return Method.get(req);
        } catch (IllegalArgumentException e) {
            return Method.UNKNOWN;
        }
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return address;
    }

    @Override
    public String getHeader(String nm) {
        return req.headers().get(nm);
    }

    @Override
    public String getParameter(String param) {
        return getParametersAsMap().get(param);
    }

    @Override
    public Path getPath() {
        return paths.toPath(req.getUri());
    }

    @Override
    public <T> T getHeader(HeaderValueType<T> value) {
        String header = getHeader(value.name());
        if (header != null) {
            return value.toValue(header);
        }
        return null;
    }
    private Map<String, String> paramsMap;

    @Override
    public synchronized Map<String, String> getParametersAsMap() {
        if (paramsMap == null) {
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.getUri());
            Map<String, List<String>> params = queryStringDecoder.parameters();
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, List<String>> e : params.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                result.put(e.getKey(), e.getValue().get(0));
            }
            paramsMap = ImmutableSortedMap.copyOf(result);
        }
        return paramsMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getParametersAs(Class<T> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Not an interface: " + type);
        }
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{type}, new IH(type));
    }

    @Override
    public boolean isKeepAlive() {
        if (neverKeepAlive) {
            return false;
        }
        Connection c = getHeader(Headers.CONNECTION);
        return c == null ? false : c == Connection.keep_alive;
    }

    @Override
    public Optional<Integer> getIntParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            int ival = Integer.parseInt(val);
            return Optional.of(ival);
        }
        return Optional.absent();
    }

    @Override
    public Optional<Long> getLongParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            long lval = Long.parseLong(val);
            return Optional.of(lval);
        }
        return Optional.absent();
    }

    class IH implements InvocationHandler {

        private final Class<?> iface;

        IH(Class<?> iface) {
            this.iface = iface;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if ("equals".equals(method.getName())) {
                return false;
            }
            if ("hashCode".equals(method.getName())) {
                return 0;
            }
            if ("toString".equals(method.getName())) {
                return "Proxy " + iface.getSimpleName() + " over parameters "
                        + getParametersAsMap() + " from " + getRequest().getUri();
            }
            String nm = method.getName();
            String result = getParameter(nm);
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
            } else if (method.getReturnType() == DateTime.class) {
                long when = parseDate(result);
                if (when == Long.MIN_VALUE) {
                    return null;
                }
                return new DateTime(parseDate(result));
            } else if (method.getReturnType() == Duration.class) {
                long amt = -1;
                try {
                    amt = Long.parseLong(result);
                } catch (NumberFormatException nfe) {
                    return Duration.ZERO;
                }
                return new Duration(amt);
            }
            throw new IllegalArgumentException("Unsupported type " + method.getReturnType());
        }
    }

    private long parseDate(String result) {
        long when;
        try {
            when = Long.parseLong(result);
        } catch (NumberFormatException nfe) {
            when = Date.parse(result);
        }
        return when;
    }
}
