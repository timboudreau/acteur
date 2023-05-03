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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_BASE_PATH;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_GENERATE_SECURE_URLS;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_GENERATE_URLS_WITH_INET_ADDRESS_GET_LOCALHOST;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_URLS_EXTERNAL_PORT;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_URLS_HOST_NAME;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.*;
import com.mastfrog.util.preconditions.Checks;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.preconditions.Exceptions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import static java.lang.System.currentTimeMillis;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
class DefaultPathFactory implements PathFactory {

    private static final AsciiString X_URL_SCHEME = new AsciiString("X-Url-Scheme");
    private static final AsciiString X_FORWARDED_SSL = new AsciiString("X-Forwarded-Ssl");
    private static final AsciiString FRONT_END_HTTPS = new AsciiString("Front-End-Https");
    private static final AsciiString FORWARDED = new AsciiString("Forwarded");
    private static final Pattern FORWARDED_SUB_PATTERN = Pattern.compile("proto=(\\S+)[;$]");
    private static final Pattern STRIP_QUERY = Pattern.compile("(.*?)\\?(.*)");
    private static final int URI_TO_PATH_CACHE_PRUNE_INTERVAL = 64;
    private static final int URI_TO_PATH_CACHE_EXPIRY_MILLIS = 60_000 * 5;

    // Cache common URI->Path mappings
    private final Map<String, PathCacheEntry> cache = new ConcurrentHashMap<>(256, 0.9F);
    // Used to prune cache entries every URI_TO_PATH_CACHE_PRUNE_INTERVAL requests
    private int hits = Integer.MIN_VALUE;
    private final int port;
    private final int securePort;
    private final String hostname;
    private final Path pth;
    private final boolean secure;

    @Inject
    DefaultPathFactory(Settings settings) {
        int port = settings.getInt(SETTINGS_KEY_URLS_EXTERNAL_PORT, -1);
        if (port == -1) {
            port = settings.getInt(ServerModule.PORT, Protocols.HTTP.getDefaultPort().intValue());
        }
        this.port = port;
        int secPort = settings.getInt(SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT,
                settings.getInt("securePort", Protocols.HTTPS.getDefaultPort().intValue())); // legacy stuff

        if (secPort < 0) {
            throw new ConfigurationError(SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT + " cannot be negative: " + secPort);
        }
        if (port < 0) {
            throw new ConfigurationError(SETTINGS_KEY_URLS_EXTERNAL_PORT + " cannot be negative: " + secPort);
        }
        this.securePort = secPort;
        secure = settings.getBoolean(SETTINGS_KEY_GENERATE_SECURE_URLS, settings.getBoolean(ServerModule.SETTINGS_KEY_SSL_ENABLED,
                settings.getBoolean("use.secure.urls", false))); // more legacy stuff

        String hn = settings.getString(SETTINGS_KEY_URLS_HOST_NAME);
        if (hn == null) {
            if (settings.getBoolean(SETTINGS_KEY_GENERATE_URLS_WITH_INET_ADDRESS_GET_LOCALHOST, false)) {
                try {
                    hn = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException ex) {
                    Exceptions.printStackTrace(ex);
                    hn = "localhost";
                }
            } else {
                hn = "localhost";
            }
        }
        hostname = hn;
        String path = settings.getString(SETTINGS_KEY_BASE_PATH, "");
        pth = Path.parse(path);
        if (!pth.isValid()) {
            throw new ConfigurationError(SETTINGS_KEY_BASE_PATH + " is not a valid URL path: '" + path + "'");
        }
    }

    @Override
    public int portForProtocol(Protocol protocol) {
        if (protocol.isSecure()) {
            return securePort;
        } else {
            return port;
        }
    }

    private Path basePath() {
        return pth;
    }

    @Override
    public Path toExternalPath(String path) {
        return toExternalPath(Path.parse(path));
    }

    @Override
    public Path toExternalPath(Path path) {
        return Path.merge(basePath(), path);
    }

    @Override
    public URL constructURL(Path path) {
        return constructURL(path, secure);
    }

