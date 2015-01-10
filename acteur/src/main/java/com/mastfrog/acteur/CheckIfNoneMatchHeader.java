/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.acteur;

import static com.mastfrog.acteur.headers.Headers.IF_NONE_MATCH;
import com.mastfrog.util.Checks;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import java.util.Map;
import javax.inject.Inject;

/**
 * Convenience Acteur which compares the current Page's ETag against the
 * current request's If-None-Match header and returns a 304 response if
 * the browser's cached version is current.
 *
 * @author Tim Boudreau
 */
public class CheckIfNoneMatchHeader extends Acteur {

    @Inject
    CheckIfNoneMatchHeader(HttpEvent event, Page page) throws Exception {
        Checks.notNull("event", event);
        Checks.notNull("page", page);
        String etag = event.getHeader(IF_NONE_MATCH);
        String pageEtag = page.getResponseHeaders().getETag();
        if (etag != null && pageEtag != null) {
            if (etag.equals(pageEtag)) {
                reply(NOT_MODIFIED);
                // XXX workaround for peculiar problem with FileResource =
                // not modified responses are leaving a hanging connection
//                setResponseBodyWriter(ChannelFutureListener.CLOSE);
                return;
            }
        }
        next();
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Replies with a 304 not modified if the "
                + "If-None-Match header in the current request matches the "
                + "ETag already set on the current page", true);
    }

}
