package com.mastfrog.acteur.cookie.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;

/**
 * Acteur which can be used in the {@link Precursors} annotation of HTTP
 * endpoints that log in a user, passing a <code>LoginInfo</code> as the HTTP
 * request body.
 *
 * @author Tim Boudreau
 */
public final class LoginActeur extends Acteur {

    @Inject
    LoginActeur(HttpEvent evt, CookieAuthenticator auth) throws Exception {
        LoginInfo info = evt.jsonContent(LoginInfo.class);
        Object[] inject = auth.login(evt, info, response());
        if (inject == null || inject.length == 0) {
            auth.logout(evt, response());
            setState(new RespondWith(UNAUTHORIZED, "Unknown user name or password"));
        } else {
            setState(new ConsumedLockedState(inject[0]));
        }
    }
}
