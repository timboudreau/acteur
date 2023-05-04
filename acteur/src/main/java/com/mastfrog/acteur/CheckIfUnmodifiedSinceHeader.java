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
import static com.mastfrog.acteur.headers.Headers.IF_UNMODIFIED_SINCE;
import com.mastfrog.acteur.preconditions.Description;
import static io.netty.handler.codec.http.HttpResponseStatus.PRECONDITION_FAILED;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Map;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Description("Replies with a 304 Not-Modified status if the "
        + "If-Modified-Since header in the current request matches the "
        + "Last-Modified header already set on the current response")
public final class CheckIfUnmodifiedSinceHeader extends Acteur {

    @Inject
    CheckIfUnmodifiedSinceHeader(HttpEvent event, Page page) {
        ZonedDateTime headerValue = event.header(IF_UNMODIFIED_SINCE);
        if (headerValue != null) {
            ZonedDateTime pageLastModified = response().get(Headers.LAST_MODIFIED);
            if (pageLastModified != null) {
                pageLastModified = pageLastModified.with(ChronoField.MILLI_OF_SECOND, 0);

                if (headerValue.equals(pageLastModified) || headerValue.isBefore(pageLastModified)) {
                    reply(PRECONDITION_FAILED);
                }
            }
        }
        next();
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Replies with a 304 Not-Modified status if the "
                + "If-Modified-Since header in the current request matches the "
                + "Last-Modified header already set on the current response", true);
    }

}
