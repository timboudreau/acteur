package com.mastfrog.acteur.auth;

import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * A basic implementation of Tarpit which uses in-memory storage and a five
 * minute default expiration
 *
 * @author Tim Boudreau
 */
@Singleton
final class TarpitImpl implements Tarpit {

    private final Map<String, Entry> map = Maps.newConcurrentMap();
    private final Duration timeToExpiration;
    private final TarpitCacheKeyFactory keyFactory;
    private final GarbageCollect gc = new GarbageCollect();

    
    @Inject
    TarpitImpl(Settings settings, TarpitCacheKeyFactory keyFactory, ShutdownHookRegistry reg) {
        timeToExpiration = Duration.standardMinutes(settings.getLong(SETTINGS_KEY_TARPIT_EXPIRATION_TIME_MINUTES, 5));
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(gc, DateTime.now().plus(timeToExpiration).toDate(), timeToExpiration.getMillis() / 2);
        reg.add(new Runnable() {

            @Override
            public void run() {
                gc.cancel();
            }

        });
        this.keyFactory = keyFactory;
    }

    private class GarbageCollect extends TimerTask {

        @Override
        public void run() {
            garbageCollect();
        }
    }

    @Override
    public int count(HttpEvent evt) {
        Entry e = map.get(keyFactory.createKey(evt));
        int result = e == null ? 0 : e.size();
        return result;
    }

    int garbageCollect() {
        Map<String, Entry> copy = new HashMap<>(map);
        int result = 0;
        for (Map.Entry<String, Entry> e : copy.entrySet()) {
            if (e.getValue().isExpired()) {
                map.remove(e.getKey());
                result++;
            }
        }
        return result;
    }

    @Override
    public int add(HttpEvent evt) {
        String remoteAddress = keyFactory.createKey(evt);
        Entry entry = map.get(remoteAddress);
        if (entry == null) {
            // still need atomicity for adding
            synchronized (map) {
                entry = map.get(remoteAddress);
                if (entry == null) {
                    entry = new Entry();
                    map.put(remoteAddress, entry);
                } else {
                    entry.touch();
                }
            }
        } else {
            entry.touch();
        }
        return entry.touch();
    }

    @Override
    public void remove(HttpEvent evt) {
        map.remove(keyFactory.createKey(evt));
    }

    private class Entry {

        ConcurrentLinkedDeque<Long> accesses = new ConcurrentLinkedDeque<>();

        int touch() {
            accesses.offerLast(System.currentTimeMillis());
            return accesses.size();
        }

        int size() {
            return accesses.size();
        }

        boolean isExpired() {
            DateTime now = DateTime.now();
            for (Iterator<Long> iter = accesses.iterator(); iter.hasNext();) {
                DateTime when = new DateTime(iter.next());
                Duration dur = new Duration(when, now);
                if (dur.isLongerThan(timeToExpiration)) {
                    iter.remove();
                }
            }
            return accesses.isEmpty();
        }

        @Override
        public String toString() {
            return accesses.toString() + " expired " + isExpired();
        }
    }
}
