package com.mastfrog.acteur.cookie.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Implementation of AuthenticationActeur which uses cookies instead of
 * basic auth credentials.
 *
 * @author Tim Boudreau
 */
final class CookieActeur extends AuthenticationActeur {

    @Inject
    CookieActeur(HttpEvent evt, CookieAuthenticator auth) throws Exception {
        Object[] o = auth.authenticateRequest(evt);
        if (o != null && o.length > 0) {
            setState(new ConsumedLockedState(o[0]));
        } else {
            setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED, "Not authorized"));
        }
    }
}
