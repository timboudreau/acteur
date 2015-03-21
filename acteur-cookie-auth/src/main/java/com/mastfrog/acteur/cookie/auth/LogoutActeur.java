package com.mastfrog.acteur.cookie.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.Precursors;
import java.net.URISyntaxException;

/**
 * Acteur which can be used in the {@link Precursors} annotation of an
 * endpoint which should log the user out.
 *
 * @author Tim Boudreau
 */
public class LogoutActeur extends Acteur {

    @Inject
    LogoutActeur(CookieAuthenticator auth, HttpEvent evt) throws URISyntaxException {
        auth.logout(evt, response());
        setState(new ConsumedLockedState());
    }
}
