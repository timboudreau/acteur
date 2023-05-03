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

import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.IF_MODIFIED_SINCE;
import com.mastfrog.util.time.TimeUtil;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import java.time.ZonedDateTime;
import java.util.Map;
import javax.inject.Inject;

/**
 * Convenience Acteur which compares the current Page's Date against the current
 * request's If-Modified-Since header and returns a 304 response if the
 * browser's cached version is current.
 *
 * @author Tim Boudreau
 */
public final class CheckIfModifiedSinceHeader extends Acteur {

    @Inject
    CheckIfModifiedSinceHeader(HttpEvent event, Page page) {
        ZonedDateTime lastModifiedMustBeNewerThan = event.header(IF_MODIFIED_SINCE);
        if (lastModifiedMustBeNewerThan != null) {
            ZonedDateTime pageLastModified = response().get(Headers.LAST_MODIFIED);
            if (pageLastModified != null) {
                if (TimeUtil.isBeforeEqualOrNullSecondsResolution(lastModifiedMustBeNewerThan, pageLastModified)) {
                    reply(NOT_MODIFIED);
                    return;
                }
            }
        }
        next();
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Check if the Last-Modified header of the current page "
                + "is older than the date in the If-Modified-Since header "
                + "(if any) in the current request", true);
    }
}
