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
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import com.google.inject.util.Providers;
import com.mastfrog.acteur.ContentConverter;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.url.Path;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.Protocols;
import com.mastfrog.url.URL;
import com.mastfrog.util.Codec;
import com.mastfrog.util.collections.CollectionUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import static io.netty.util.CharsetUtil.UTF_8;
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
    private final PathFactory paths;
    private boolean neverKeepAlive = false;
    private final ChannelHandlerContext channel;
    private ContentConverter converter;
    private boolean ssl;
    private boolean early;

    public EventImpl(HttpRequest req, PathFactory paths) {
        this.req = req;
        this.path = paths.toPath(req.uri());
        this.paths = paths;
        address = new InetSocketAddress("timboudreau.com", 8985); //XXX for tests
        this.channel = null;
        Codec codec = new ServerModule.CodecImpl(Providers.of(new ObjectMapper()));
        this.converter = new ContentConverter(codec, Providers.of(UTF_8), null);
    }

    public EventImpl(HttpRequest req, SocketAddress addr, ChannelHandlerContext channel, PathFactory paths, ContentConverter converter, boolean ssl) {
        this.req = req;
        this.path = paths.toPath(req.uri());
        address = addr;
        this.channel = channel;
        this.converter = converter;
        this.ssl = ssl;
        this.paths = paths;
    }

    @Override
    public boolean isPreContent() {
        return early;
    }

    /**
     * Returns a best-effort at reconstructing the inbound URL, following the
     * following algorithm:
     * <ul>
     * <li>If the application has external url generation configured via
     * PathFactory, prefer the output of that</li>
     * <li>If not, try to honor non-standard but common headers such as
     * <code>X-Forwarded-Proto, X-URI-Scheme, Forwarded, X-Forwarded-Host</code></li>
     * </ul>
     *
     * Applications which respond to multiple virtual host names may need a
     * custom implementation of PathFactory bound to do this correctly.
     *
     * @return A URL string
     */
    public String getRequestURL(boolean preferHeaders) {
        HttpEvent evt = this;
        String uri = evt.request().uri();
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return uri;
        }
        URL takeFrom = paths.constructURL("/");
        CharSequence proto = DefaultPathFactory.findProtocol(evt);
        if (proto == null) {
            proto = takeFrom.getProtocol().toString();
        }
        String host = evt.header("X-Forwarded-Host");
        if (host == null) {
            host = evt.header(HOST);
        }
        int port = -1;
        if (host != null && host.indexOf(':') > 0) {
            HostAndPort hp = HostAndPort.fromString(host);
            port = hp.getPort();
            host = host.substring(0, host.indexOf(':'));
            if (port != -1 && Protocols.forName(proto.toString()).getDefaultPort().intValue() == port) {
                port = -1;
            } else if (port != -1 && Protocols.forName(proto.toString()).getDefaultPort().intValue() != port) {
                host = host + ":" + port;
            }
        }
        if (!preferHeaders) {
            String configuredHost = takeFrom.getHost().toString();
            if (!configuredHost.equals("localhost")) {
                host = configuredHost;
                port = takeFrom.getPort().intValue();
                proto = takeFrom.getProtocol().toString();
                Protocol protocol = com.mastfrog.url.Protocols.forName(proto.toString());
                if (port != -1 && port != protocol.getDefaultPort().intValue()) {
                    host = host + ":" + takeFrom.getPort().intValue();
                }
            }
        }
        if (uri.length() == 0 || uri.charAt(0) != '/') {
            host += '/';
        }
        return proto + "://" + host + uri;
    }


    EventImpl early() {
        early = true;
        return this;
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
        return channel.channel();
    }

    @Override
    public ChannelHandlerContext ctx() {
        return channel;
    }

    @Override
    public ByteBuf content() {
        return req instanceof ByteBufHolder ? ((ByteBufHolder) req).content()
                : Unpooled.EMPTY_BUFFER;
    }

    @Override
    public <T> T jsonContent(Class<T> type) throws Exception {
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
        ByteBuf content = content();
        if (content == null) {
            return "";
        }
        return converter.toString(content, encoding);
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
