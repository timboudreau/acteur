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
import com.mastfrog.url.Path;
import com.mastfrog.util.Codec;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
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
    private boolean ssl;

    public EventImpl(HttpRequest req, PathFactory paths) {
        this.req = req;
        this.path = paths.toPath(req.uri());
        address = new InetSocketAddress("timboudreau.com", 8985); //XXX for tests
        this.channel = null;
        Codec codec = new ServerModule.CodecImpl(Providers.of(new ObjectMapper()));
        this.converter = new ContentConverter(codec, Providers.of(Charset.defaultCharset()), null, null);
    }

    public EventImpl(HttpRequest req, SocketAddress addr, Channel channel, PathFactory paths, ContentConverter converter, boolean ssl) {
        this.req = req;
        this.path = paths.toPath(req.uri());
        address = addr;
        this.channel = channel;
        this.converter = converter;
        this.ssl = ssl;
    }
    
    private static final AsciiString HTTPS = AsciiString.of("https");
    public boolean isSsl() {
        boolean result = ssl;
        if (!result) {
            CharSequence cs = header(Headers.X_FORWARDED_PROTO);
            if (cs != null) {
                result = HTTPS.contentEqualsIgnoreCase(HTTPS);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return req.method() + "\t" + req.uri();
    }

    public void setNeverKeepAlive(boolean val) {
        neverKeepAlive = val;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ByteBuf content() {
        return req instanceof ByteBufHolder ? ((ByteBufHolder) req).content()
                : Unpooled.EMPTY_BUFFER;
    }

    @Override
    public <T> T jsonContent(Class<T> type) throws IOException {
        MediaType mimeType = header(Headers.CONTENT_TYPE);
        if (mimeType == null) {
            mimeType = MediaType.ANY_TYPE;
        }
        return converter.toObject(content(), mimeType, type);
    }

    @Override
    public String stringContent() throws IOException {
        MediaType type = header(Headers.CONTENT_TYPE);
        if (type == null) {
            type = MediaType.PLAIN_TEXT_UTF_8;
        }
        Charset encoding;
        if (type.charset().isPresent()) {
            encoding = type.charset().get();
        } else {
            encoding = CharsetUtil.UTF_8;
        }
        return converter.toString(content(), encoding);
    }

    @Override
    public HttpRequest request() {
        return req;
    }

    @Override
    public Method method() {
        try {
            return Method.get(req);
        } catch (IllegalArgumentException e) {
            return Method.UNKNOWN;
        }
    }

    @Override
    public SocketAddress remoteAddress() {
        return address;
    }

    @Override
    public String header(CharSequence nm) {
        return req.headers().get(nm);
    }

    @Override
    public String urlParameter(String param) {
        return urlParametersAsMap().get(param);
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public <T> List<T> headers(HeaderValueType<T> headerType) {
        List<CharSequence> headers = CollectionUtils.<CharSequence>generalize(request().headers().getAll(headerType.name()));
        return CollectionUtils.convertedList(headers, headerType, CharSequence.class, headerType.type());
    }

    @Override
    public Map<CharSequence, CharSequence> headersAsMap() {
        Map<CharSequence, CharSequence> headers = CollectionUtils.caseInsensitiveStringMap();
        for (Map.Entry<String, String> e : request().headers().entries()) {
            headers.put(e.getKey(), e.getValue());
        }
        return headers;
    }

    @Override
    public <T> T header(HeaderValueType<T> value) {
        String header = header(value.name());
        if (header != null) {
            return value.toValue(header);
        }
        return null;
    }
    private Map<String, String> paramsMap;

    @Override
    public synchronized Map<String, String> urlParametersAsMap() {
        if (paramsMap == null) {
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
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
    public <T> T urlParametersAs(Class<T> type) {
        return converter.toObject(urlParametersAsMap(), type);
    }

    @Override
    public boolean requestsConnectionStayOpen() {
        if (neverKeepAlive) {
            return false;
        }
        boolean hasKeepAlive = req.headers()
                .contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);
//        String hdr = req.headers().get(HttpHeaderNames.CONNECTION);
//        if (hdr == null) {
//            return false;
//        }
//        return "keep-alive".equalsIgnoreCase(hdr);
        return hasKeepAlive;
    }

    @Override
    public Optional<Integer> intUrlParameter(String name) {
        String val = urlParameter(name);
        if (val != null) {
            int ival = Integer.parseInt(val);
            return Optional.of(ival);
        }
        return Optional.absent();
    }

    @Override
    public Optional<Long> longUrlParameter(String name) {
        String val = urlParameter(name);
        if (val != null) {
            long lval = Long.parseLong(val);
            return Optional.of(lval);
        }
        return Optional.absent();
    }
}
