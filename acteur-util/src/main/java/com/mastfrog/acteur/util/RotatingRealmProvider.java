package com.mastfrog.acteur.util;

import com.mastfrog.settings.Settings;
import com.mastfrog.util.Strings;
import com.mastfrog.util.strings.RandomStrings;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Provides an authentication realm which changes every hour or whatever
 * interval is specified
 *
 * @author Tim Boudreau
 */
@Singleton
public final class RotatingRealmProvider implements Provider<Realm> {

    public static final String SETTINGS_KEY_ROTATE_INTERVAL_MINUTES = "realm.rotate.interval.minutes";
    public static final int DEFAULT_ROTATE_INTERVAL = 60;
    private static final RandomStrings rs = new RandomStrings();
    private final Duration interval;
    private final String salt;

    @Inject
    RotatingRealmProvider(Settings s) {
        interval = Duration.of(s.getInt(SETTINGS_KEY_ROTATE_INTERVAL_MINUTES, DEFAULT_ROTATE_INTERVAL), ChronoUnit.MINUTES);
        salt = rs.get(4);
    }

    @Override
    public Realm get() {
        Duration since1970 = Duration.ofMillis(System.currentTimeMillis());
        long minutesSince1970 = since1970.toMinutes();
        long intervals = minutesSince1970 / interval.toMinutes();
        return new Realm(Strings.interleave(Long.toString(intervals, 36), salt));
    }
}
