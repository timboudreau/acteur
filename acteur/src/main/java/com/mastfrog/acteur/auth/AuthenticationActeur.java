package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.Acteur;

/**
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("deprecation")
@ImplementedBy(AuthenticateBasicActeur.class)
public class AuthenticationActeur extends Acteur {

}
