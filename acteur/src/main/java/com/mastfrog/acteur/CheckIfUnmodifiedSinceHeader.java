/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.acteur;

import static com.mastfrog.acteur.headers.Headers.IF_UNMODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpResponseStatus.PRECONDITION_FAILED;
import java.util.Map;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
public class CheckIfUnmodifiedSinceHeader extends Acteur {

    @Inject
    CheckIfUnmodifiedSinceHeader(HttpEvent event, Page page) {
        DateTime dt = event.getHeader(IF_UNMODIFIED_SINCE);
        if (dt != null) {
            DateTime pageLastModified = page.getResponseHeaders().getLastModified();
            if (pageLastModified != null) {
                boolean modSince = pageLastModified.getMillis() > dt.getMillis();
                if (modSince) {
                    reply(PRECONDITION_FAILED);
                    return;
                }
            }
        }
        next();
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Supports If-Unmodified-Since", true);
    }

}
