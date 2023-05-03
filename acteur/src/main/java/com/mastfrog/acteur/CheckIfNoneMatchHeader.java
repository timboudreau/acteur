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
import static com.mastfrog.acteur.headers.Headers.IF_NONE_MATCH;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.strings.Strings;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import java.util.Map;
import javax.inject.Inject;

/**
 * Convenience Acteur which compares the current Page's ETag against the current
 * request's If-None-Match header and returns a 304 response if the browser's
 * cached version is current.
 *
 * @author Tim Boudreau
 */
@Description("Replies with a 304 Not-Modified if the "
        + "If-None-Match header in the current request matches the "
        + "ETag header already set on the current response")
public final class CheckIfNoneMatchHeader extends Acteur {

    @Inject
    CheckIfNoneMatchHeader(HttpEvent event, Page page) {
        Checks.notNull("event", event);
        Checks.notNull("page", page);
        CharSequence etag = event.header(IF_NONE_MATCH);
        CharSequence pageEtag = response().get(Headers.ETAG);
        if (etag != null && pageEtag != null && Strings.charSequencesEqual(etag, pageEtag)) {
            reply(NOT_MODIFIED);
            return;
        }
        next();
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Replies with a 304 Not-Modified if the "
                + "If-None-Match header in the current request matches the "
                + "ETag header already set on the current response", true);
    }
}
