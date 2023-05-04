package com.mastfrog.acteur.server;

import com.mastfrog.acteur.Event;
import io.netty.handler.codec.http.HttpRequest;

/**
 *
 * @author Tim Boudreau
 */
public class EventImplFactory {
    @SuppressWarnings("rawtypes")
    public static Event newEvent(HttpRequest req, PathFactory paths) {
        return new EventImpl(req, paths);
    }

    private EventImplFactory() {
    }
}
