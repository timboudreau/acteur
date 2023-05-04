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
package com.mastfrog.acteur;

import com.google.inject.Singleton;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.CacheControlTypes;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Method.OPTIONS;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_CACHE_CONTROL_MAX_AGE;
import com.mastfrog.settings.Settings;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Description(category = "Info", value = "Answers CORS preflight HTTP OPTIONS requests - see the ajax spec")
@Methods(OPTIONS)
final class CORSResource extends Page {

    @Inject
    CORSResource() {
        add(CorsHeaders.class);
        add(CorsResponse.class);
    }

    static final class CorsHeaders extends Acteur {

        @Inject
        CorsHeaders(CORSResponseDecorator corsDecorator, HttpEvent evt, Page page) {
            corsDecorator.decorateCorsPreflight(evt, response(), page);
            next();
        }
    }

    private static final class CorsResponse extends Acteur {

        @Inject
        CorsResponse(CacheControlDuration dur) {
            response().addIfUnset(CACHE_CONTROL, dur.cacheControl);
            reply(HttpResponseStatus.NO_CONTENT);
        }
    }

    // Avoids recomputing the cache control setting for each CORS request
    @Singleton
    static final class CacheControlDuration {

        final CacheControl cacheControl;

        @Inject
        CacheControlDuration(Settings settings) {
            Duration corsCacheControlMaxAge = Duration.ofDays(settings.getLong(SETTINGS_KEY_CORS_CACHE_CONTROL_MAX_AGE, 365));
            cacheControl = CacheControl.$(CacheControlTypes.Public).add(CacheControlTypes.max_age, corsCacheControlMaxAge);
        }
    }
}
