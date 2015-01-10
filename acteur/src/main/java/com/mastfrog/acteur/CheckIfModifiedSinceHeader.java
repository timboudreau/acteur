package com.mastfrog.acteur;

import static com.mastfrog.acteur.headers.Headers.IF_MODIFIED_SINCE;
import io.netty.channel.ChannelFutureListener;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import java.util.Map;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Convenience Acteur which compares the current Page's Date against the current
 * request's If-Modified-Since header and returns a 304 response if the
 * browser's cached version is current.
 *
 * @author Tim Boudreau
 */
public class CheckIfModifiedSinceHeader extends Acteur {

    @Inject
    CheckIfModifiedSinceHeader(HttpEvent event, Page page) {
        DateTime lastModifiedMustBeNewerThan = event.getHeader(IF_MODIFIED_SINCE);
        DateTime pageLastModified = page.getResponseHeaders().getLastModified();
        boolean notModified = lastModifiedMustBeNewerThan != null && pageLastModified != null;
        if (notModified) {
            pageLastModified = pageLastModified.withMillisOfSecond(0);
            notModified = pageLastModified.getMillis() <= lastModifiedMustBeNewerThan.getMillis();
        }
        if (notModified) {
            reply(NOT_MODIFIED);
            // XXX workaround for peculiar problem with FileResource =
            // not modified responses are leaving a hanging connection
            setResponseBodyWriter(ChannelFutureListener.CLOSE);
            return;
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
