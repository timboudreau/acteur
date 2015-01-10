/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.acteur;

import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.util.Checks;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
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
        String etag = event.getHeader(Headers.IF_NONE_MATCH);
        String pageEtag = page.getResponseHeaders().getETag();
        if (etag != null && pageEtag != null) {
            if (etag.equals(pageEtag)) {
                setState(new RespondWith(HttpResponseStatus.NOT_MODIFIED));
                // XXX workaround for peculiar problem with FileResource =
                // not modified responses are leaving a hanging connection
                setResponseBodyWriter(ChannelFutureListener.CLOSE);
                return;
            }
        }
        next();
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Supports If-None-Match header", true);
    }

}
