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
import com.mastfrog.util.Strings;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.util.AsciiString;
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
    private final boolean useCookiePorts;
    private final boolean cookieHttpOnly;
    private final String cookieHost;
    private final SessionTimeout timeout;
    private final Integer port;

    @Inject
    AuthCookieWriter(Settings settings, SessionTimeout timeout) {
        cookieName = settings.getString(SETTINGS_KEY_COOKIE_NAME, DEFAULT_COOKIE_NAME);
        useCookieHost = settings.getBoolean(SETTINGS_KEY_USE_COOKIE_HOST, true);
        useCookiePorts = settings.getBoolean(SETTINGS_KEY_USE_COOKIE_PORTS, true);
        cookieHttpOnly = settings.getBoolean(SETTINGS_KEY_COOKIE_HTTP_ONLY, true);
        cookieHost = settings.getString(SETTINGS_KEY_COOKIE_HOST);
        port = settings.getInt("port");
        this.timeout = timeout;
    }

    public String cookieName() {
        return cookieName;
    }

    protected Cookie findCookie(HttpEvent evt) {
        Cookie[] cookies = evt.header(Headers.COOKIE);
        Cookie cookie = null;
        if (cookies != null && cookies.length > 0) {
            for (Cookie ck : cookies) {
                if (cookieName.equals(ck.getName())) {
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

    private static final AsciiString LOCALHOST = new AsciiString("localhost");

    protected void configureCookie(HttpEvent evt, Cookie cookie) {
        CharSequence cookieHost = this.cookieHost;
        if (cookieHost == null) {
            cookieHost = evt.header(Headers.HOST);
            if (cookieHost != null) {
                int ix = Strings.lastIndexOf(':', cookieHost);
                if (ix > 0) {
                    cookieHost = cookieHost.subSequence(0, cookieHost.toString().lastIndexOf(":"));
                }
            }
        }
        if (cookieHost == null) {
            cookieHost = LOCALHOST;
            if (!logged) {
                logged = true;
                System.err.println("cookie.host not set and no host header found - using localhost");
            }
        }
        boolean use = cookieHost == null || (cookieHost != null && !Strings.startsWith(cookieHost, LOCALHOST));
        if (use) {
            // these fail in chrome on localhost
            if (useCookieHost) {
                cookie.setDomain(cookieHost.toString());
            }
            if (cookieHttpOnly) {
                cookie.setHttpOnly(true);
            }
            if (useCookiePorts) {
                cookie.setPorts(cookiePorts());
            }
        }
    }

    /**
     * Write the authentication cookie (using the configured name for it) to the
     * response.
     *
     * @param evt The request
     * @param cookieValue The value - which should be something opaque and
     * random which can be used as a cache key to look up the user on the server
     * side
     * @param on
     */
    public void setCookie(HttpEvent evt, String cookieValue, Response on) {
        DefaultCookie ck = new DefaultCookie(cookieName, cookieValue);
        ck.setMaxAge(timeout.get().getSeconds());
        configureCookie(evt, ck);
        on.add(Headers.SET_COOKIE, ck);
    }

    public void discardCookie(HttpEvent evt, Response on) {
        DefaultCookie cookie = new DefaultCookie(cookieName(), "");
        configureCookie(evt, cookie);
        cookie.setDiscard(true);
        cookie.setMaxAge(0);
        on.add(Headers.SET_COOKIE, cookie);
    }
}
