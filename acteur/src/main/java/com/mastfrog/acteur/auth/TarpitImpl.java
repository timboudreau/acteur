package com.mastfrog.acteur.auth;

import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.settings.Settings;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
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

    private Map<String, Entry> map = Maps.newConcurrentMap();
    private Duration timeToExpiration;

    TarpitImpl(Settings settings) {
        timeToExpiration = Duration.standardMinutes(settings.getLong("tarpit.default.duration.minutes", 5));
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                garbageCollect();
            }
        }, DateTime.now().plus(timeToExpiration).toDate(), timeToExpiration.getMillis() / 2);
    }

    @Override
    public int count(HttpEvent evt) {
        Entry e = map.get(evt.getRemoteAddress().toString());
        return e == null ? 0 : e.size();
    }

    int garbageCollect() {
        Map<String, Entry> copy = new HashMap<String, Entry>(map);
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
        String remoteAddress = evt.getRemoteAddress().toString();
        Entry entry = map.get(remoteAddress);
        if (entry == null) {
            // still need atomicity for adding
            synchronized (map) {
                entry = map.get(remoteAddress);
                if (entry == null) {
                    entry = new Entry();
                    map.put(remoteAddress, entry);
                }
            }
        }
        return entry.touch();
    }

    private class Entry {

        ConcurrentLinkedDeque<Long> accesses = new ConcurrentLinkedDeque<Long>();

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
    }
}
