package com.mastfrog.acteur.cookie.auth;

import com.google.inject.Inject;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.DeploymentMode;
import java.time.Duration;
import javax.inject.Singleton;

/**
 * Represents the duration of a login session before a user will be required to
 * re-log-in. Used for cookie expiration times, cache expiration times, etc. Set
 * the <code>"session.expiry.hours"</code> settings key to a number of hours
 * that sessions should last without requiring login again. By default it is set
 * to 2 minutes in development mode and 2 days in production mode.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class SessionTimeout {

    /**
     * Settings key which determines session duration in hours. If unspecified,
     * session duration is 2 minutes for testing purposes, so this should
     * <i>always</i> be set in production.
     */
    public static final String SETTINGS_KEY_SESSION_EXPIRY_HOURS = "session.expiry.hours";
    private final Duration expiry;

    @Inject
    SessionTimeout(Settings settings, DeploymentMode mode) {
        Integer expiry = settings.getInt(SETTINGS_KEY_SESSION_EXPIRY_HOURS);
        if (expiry != null) {
            this.expiry = Duration.ofHours(expiry);
        } else if (mode.isProduction()) {
//                throw new ConfigurationError("Will not use the default session "
//                        + "expiration time of 2 minutes in production.  Set"
//                        + SETTINGS_KEY_SESSION_EXPIRY_HOURS + " to something resonable.");
            this.expiry = Duration.ofDays(2);
        } else {
            this.expiry = Duration.ofMinutes(2);
        }
    }

    public Duration get() {
        return expiry;
    }
}
