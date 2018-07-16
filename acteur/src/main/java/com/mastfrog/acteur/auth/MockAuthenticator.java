/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.acteur.util.BasicCredentials;
import com.google.inject.Singleton;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.settings.Settings;
import java.io.IOException;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class MockAuthenticator implements Authenticator {

    private final String mockPassword;
    private final PasswordHasher hasher;

    @Inject
    MockAuthenticator(Settings settings, PasswordHasher hasher) {
        this.hasher = hasher;
        mockPassword = hasher.encryptPassword(settings.getString("mockPassword", "password"));
        if (Dependencies.isProductionMode(settings)) {
            throw new ConfigurationError("Using mock " + getClass().getName() + " in production");
        }
    }

    @Override
    public Object[] authenticate(String realm, BasicCredentials credentials) throws IOException {
        if (hasher.checkPassword(credentials.password, mockPassword)) {
            return new Object[]{credentials};
        }
        return null;
    }
}
