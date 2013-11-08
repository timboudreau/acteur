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

import com.mastfrog.acteur.util.Realm;
import com.mastfrog.acteur.util.BasicCredentials;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.settings.Settings;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.joda.time.Duration;

/**
 * Basic authentication Acteur, with a few tricks:
 * <ul>
 * <li>Will "tarpit" IP addresses which make repeated failed requests</li>
 * <li>Will delay sending the response if more than a threshold number of bad attempts have already been made</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public class AuthenticateBasicActeur extends Acteur {
    public static final String SETTINGS_KEY_TARPIT_BAD_LOGIN_ATTEMPT_COUNT = "max.allowed.failed.login.attempts";
    public static final String SETTINGS_KEY_TARPIT_DELAY_RESPONSE_AFTER = "delay.failed.login.attempts.after";
    public static final String SETTINGS_KEY_TARPIT_DELAY_SECONDS = "failed.login.attempt.response.delay";
    
    public static final int DEFAULT_FAILED_LOGIN_ATTEMPT_LIMIT = 7;
    public static final int DEFAULT_FAILED_LOGIN_ATTEMPT_DELAY_THRESHOLD = 3;
    public static final int DEFAULT_FAILED_LOGIN_DELAY_SECONDS_MULTIPLIER = 1;

    @Inject
    AuthenticateBasicActeur(HttpEvent event, Authenticator authenticator, Realm r, Tarpit tarpit, Settings settings) throws IOException {
        int badRequestCount = tarpit.count(event);
        if (badRequestCount >0 && badRequestCount > settings.getInt(SETTINGS_KEY_TARPIT_BAD_LOGIN_ATTEMPT_COUNT, DEFAULT_FAILED_LOGIN_ATTEMPT_LIMIT)) {
            setState(new RespondWith(SERVICE_UNAVAILABLE, "Too many bad password attempts"));
            return;
        }
        BasicCredentials credentials = event.getHeader(Headers.AUTHORIZATION);
        if (credentials == null) {
            unauthorized(r);
        } else {
            Object[] stuff = authenticator.authenticate(r.toString(), credentials);
            if (stuff == null) {
                int badCount = tarpit.add(event);
                if (badCount > settings.getInt(SETTINGS_KEY_TARPIT_DELAY_RESPONSE_AFTER, DEFAULT_FAILED_LOGIN_ATTEMPT_DELAY_THRESHOLD)) {
                    response().setDelay(Duration.standardSeconds(badCount * settings.getInt(SETTINGS_KEY_TARPIT_DELAY_SECONDS, 1)));
                }
                unauthorized(r);
            } else {
                List<Object> l = new ArrayList<>(Arrays.asList(stuff));
                setState(new ConsumedLockedState(l.toArray(new Object[0])));
            }
        }
    }

    private void unauthorized(Realm realm) {
        add(Headers.WWW_AUTHENTICATE, realm);
        setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED));
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Basic Authentication Required", true);
    }
}
