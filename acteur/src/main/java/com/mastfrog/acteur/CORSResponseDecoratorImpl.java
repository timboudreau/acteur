/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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
import static com.mastfrog.acteur.headers.Headers.ACCEPT;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_ALLOW;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_ALLOW_HEADERS;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_MAX_AGE;
import static com.mastfrog.acteur.headers.Headers.AUTHORIZATION;
import static com.mastfrog.acteur.headers.Headers.X_REQUESTED_WITH;
import static com.mastfrog.acteur.headers.Headers.write;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.CORS;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.DEFAULT_CORS_ALLOW_CREDENTIALS;
import static com.mastfrog.acteur.server.ServerModule.DEFAULT_CORS_ALLOW_ORIGIN;
import static com.mastfrog.acteur.server.ServerModule.DEFAULT_CORS_MAX_AGE_MINUTES;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_ALLOW_CREDENTIALS;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_ALLOW_HEADERS;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_MAX_AGE_MINUTES;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_REPLACE_ALLOW_HEADERS;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AsciiString;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class CORSResponseDecoratorImpl implements CORSResponseDecorator {

    private static final Method[] methods = new Method[]{Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS};
    static final HeaderValueType<CharSequence> ALLOW_ORIGIN_STRING = ACCESS_CONTROL_ALLOW_ORIGIN.toStringHeader();
    static final HeaderValueType<CharSequence> ALLOW_HEADERS_STRING = ACCESS_CONTROL_ALLOW_HEADERS.toStringHeader();
    static final HeaderValueType<CharSequence> ALLOW_CREDENTIALS_STRING = ACCESS_CONTROL_ALLOW_CREDENTIALS.toStringHeader();

    final CharSequence hdrs;
    final Duration corsMaxAge;
    private final String allowOrigin;
    private final boolean allowCredentials;

    @Inject
    CORSResponseDecoratorImpl(Settings settings) {
        Set<HeaderValueType<?>> defaultHeaders = new HashSet<>(setOf(Headers.CONTENT_TYPE,
                ACCEPT, X_REQUESTED_WITH,
                AUTHORIZATION));
        String addtl = settings.getString(SETTINGS_KEY_CORS_ALLOW_HEADERS);
        if (addtl != null) {
            Set<CharSequence> seqs = Strings.splitUniqueNoEmpty(',', addtl);
            for (CharSequence s : seqs) {
                defaultHeaders.add(Headers.header(s));
            }
        }
        String replace = settings.getString(SETTINGS_KEY_CORS_REPLACE_ALLOW_HEADERS);
        if (replace != null) {
            hdrs = replace;
        } else {
            List<CharSequence> headerNames = new ArrayList<>(CollectionUtils.transform(defaultHeaders, h -> h.name()));
            Collections.sort(headerNames, Strings.charSequenceComparator(true));
            hdrs = new AsciiString(Strings.join(',', headerNames));
        }
        allowCredentials = settings.getBoolean(SETTINGS_KEY_CORS_ALLOW_CREDENTIALS, DEFAULT_CORS_ALLOW_CREDENTIALS);
        corsMaxAge = Duration.of(settings.getLong(SETTINGS_KEY_CORS_MAX_AGE_MINUTES, DEFAULT_CORS_MAX_AGE_MINUTES), ChronoUnit.MINUTES);
        allowOrigin = settings.getString(ServerModule.SETTINGS_KEY_CORS_ALLOW_ORIGIN, DEFAULT_CORS_ALLOW_ORIGIN);
    }

    private static final AsciiString TRUE = new AsciiString("true");
    private static final AsciiString FALSE = new AsciiString("false");

    @Override
    public void decorateCorsPreflight(HttpEvent evt, Response resp, Page page) {
        Method[] methods = CORSResponseDecoratorImpl.methods;
        CORS cors = page.getClass().getAnnotation(CORS.class);
        CharSequence headers = this.hdrs;
        if (cors != null) {
            if (!cors.value()) {
                return;
            }
            if (cors.methods().length > 0) {
                methods = cors.methods();
            }
            if (cors.headers().length > 0) {
                headers = Strings.join(',', cors.headers());
            }
        }
        resp.addIfUnset(ALLOW_ORIGIN_STRING, allowOrigin);
        resp.addIfUnset(ALLOW_CREDENTIALS_STRING, allowCredentials ? TRUE : FALSE);
        resp.addIfUnset(ALLOW_HEADERS_STRING, headers);
        resp.addIfUnset(ACCESS_CONTROL_ALLOW, methods);
        resp.addIfUnset(ACCESS_CONTROL_MAX_AGE, corsMaxAge);
    }

    @Override
    public void decorateApplicationResponse(HttpResponse response) {
        if (!response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)) {
            write(Headers.ACCESS_CONTROL_ALLOW_ORIGIN.toStringHeader(), allowOrigin, response);
        }
        if (!response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE)) {
            write(Headers.ACCESS_CONTROL_MAX_AGE, corsMaxAge, response);
        }
    }
}
