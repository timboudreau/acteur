package com.mastfrog.acteur.cookie.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.util.Checks;
import java.util.concurrent.ThreadLocalRandom;
import org.netbeans.validation.api.InvalidInputException;
import org.netbeans.validation.api.Problems;

/**
 * User credentials sent in an HTTP request body to log in. Note: The password
 * returning method can be called exactly <i>once</i> - it overwrites the
 * original value with garbage upon being called.. Two properties must be
 * present in the JSON:
 * <ul>
 * <li><code>user</code> decodable as a <code>String</code>
 * <li><code>password</code> decodable as a <code>char[]</code>
 * </ul>
 * Other properties may be present but will be ignored.
 *
 * @author Tim Boudreau
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginInfo {

    private final String user;
    private final char[] password;

    @JsonCreator
    public LoginInfo(@JsonProperty("user") String user, @JsonProperty("password") char[] password) {
        Checks.notNull("user", user);
        Checks.notNull("password", password);
        if (password.length == 0) {
            throw new InvalidInputException(new Problems().append("Zero length password"));
        }
        this.user = user;
        this.password = password;
    }

    String user() {
        return user;
    }

    synchronized char[] getPassword() {
        char[] result = new char[password.length];
        System.arraycopy(password, 0, result, 0, result.length);
        for (int i = 0; i < password.length; i++) {
            password[i] = (char) ThreadLocalRandom.current().nextInt();
        }
        return result;
    }
}
