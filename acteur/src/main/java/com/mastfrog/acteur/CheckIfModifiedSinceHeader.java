package com.mastfrog.acteur;

import com.mastfrog.acteur.headers.Headers;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Convenience Acteur which compares the current Page's Date against the
 * current request's If-Modified-Since header and returns a 304 response if
 * the browser's cached version is current.
 *
 * @author Tim Boudreau
 */
public class CheckIfModifiedSinceHeader extends Acteur {

    @Inject
    CheckIfModifiedSinceHeader(HttpEvent event, Page page) {
        DateTime lastModifiedMustBeNewerThan = event.getHeader(Headers.IF_MODIFIED_SINCE);
        DateTime pageLastModified = page.getResponseHeaders().getLastModified();
        boolean notModified = lastModifiedMustBeNewerThan != null && pageLastModified != null;
        if (notModified) {
            pageLastModified = pageLastModified.withMillisOfSecond(0);
            notModified = pageLastModified.getMillis() <= lastModifiedMustBeNewerThan.getMillis();
        }
        if (notModified) {
            setResponseCode(HttpResponseStatus.NOT_MODIFIED);
            setState(new RespondWith(HttpResponseStatus.NOT_MODIFIED));
            // XXX workaround for peculiar problem with FileResource =
            // not modified responses are leaving a hanging connection
            setResponseBodyWriter(ChannelFutureListener.CLOSE);
            return;
        }
        next();
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Supports If-Modified-Since header", true);
    }

}
