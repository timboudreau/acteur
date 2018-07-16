package com.mastfrog.acteur.cookie.auth;

import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import com.mastfrog.util.preconditions.Checks;

/**
 * Looks up a user in a database or wherever - to use Acteur Cookie
 * Authentication, this is the one class you need to implement. It handles
 * looking up users based on credentials, and storing and mapping session
 * cookies to users.
 *
 * @author Tim Boudreau
 */
public abstract class UserFinder<T> {

    private final Class<T> type;

    /**
     * Create a new UserFinder
     *
     * @param type The type used for User objects.
     */
    protected UserFinder(Class<T> type) {
        Checks.notNull("type", type);
        this.type = type;
    }

    /**
     * Look up a user based on a cookie value
     *
     * @param cookieValue The cookie value
     * @return A user object (of whatever type the application uses), or null if
     * authentication failed
     * @throws Exception If something goes wrong
     */
    public abstract T findUser(HttpEvent evt, String cookieValue) throws Exception;

    /**
     * Log in a user, creating a new session (perhaps, storing something
     * in a database for it) and returning the value to assign to the
     * HTTP cookie and the user object.
     *
     * @param info Login info deserialized from the request body or somewhere
     * @param evt The HTTP event, for extracting the Host header for the cookie
     * if cookie.host is not set
     * @param response The response
     * @return A wrapper for both the user object and the cookie value that
     * should be used to securely identify the user.
     * @throws Exception If something goes wrong
     */
    public abstract UserAndCookieValue<T> login(LoginInfo info, HttpEvent evt, Response response) throws Exception;

    /**
     * Log in an existing user object, returning a cookie value to write
     * to the response.
     *
     * @param user A user object of the type used by the application
     * @param evt The http request that triggered this call
     * @return A string that should be stored in a session cookie, or none if
     * the login should fail
     */
    public abstract String login(T user, HttpEvent evt);

    /**
     * The type used for user objects.
     *
     * @return The type
     */
    public final Class<T> type() {
        return type;
    }

    /**
     * Get the user name for a LoginInfo.
     *
     * @param info The LoginInfo.
     * @return The user name
     */
    protected final String usernameFor(LoginInfo info) {
        return info.user();
    }

    /**
     * Get the password contained in a LoginInfo. Note that this method may only
     * be called exactly once for a LoginInfo - after that the LoginInfo will
     * overwrite the data with random characters.
     *
     * @param info The login info
     * @return The password as a char array
     */
    protected final char[] passwordFor(LoginInfo info) {
        return info.getPassword();
    }

    /**
     * Just combines a cookie value from the {@link UserFinder} and a user
     * object.
     *
     * @param <T> The type of user object
     */
    public static final class UserAndCookieValue<T> {

        /**
         * The user object, never null
         */
        public final T user;
        /**
         * The cookie value, never null
         */
        public final String cookie;

        public UserAndCookieValue(T user, String cookie) {
            Checks.notNull("user", user);
            Checks.notNull("cookie", cookie);
            this.user = user;
            this.cookie = cookie;
        }
    }
}
