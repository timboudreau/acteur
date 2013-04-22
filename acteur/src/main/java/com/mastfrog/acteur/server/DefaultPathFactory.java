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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.url.*;
import com.mastfrog.util.Exceptions;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
//@SettingValues({DefaultPathFactory.HOSTNAME_SETTINGS_KEY + "=localhost", 
//DefaultPathFactory.BASE_PATH_SETTINGS_KEY + "="})
@Singleton
class DefaultPathFactory implements PathFactory {

    private @Inject(optional = true)
    @Named("port")
    int port = 80;
    private @Inject(optional = true)
    @Named("securePort")
    int securePort = 443;
    private @Inject(optional = true)
    @Named(HOSTNAME_SETTINGS_KEY)
    String hostname = "localhost";
    private @Inject(optional = true)
    @Named(BASE_PATH_SETTINGS_KEY)
    String path = "";
    private volatile Path pth;

    private Path basePath() {
        if (pth == null) {
            synchronized (this) {
                if (pth == null) {
                    pth = Path.parse(path);
                }
            }
        }
        return pth;
    }
    private static final Pattern STRIP_QUERY = Pattern.compile("(.*?)\\?.*");

    @Override
    public Path toExternalPath(String path) {
        return toExternalPath(Path.parse(path));
    }

    @Override
    public Path toExternalPath(Path path) {
        return Path.merge(basePath(), path);
    }

    private class LDR extends CacheLoader<String, Path> {

        @Override
        public Path load(String uri) throws Exception {
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
            return result;
        }
    }
    private final LoadingCache<String, Path> cache = CacheBuilder.newBuilder()
            .softValues()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .concurrencyLevel(5)
            .initialCapacity(20)
            .build(new LDR());

    @Override
    public Path toPath(String uri) {
        try {
            return cache.get(uri);
        } catch (Exception e) {
            return Exceptions.chuck(e);
        }
    }

    @Override
    public URL constructURL(Path path, boolean secure) {
        if (basePath().size() > 0) {
            path = Path.merge(basePath(), path);
        }
        Protocol protocol = secure ? Protocols.HTTPS : Protocols.HTTP;

        URLBuilder b = URL.builder(secure ? Protocols.HTTPS : Protocols.HTTP).setPath(path).setHost(hostname);
        if (port > 0 && port != protocol.getDefaultPort().intValue()) {
            b.setPort(secure ? securePort : port);
        }
        return b.create();
    }
}
