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
package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.preconditions.Authenticated;
import static com.mastfrog.acteur.preconditions.Authenticated.OPTIONAL;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.util.strings.Strings;
import io.netty.handler.codec.http.HttpHeaderNames;
import static io.netty.handler.codec.http.HttpHeaderNames.WWW_AUTHENTICATE;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import io.netty.util.AsciiString;
import javax.inject.Inject;

/**
 * Implements AuthenticationActeur for AsyncAuthenticationModule.
 *
 * @author Tim Boudreau
 */
final class AsyncAuthenticationActeur extends AuthenticationActeur {

    private static final HeaderValueType<CharSequence> VT = Headers.header(WWW_AUTHENTICATE);
    private static final AsciiString BEARER = new AsciiString("Bearer");

    @Inject
    @SuppressWarnings("unchecked")
    AsyncAuthenticationActeur(AsyncAuthenticator<?> auth, Chain chain, HttpEvent evt, RequestID rid, Page page) {
        String authHeader = evt.header(HttpHeaderNames.AUTHORIZATION);
        if (authHeader == null) {
            Authenticated anno = page.getClass().getAnnotation(Authenticated.class);
            if (anno == null || !OPTIONAL.equals(anno.value())) {
                reply(UNAUTHORIZED, "No auth header present");
                return;
            } else if (OPTIONAL.equals(anno.value())) {
                next();
                return;
            }
        }
        String token;
        if (Strings.startsWithIgnoreCase(authHeader, "bearer ")) {
            token = Strings.urlDecode(authHeader.substring("bearer ".length()));
        } else {
            reply(UNAUTHORIZED, "Invalid auth header prefix in '" + authHeader + "'");
            return;
        }
        String invalidMessage = auth.validate(evt, token);
        if (invalidMessage != null) {
            reply(UNAUTHORIZED, invalidMessage);
            add(VT, BEARER);
            return;
        }
        chain.insert(CheckAuth.class);
        continueAfter(true, auth.authenticate(rid, evt, token));
    }

    static final class CheckAuth extends Acteur {

        @Inject
        CheckAuth(AuthenticationResult<?> authRes, Page page) {
            Authenticated anno = page.getClass().getAnnotation(Authenticated.class);
            if (anno != null && OPTIONAL.equals(anno.value())) {
                next();
                return;
            }
            if (authRes.failureStatus != null) {
                add(VT, BEARER);
                if (authRes.failureMessage != null) {
                    reply(authRes.failureStatus, authRes.failureMessage);
                } else {
                    reply(authRes.failureStatus);
                }
                return;
            }
            next(authRes, authRes.info);
        }
    }
}
