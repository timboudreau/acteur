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
package com.mastfrog.acteur;

import com.google.inject.Singleton;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AsciiString;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/**
 * Decorates cors responses.
 *
 * @author Tim Boudreau
 */
@Singleton
final class CORSResponseDecorator {

    private static final Method[] methods = new Method[]{Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS};
    static final HeaderValueType<CharSequence> ALLOW_ORIGIN_STRING = Headers.ACCESS_CONTROL_ALLOW_ORIGIN.toStringHeader();
    final HeaderValueType<?>[] hdrs;
    final Duration corsMaxAge;
    final Duration corsCacheControlMaxAge;
    private final String allowOrigin;
    private final CacheControl cacheControl;

    @Inject
    CORSResponseDecorator(Settings settings) {
        Set<String> defaultHeaders = CollectionUtils.setOf(Headers.CONTENT_TYPE.name().toString(), 
                Headers.ACCEPT.name().toString(), Headers.X_REQUESTED_WITH.name().toString(),
                Headers.AUTHORIZATION.name().toString());
        String addtl = settings.getString(ServerModule.SETTINGS_KEY_CORS_ALLOW_HEADERS);
        if (addtl != null) {
            Set<CharSequence> seqs = Strings.splitUniqueNoEmpty(',', addtl);
            for (CharSequence s : seqs) {
                defaultHeaders.add(s.toString());
            }
        }
        Set<HeaderValueType> s = new HashSet<>();
        for (String s1 : defaultHeaders) {
            s.add(Headers.header(new AsciiString(s1)));
        }
        hdrs = s.toArray(new HeaderValueType<?>[s.size()]);
        corsMaxAge = Duration.of(settings.getLong(ServerModule.SETTINGS_KEY_CORS_MAX_AGE_MINUTES, ServerModule.DEFAULT_CORS_MAX_AGE_MINUTES), ChronoUnit.MINUTES);
        allowOrigin = settings.getString(ServerModule.SETTINGS_KEY_CORS_ALLOW_ORIGIN, ServerModule.DEFAULT_CORS_ALLOW_ORIGIN);
        corsCacheControlMaxAge = Duration.ofDays(settings.getLong(ServerModule.SETTINGS_KEY_CORS_CACHE_CONTROL_MAX_AGE, 365));
        cacheControl = CacheControl.$(CacheControlTypes.Public).add(CacheControlTypes.max_age, corsCacheControlMaxAge);
    }

    public void decorateCorsPreflight(Response resp) {
        resp.add(Headers.CONTENT_LENGTH, 0);
        resp.add(ALLOW_ORIGIN_STRING, allowOrigin);
        resp.add(Headers.ACCESS_CONTROL_ALLOW_CREDENTIALS, true);
        resp.add(Headers.ACCESS_CONTROL_ALLOW_HEADERS, hdrs);
        resp.add(Headers.ACCESS_CONTROL_ALLOW, methods);
        resp.add(Headers.ACCESS_CONTROL_MAX_AGE, corsMaxAge);
        resp.add(Headers.CACHE_CONTROL, cacheControl);
    }

    public void decorateApplicationResponse(HttpResponse response) {
        if (!response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)) {
            Headers.write(Headers.ACCESS_CONTROL_ALLOW_ORIGIN.toStringHeader(), allowOrigin, response);
        }
        if (!response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE)) {
            Headers.write(Headers.ACCESS_CONTROL_MAX_AGE, corsMaxAge, response);
        }
    }
}
