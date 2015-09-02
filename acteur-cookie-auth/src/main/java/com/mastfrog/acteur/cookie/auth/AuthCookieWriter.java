package com.mastfrog.acteur.cookie.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.cookie.auth.CookieAuthenticator.DEFAULT_COOKIE_NAME;
import static com.mastfrog.acteur.cookie.auth.CookieAuthenticator.SETTINGS_KEY_COOKIE_HOST;
import static com.mastfrog.acteur.cookie.auth.CookieAuthenticator.SETTINGS_KEY_COOKIE_HTTP_ONLY;
import static com.mastfrog.acteur.cookie.auth.CookieAuthenticator.SETTINGS_KEY_COOKIE_NAME;
import static com.mastfrog.acteur.cookie.auth.CookieAuthenticator.SETTINGS_KEY_USE_COOKIE_HOST;
import static com.mastfrog.acteur.cookie.auth.CookieAuthenticator.SETTINGS_KEY_USE_COOKIE_PORTS;
import com.mastfrog.settings.Settings;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.util.ArrayList;
import java.util.List;

/**
 * Actually writes the authentication cookie to the response.
 *
 * @author Tim Boudreau
 */
final class AuthCookieWriter {

    private final String cookieName;
    private final boolean useCookieHost;
    private final boolean cookieHttpOnly;
    private final String cookieHost;
    private final SessionTimeout timeout;
    private final Integer port;

    @Inject
    AuthCookieWriter(Settings settings, SessionTimeout timeout) {
        cookieName = settings.getString(SETTINGS_KEY_COOKIE_NAME, DEFAULT_COOKIE_NAME);
        useCookieHost = settings.getBoolean(SETTINGS_KEY_USE_COOKIE_HOST, true);
        cookieHttpOnly = settings.getBoolean(SETTINGS_KEY_COOKIE_HTTP_ONLY, true);
        cookieHost = settings.getString(SETTINGS_KEY_COOKIE_HOST);
        port = settings.getInt("port");
        this.timeout = timeout;
    }

    public String cookieName() {
        return cookieName;
    }

    protected Cookie findCookie(HttpEvent evt) {
        Cookie[] cookies = evt.getHeader(Headers.COOKIE);
        Cookie cookie = null;
        if (cookies != null && cookies.length > 0) {
            for (Cookie ck : cookies) {
                if (cookieName.equals(ck.name())) {
                    cookie = ck;
                    break;
                }
            }
        }
        return cookie;
    }

    private List<Integer> cookiePorts;

    protected List<Integer> cookiePorts() {
        if (cookiePorts == null) {
            List<Integer> result = new ArrayList<>(3);
            result.add(80);
            result.add(443);
            Integer port = this.port;
            if (port != null) {
                result.add(port);
            }
            return cookiePorts = result;
        }
        return cookiePorts;
    }

    private boolean logged;

    protected void configureCookie(HttpEvent evt, Cookie cookie) {
        String cookieHost = this.cookieHost;
        if (cookieHost == null) {
            cookieHost = evt.getHeader(Headers.HOST);
            if (cookieHost != null && cookieHost.lastIndexOf(":") > 0) {
                cookieHost = cookieHost.substring(0, cookieHost.lastIndexOf(":"));
            }
        }
        if (cookieHost == null) {
            cookieHost = "localhost";
            if (!logged) {
                logged = true;
                System.err.println("cookie.host not set and no host header found - using localhost");
            }
        }
        boolean use = cookieHost == null || (cookieHost != null && !cookieHost.startsWith("localhost"));
        if (use) {
            // these fail in chrome on localhost
            if (useCookieHost) {
                cookie.setDomain(cookieHost);
            }
            if (cookieHttpOnly) {
                cookie.setHttpOnly(true);
            }
        }
    }

    /**
     * Write the authentication cookie (using the configured name for it)
     * to the response.
     * 
     * @param evt The request
     * @param cookieValue The value - which should be something opaque and random
     * which can be used as a cache key to look up the user on the server side
     * @param on 
     */
    public void setCookie(HttpEvent evt, String cookieValue, Response on) {
        DefaultCookie ck = new DefaultCookie(cookieName, cookieValue);
        ck.setMaxAge(timeout.get().getStandardSeconds());
        configureCookie(evt, ck);
        on.add(Headers.SET_COOKIE, ck);
    }

    public void discardCookie(HttpEvent evt, Response on) {
        DefaultCookie cookie = new DefaultCookie(cookieName(), "");
        configureCookie(evt, cookie);
        cookie.setMaxAge(0);
        on.add(Headers.SET_COOKIE, cookie);
    }
}