    private PathCacheEntry load(String uri) {
        Path result;
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        Matcher m = STRIP_QUERY.matcher(uri);
        if (m.matches()) {
            uri = m.group(1);
        }
        if (uri.startsWith(basePath().toString())) {
            uri = uri.substring(basePath().toString().length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        result = Path.parse(uri, true);
        return new PathCacheEntry(result);
    }

    private void pruneCache() {
        // Ensure that we don't pile up cache entries by pruning those
        // unused for > five minutes
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    @Override
    public Path toPath(String uri) {
        if (hits++ % URI_TO_PATH_CACHE_PRUNE_INTERVAL == 0) {
            pruneCache();
        }
        PathCacheEntry pce = cache.computeIfAbsent(uri, this::load);
        return pce.path();
    }

    @Override
    public URL constructURL(Protocol protocol, Path path) {
        return constructURL(protocol, path, secure);
    }

    public URL constructURL(Protocol protocol, Path path, boolean secure) {
        return constructURL(protocol, path, secure ? securePort : port);
    }

    public URL constructURL(Protocol protocol, Path path, int port) {
        Checks.nonNegative("port", port);
        if (basePath().size() > 0) {
            path = Path.merge(basePath(), path);
        }
        URLBuilder b = URL.builder(protocol).setPath(path).setHost(hostname);
        if (port > 0) {
            b.setPort(port);
        }
        return b.create();
    }

    @Override
    public URL constructURL(Path path, boolean secure) {
        if (basePath().size() > 0) {
            path = Path.merge(basePath(), notNull("path", path));
        }
        Protocol protocol = secure ? Protocols.HTTPS : Protocols.HTTP;

        URLBuilder b = URL.builder(secure ? Protocols.HTTPS : Protocols.HTTP)
                .setPath(path)
                .setHost(hostname);
        if (port > 0 && port != protocol.getDefaultPort().intValue()) {
            b.setPort(secure ? securePort : port);
        }
        return b.create();
    }

    static CharSequence findProtocol(HttpEvent evt) {
        CharSequence proto = evt == null ? null : evt.header(Headers.X_FORWARDED_PROTO);
        if (proto == null && evt != null) {
            proto = evt.header(X_URL_SCHEME);
        }
        if (proto == null && evt != null) {
            if ("on".equals(evt.header(X_FORWARDED_SSL))) {
                proto = "https";
            } else if ("on".equals(evt.header(FRONT_END_HTTPS))) {
                proto = "https";
            }
        }
        if (proto == null && evt != null) {
            String s = evt.header(FORWARDED);
            if (s != null) {
                Matcher m = FORWARDED_SUB_PATTERN.matcher(s);
                if (m.find()) {
                    proto = m.group(1);
                }
            }
        }
        return proto;
    }

    @Override
    public URL constructURL(String path, HttpEvent evt) {
        int qix = path.indexOf('?');
        String query = null;
        String anchor = null;
        if (qix > 0 && qix < path.length() - 1) {
            query = path.substring(qix + 1);
            path = path.substring(0, qix);
            int aix = query.indexOf('#');
            if (aix >= 0 && aix < query.length() - 2) {
                anchor = query.substring(aix + 1);
                query = query.substring(0, aix);
            }
        }
        Path pth = Path.parse(path);
        if (!pth.isValid()) {
            throw new IllegalArgumentException("Invalid path '" + path + "'");
        }
        String host = evt == null ? null : evt.header(HttpHeaderNames.HOST);
        CharSequence proto = findProtocol(evt);
        URL base = constructURL(path);
        if (host == null) {
            if (query != null || anchor != null) {
                URLBuilder bldr = new URLBuilder(base);
                applyQueryString(query, bldr);
                if (anchor != null) {
                    bldr.setAnchor(anchor);
                }
                return bldr.create();
            }
            if (proto != null) {
                URLBuilder bldr = new URLBuilder(base);
                Protocol protocol = Protocols.forName(proto.toString());
                bldr.setProtocol(protocol);
                bldr.setPort(portForProtocol(protocol));
                return bldr.create();
            }
            return base;
        }
        String[] hostPort = host.split(":");
        URLBuilder bldr = new URLBuilder(constructURL(pth));
        bldr.setHost(hostPort[0]);
        if (hostPort.length > 1 && hostPort[1].length() > 0) {
            try {
                int portFromHostHeader = Integer.parseInt(hostPort[1]);
                bldr.setPort(portFromHostHeader);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Port in host header is not a number: '" + hostPort[1] + "'");
            }
        }
        // Preserve the query string
        applyQueryString(query, bldr);
        if (anchor != null) {
            bldr.setAnchor(anchor);
        }
        if (proto != null) {
            Protocol protocol = Protocols.forName(proto.toString());
            bldr.setProtocol(protocol);
            bldr.setPort(portForProtocol(protocol));
        }
        return bldr.create();
    }

    static boolean applyQueryString(String query, URLBuilder bldr) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        boolean result = false;
        for (String item : query.split("&")) {
            int ix = item.indexOf('=');
            if (ix >= 0) {
                String[] kv = item.split("=", 2);
                if (kv.length > 1) {
                    bldr.addQueryPair(kv[0], kv[1]);
                } else {
                    bldr.addQueryPair(kv[0], "");
                }
                result = true;
            }
        }
        return result;
    }

    private static final class PathCacheEntry {

        private long touched = currentTimeMillis();
        private final Path path;

        public PathCacheEntry(Path path) {
            this.path = path;
        }

        boolean isExpired() {
            return currentTimeMillis() - touched > URI_TO_PATH_CACHE_EXPIRY_MILLIS;
        }

        Path path() {
            touched = currentTimeMillis();
            return path;
        }
    }
}
