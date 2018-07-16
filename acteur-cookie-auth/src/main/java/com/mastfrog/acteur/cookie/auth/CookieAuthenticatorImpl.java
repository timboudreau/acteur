package com.mastfrog.acteur.cookie.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.cookie.auth.UserFinder.UserAndCookieValue;
import com.mastfrog.util.preconditions.Checks;
import io.netty.handler.codec.http.cookie.Cookie;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class CookieAuthenticatorImpl implements CookieAuthenticator {

    private final AuthCookieWriter writer;

    private final UserFinder<?> userFinder;

    @Inject
     CookieAuthenticatorImpl(AuthCookieWriter writer, UserFinder<?> userFinder) {
        this.writer = writer;
        this.userFinder = userFinder;
    }

    @Override
    public Object[] authenticateRequest(HttpEvent evt) throws Exception {
        Checks.notNull("evt", evt);
        Object[] result = null;
        Cookie ck = writer.findCookie(evt);
        if (ck != null) {
            String cookieValue = ck.value();
            if (cookieValue != null && !cookieValue.isEmpty()) {
                Object user = userFinder.findUser(evt, cookieValue);
                if (user != null) {
                    result = new Object[]{user};
                }
            }
        }
        return result;
    }

    @Override
    public Object[] login(HttpEvent evt, LoginInfo info, Response response) throws Exception {
        Checks.notNull("evt", evt);
        Checks.notNull("info", info);
        Checks.notNull("response", response);
        UserAndCookieValue<?> userAndCookie = userFinder.login(info, evt, response);
        if (userAndCookie != null) {
            writer.setCookie(evt, userAndCookie.cookie, response);
        }
        Object[] result = userAndCookie == null ? null : new Object[]{userAndCookie.user};
        return result;
    }

    @Override
    public void logout(HttpEvent evt, Response response) {
        Checks.notNull("evt", evt);
        Checks.notNull("response", response);
        writer.discardCookie(evt, response);
    }

    @Override
    public boolean login(Object user, HttpEvent evt, Response response) {
        Checks.notNull("user", user);
        Checks.notNull("evt", evt);
        Checks.notNull("response", response);
        Checks.isInstance("user", userFinder.type(), user);
        return login(user, userFinder, evt, response);
    }

    private <T> boolean login(Object user, UserFinder<T> finder, HttpEvent evt, Response response) {
        String cookie = finder.login(finder.type().cast(user), evt);
        if (cookie != null) {
            writer.setCookie(evt, cookie, response);
            return true;
        }
        return false;
    }

    @Override
    public String cookieName() {
        return writer.cookieName();
    }
}
