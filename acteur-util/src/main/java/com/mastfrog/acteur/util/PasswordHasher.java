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
package com.mastfrog.acteur.util;

import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Exceptions;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.binary.Base64;

/**
 * Hashes passwords into hashes which can be tested for matching but which
 * the original password cannot be easily decrypted from.  The default
 * algorithm is SHA-512.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class PasswordHasher {

    private final byte[] saltBytes;
    private final String algorithm;
    // A plokta default:
    static final String DEFAULT_SALT = "wlkefjasdfhadasdlkfjhwa,l.e,u.f,2.o3ads[]90as!!_$GHJM"
            + "<$^UJCMM<>OIUHGC^#YUJKTGYSUCINJd9f0awe0f9aefansjneaiw"
            + "aoeifa222222222222o(#(#(&@^!";
    private final Charset charset;

    @Inject
    PasswordHasher(Settings settings, Charset charset) throws NoSuchAlgorithmException {
        this.charset = charset;
        String salt = settings.getString("salt", DEFAULT_SALT);
        String alg = settings.getString("passwordHashingAlgorithm", "SHA-512");
        if (settings.getBoolean("productionMode", false) && DEFAULT_SALT.equals(salt)) {
            throw new ConfigurationError("Default password salt should not be used in "
                    + "production mode.  Set property salt for namespace timetracker to "
                    + "be something else");
        }
        saltBytes = salt.getBytes(charset);
        this.algorithm = alg;
        // fail early
        hash("abcd");
    }

    public boolean checkPassword(String unhashed, String hashed) {
        try {
            byte[] bytes = hash(unhashed);
            byte[] check = Base64.decodeBase64(hashed);
            return Arrays.equals(bytes, check);
        } catch (NoSuchAlgorithmException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public String encryptPassword(String password) {
        try {
            return Base64.encodeBase64String(hash(password));
        } catch (NoSuchAlgorithmException ex) {
            return Exceptions.chuck(ex);
        }
    }

    private byte[] hash(String unhashed) throws NoSuchAlgorithmException {
        MessageDigest dg = MessageDigest.getInstance(algorithm);
        byte[] b = (unhashed + ":" + new String(saltBytes)).getBytes(charset);
        byte[] check = dg.digest(b);
        return check;
    }
}
