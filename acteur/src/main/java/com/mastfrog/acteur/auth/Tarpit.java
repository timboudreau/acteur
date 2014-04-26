package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;

/**
 * Used by AuthenticateBasicActeur.  Too many failed password attempts and
 * you go in the tar pit - first responses are delayed, then blocked altogether.
 * <p/>
 * Callers are identified by default by their IP address, but you can inject a
 * TarpitCacheKeyFactory to look at cookies or whatever you want.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(TarpitImpl.class)
public interface Tarpit {
    public static final String SETTINGS_KEY_TARPIT_EXPIRATION_TIME_MINUTES
            = "tarpit.default.expiration.minutes";
    public static final int DEFAULT_TARPIT_EXPIRATION_TIME_MINUTES = 5;

    /**
     * Add an entry to the tarpit for failing to authenticate, incrementing
     * the count if necessary.
     *
     * @param evt The http request
     * @return The updated number of bad requests
     */
    public int add(HttpEvent evt);
    /**
     * Get the number of bad requests
     * @param evt An http request
     * @return The
     */
    public int count(HttpEvent evt);

    public void remove(HttpEvent evt);
}
