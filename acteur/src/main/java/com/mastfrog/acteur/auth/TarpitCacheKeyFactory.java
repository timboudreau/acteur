package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.HttpEvent;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 *
 * @author tim
 */
public class TarpitCacheKeyFactory {

    /**
     * Construct a cache key
     * @param evt An http request
     * @return The key or null if none can be computed
     */
    public String createKey(HttpEvent evt) {
        SocketAddress addr = evt.remoteAddress();
        if (!(addr instanceof InetSocketAddress)) {
            return null;
        }
        String remoteAddress = ((InetSocketAddress) addr).getHostString();
        return remoteAddress;
    }
}
