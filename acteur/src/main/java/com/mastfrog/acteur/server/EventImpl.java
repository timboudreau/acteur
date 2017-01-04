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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.net.MediaType;
import com.google.inject.util.Providers;
import com.mastfrog.acteur.ContentConverter;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.url.Path;
import com.mastfrog.util.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
final class EventImpl implements HttpEvent {

    private final HttpRequest req;
    private final Path path;
    private final SocketAddress address;
    private boolean neverKeepAlive = false;
    private final Channel channel;
    private ContentConverter converter;

    public EventImpl(HttpRequest req, PathFactory paths) {
        this.req = req;
        this.path = paths.toPath(req.getUri());
        address = new InetSocketAddress("timboudreau.com", 8985); //XXX for tests
        this.channel = null;
        Codec codec = new ServerModule.CodecImpl(Providers.of(new ObjectMapper()));
        this.converter = new ContentConverter(codec, Providers.of(Charset.defaultCharset()), null, null);
    }

    public EventImpl(HttpRequest req, SocketAddress addr, Channel channel, PathFactory paths, ContentConverter converter) {
        this.req = req;
        this.path = paths.toPath(req.getUri());
        address = addr;
        this.channel = channel;
        this.converter = converter;
    }

    @Override
    public String toString() {
        return req.getMethod() + "\t" + req.getUri();
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
        return req instanceof ByteBufHolder ? ((ByteBufHolder) req).content()
                : Unpooled.EMPTY_BUFFER;
    }

    @Override
    public <T> T getContentAsJSON(Class<T> type) throws IOException {
        MediaType mimeType = getHeader(Headers.CONTENT_TYPE);
        if (mimeType == null) {
            mimeType = MediaType.ANY_TYPE;
        }
        return converter.toObject(getContent(), mimeType, type);
    }

    @Override
    public String getContentAsString() throws IOException {
        MediaType type = getHeader(Headers.CONTENT_TYPE);
        if (type == null) {
            type = MediaType.PLAIN_TEXT_UTF_8;
        }
        Charset encoding;
        if (type.charset().isPresent()) {
            encoding = type.charset().get();
        } else {
            encoding = CharsetUtil.UTF_8;
        }
        return converter.toString(getContent(), encoding);
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
        return path;
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
        return converter.toObject(getParametersAsMap(), type);
    }

    @Override
    public boolean isKeepAlive() {
        if (neverKeepAlive) {
            return false;
        }
        String hdr = req.headers().get("Connection");
        if (hdr == null) {
            return false;
        }
        return "keep-alive".equalsIgnoreCase(hdr);
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
}
