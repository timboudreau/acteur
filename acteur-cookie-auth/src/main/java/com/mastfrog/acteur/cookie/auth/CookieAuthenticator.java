package com.mastfrog.acteur.cookie.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;

/**
 * Object which can authenticate a user by looking up a cookie in a request or
 * processing credentials contained in a request.  If you need to directly
 * authenticate a request, ask for an instance of this to be injected - an 
 * implementation is part of this library.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(CookieAuthenticatorImpl.class)
public interface CookieAuthenticator {

    /**
     * The host value to use in cookies. If not set, the host header from the
     * request will be used unless it begins with "localhost".
     */
    public static final String SETTINGS_KEY_COOKIE_HOST = "cookie.host";
    /**
     * Whether auth cookies should be http-only. The default is true.
     */
    public static final String SETTINGS_KEY_COOKIE_HTTP_ONLY = "cookie.http.only";
    /**
     * Whether auth cookies should specify ports.
     */
    public static final String SETTINGS_KEY_USE_COOKIE_PORTS = "use.cookie.ports";
    /**
     * Whether auth headers should be used in non-localhost cookies.
     */
    public static final String SETTINGS_KEY_USE_COOKIE_HOST = "use.cookie.host";

    /**
     * The default name for the login cookie.
     */
    String DEFAULT_COOKIE_NAME = "ex";
    /**
     * Settings key which allows the name of the auth cookie to be specified by
     * configuration.
     */
    String SETTINGS_KEY_COOKIE_NAME = "auth.cookie.name";

    /**
     * Authenticate a user, returning an array of objects to inject into
     * subsequent Acteurs if successful, or null if failed.
     *
     * @param evt The request
     * @return An array of objects if authentication is successful, null if not
     * @throws Exception if something goes wrong
     */
    Object[] authenticateRequest(HttpEvent evt) throws Exception;

    /**
     * Log out, setting the response up to tell the browser to discard the auth
     * cookie.
     *
     * @param evt The request
     * @param response The response to write cookies to
     */
    void logout(HttpEvent evt, Response response);

    /**
     * Log in a user - optionally used for sign-up pages if no email
     * authentication is required for a user to be successfully logged in.
     *
     * @param user The user, which should be of the user type returned by the
     * bound {@link UserFinder}.. A <code>ClassCastException</code> will be
     * thrown if not.
     * @param evt The request
     * @param response The response to write cookies to
     * @return True if the login succeeded, false if the UserFinder vetoed it
     * for some reason
     */
    boolean login(Object user, HttpEvent evt, Response response);

    /**
     * Log in a user
     *
     * @param evt The request, in case the user finder wants to consult a
     * blacklist or similar
     * @param info The login info, most likely deserialized from the request
     * body
     * @param response The response, to decorate with a cookie
     * @return An array of objects, such as the user object, which should be
     * available to subsequent acteurs
     * @throws Exception If something goes wrong
     */
    Object[] login(HttpEvent evt, LoginInfo info, Response response) throws Exception;

    /**
     * The name of the authentication cookie (useful for tests that need to
     * authenticate).
     *
     * @return The cookie name
     */
    String cookieName();
}
