package com.mastfrog.acteur.auth;

import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.mastfrog.util.time.TimeUtil;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.inject.Inject;

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
        timeToExpiration = Duration.ofMinutes(settings.getLong(SETTINGS_KEY_TARPIT_EXPIRATION_TIME_MINUTES, 5));
        Timer timer = new Timer(true);
        Date firstTime = new Date(TimeUtil.toUnixTimestamp(ZonedDateTime.now().plus(timeToExpiration)));
        timer.scheduleAtFixedRate(gc, firstTime, timeToExpiration.toMillis() / 2);
        reg.add(gc::cancel);
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
        return e == null ? 0 : e.size();
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

        final ConcurrentLinkedDeque<Long> accesses = new ConcurrentLinkedDeque<>();

        int touch() {
            accesses.offerLast(System.currentTimeMillis());
            return accesses.size();
        }

        int size() {
            return accesses.size();
        }

        boolean isExpired() {
            ZonedDateTime now = ZonedDateTime.now();
            for (Iterator<Long> iter = accesses.iterator(); iter.hasNext();) {
                ZonedDateTime when = TimeUtil.fromUnixTimestamp(iter.next());
                Duration dur = Duration.between(when, now);
                if (TimeUtil.isLonger(dur, timeToExpiration)) {
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
