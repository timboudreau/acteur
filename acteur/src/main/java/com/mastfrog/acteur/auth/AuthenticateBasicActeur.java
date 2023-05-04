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
package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.header.entities.BasicCredentials;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.giulius.annotations.Setting;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.perf.Benchmark;
import com.mastfrog.util.perf.Benchmark.Kind;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import javax.inject.Inject;

/**
 * Basic authentication Acteur, with a few tricks:
 * <ul>
 * <li>Will "tarpit" IP addresses which make repeated failed requests</li>
 * <li>Will delay sending the response if more than a threshold number of bad
 * attempts have already been made</li>
 * </ul>
 *
 * @author Tim Boudreau
 * @deprecated Do not use directly - bind AuthenticationActeur instead, so that
 * authentication strategies are pluggable
 */
@Deprecated
public class AuthenticateBasicActeur extends AuthenticationActeur {

    @Setting(value = "BasicAuth: In response n failed login attempts, ban a host", type = Setting.ValueType.INTEGER, defaultValue = "7")
    public static final String SETTINGS_KEY_TARPIT_BAD_LOGIN_ATTEMPT_COUNT = "max.allowed.failed.login.attempts";
    @Setting(value = "BasicAuth: In response n failed auth attempts , use an escalating delay before "
            + "returning subsequent responses to the offending address", type = Setting.ValueType.INTEGER, defaultValue = "7")
    public static final String SETTINGS_KEY_TARPIT_DELAY_RESPONSE_AFTER = "delay.failed.login.attempts.after";
    @Setting(value = "BasicAuth: The number of seconds to delay responses", type = Setting.ValueType.INTEGER, defaultValue = "7")
    public static final String SETTINGS_KEY_TARPIT_DELAY_SECONDS = "failed.login.attempt.response.delay";

    public static final int DEFAULT_FAILED_LOGIN_ATTEMPT_LIMIT = 7;
    public static final int DEFAULT_FAILED_LOGIN_ATTEMPT_DELAY_THRESHOLD = 3;
    public static final int DEFAULT_FAILED_LOGIN_DELAY_SECONDS_MULTIPLIER = 1;

    @Inject
    AuthenticateBasicActeur(HttpEvent event, Authenticator authenticator, Realm r, Tarpit tarpit, Settings settings, AuthenticationDecorator decorator, Page page) throws IOException {
        int badRequestCount = tarpit.count(event);
        if (badRequestCount > 0 && badRequestCount > settings.getInt(SETTINGS_KEY_TARPIT_BAD_LOGIN_ATTEMPT_COUNT, DEFAULT_FAILED_LOGIN_ATTEMPT_LIMIT)) {
            setState(new RespondWith(SERVICE_UNAVAILABLE, "Too many bad password attempts"));
            return;
        }
        BasicCredentials credentials = event.header(Headers.AUTHORIZATION);
        if (credentials == null) {
            unauthorized(r, event, decorator, page, response());
        } else {
            Object[] stuff = authenticator.authenticate(r.toString(), credentials);
            if (stuff == null) {
                int badCount = tarpit.add(event);
                if (badCount > settings.getInt(SETTINGS_KEY_TARPIT_DELAY_RESPONSE_AFTER, DEFAULT_FAILED_LOGIN_ATTEMPT_DELAY_THRESHOLD)) {
                    delayResponse(badCount, settings);
                }
                unauthorized(r, event, decorator, page, response());
            } else {
                decorator.onAuthenticationSucceeded(event, page, response(), stuff);
                next(stuff);
            }
        }
    }

    @Benchmark(value = "tarpittedClients", publish = Kind.CALL_COUNT)
    void delayResponse(int badCount, Settings settings) {
        Duration delayResponse = Duration.ofSeconds(badCount * settings.getInt(SETTINGS_KEY_TARPIT_DELAY_SECONDS, 1));
        response().delayedBy(delayResponse);
    }

    @Benchmark(value = "failedAuthentication", publish = Kind.CALL_COUNT)
    private void unauthorized(Realm realm, HttpEvent evt, AuthenticationDecorator decorator, Page page, Response response) {
        decorator.onAuthenticationFailed(evt, page, response);
        add(Headers.WWW_AUTHENTICATE, realm);
        setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED));
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Basic Authentication Required", true);
    }

    private static class NoOpDecorator implements AuthenticationDecorator {

        @Override
        public void onAuthenticationSucceeded(HttpEvent evt, Page page, Response response, Object[] stuff) {
            //do nothing
        }

        @Override
        public void onAuthenticationFailed(HttpEvent evt, Page page, Response response) {
            //do nothing
        }
    }

    /**
     * Decorator which can do things to the response on authentication
     * succeess/failure, such as setting/clearing cookies
     */
    @ImplementedBy(NoOpDecorator.class)
    public interface AuthenticationDecorator {

        /**
         * Called when authentication succeeds. This may be called on every
         * request with basic auth. In particular, if you are going to set a
         * cookie, ensure it is not already there and valid.
         *
         * @param evt The event/request
         * @param page The page in question
         * @param response The response
         * @param stuff Objects returned by Authenticator.authenticate()
         */
        void onAuthenticationSucceeded(HttpEvent evt, Page page, Response response, Object[] stuff);

        /**
         * Called when authetication failse
         *
         * @param evt The event
         * @param page The page
         * @param response The response
         */
        void onAuthenticationFailed(HttpEvent evt, Page page, Response response);
    }
}
