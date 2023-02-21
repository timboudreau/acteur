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

import com.mastfrog.giulius.annotations.Setting;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.UniqueIDs;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import javax.inject.Inject;
//import java.util.Base64;
import javax.inject.Singleton;

/**
 * Hashes passwords into hashes which can be tested for matching but which the
 * original password cannot be easily decrypted from. The default algorithm is
 * SHA-512.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class PasswordHasher {

    private final byte[] saltBytes;
    private final String algorithm;
    // A plokta def salt:
    static final String DEFAULT_SALT = "wlkefjasdfhadasdlkfjhwa,l.e,u.f,2.o3ads[]90as!!_$GHJM"
            + "<$^UJCMM<>OIUHGC^#YUJKTGYSUCINJd9f0awe0f9aefansjneaiw"
            + "aoeifa222222222222o(#(#(&@^!";
    private final Charset charset;
    @Setting(value = "PasswordHasher: Fixed password salt for use IN TESTS (will throw a "
            + "ConfigurationError if used in production mode)", defaultValue = "48")
    public static final String SETTINGS_KEY_PASSWORD_SALT = "salt";
    public static final String SETTINGS_KEY_HASHING_ALGORITHM = "passwordHashingAlgorithm";
    public static final String DEFAULT_HASHING_ALGORITHM = "SHA-512";
    @Setting(value = "PasswordHasher: Length of random salt to use when generating salted hashes "
            + "of passwords", defaultValue = "48")
    public static final String SETTINGS_KEY_RANDOM_SALT_LENGTH = "randomSaltLength";
    public static final int DEFAULT_RANDOM_SALT_LENGTH = 48;
    private final UniqueIDs guids = UniqueIDs.noFile();
    private final int saltLength;

    @Inject
    PasswordHasher(Settings settings, Charset charset) throws NoSuchAlgorithmException {
        this.charset = charset;
        saltLength = settings.getInt(SETTINGS_KEY_RANDOM_SALT_LENGTH, DEFAULT_RANDOM_SALT_LENGTH);
        if (saltLength <= 0) {
            throw new ConfigurationError("Salt length must be > 0");
        }
        String salt = settings.getString(SETTINGS_KEY_PASSWORD_SALT, DEFAULT_SALT);
        String alg = settings.getString(SETTINGS_KEY_HASHING_ALGORITHM, DEFAULT_HASHING_ALGORITHM);
        if (settings.getBoolean("productionMode", false) && DEFAULT_SALT.equals(salt)) {
            new ConfigurationError("Default password salt should not be used in "
                    + "production mode.").printStackTrace();
            System.exit(1);
        }
        saltBytes = salt.getBytes(charset);
        this.algorithm = alg;
        // fail early
        hash("abcd", alg);
    }

    private String[] findSalt(String hashed) {
        String[] result = hashed.split(":", 3);
        return result;
    }

    /**
     * Check an unhashed password against a stored, hashed one
     *
     * @param unhashed The unhashed password
     * @param hashed
     * @return
     */
    public boolean checkPassword(String unhashed, String hashed) {
        try {
            String[] saltAndPassAndAlgorithm = findSalt(hashed);
            if (saltAndPassAndAlgorithm.length == 1) {
                // Backward compatibility
                byte[] bytes = hash(unhashed, algorithm);
                byte[] check = Base64.getDecoder().decode(hashed);
                return Arrays.equals(bytes, check);
            }
            if (saltAndPassAndAlgorithm.length != 3) {
                // Ensure a failed attempt takes the same amount of time
                encryptPassword("DaCuBYLAlxBbT6lTyatauRp2iXCsf9WGDi8a2SyWeFVsoxGBk3Y3l1l9IHie"
                        + "+aVuOGQBD8mZlrhj8yGjl1ghjw==", "3J5pgcx0", algorithm);
                return false;
            }
            String enc = encryptPassword(unhashed, saltAndPassAndAlgorithm[1], decodeAlgorithm(saltAndPassAndAlgorithm[0]));
            return slowEquals(enc, hashed);
        } catch (NoSuchAlgorithmException ex) {
            return Exceptions.chuck(ex);
        }
    }

    private boolean slowEquals(String a, String b) {
        // Compare all the characters of the string, so that
        // the comparison time is the same whether equal or not
        boolean result = true;
        int max = Math.min(a.length(), b.length());
        for (int i = 0; i < max; i++) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);
            result &= ca == cb;
        }
        return result && a.length() == b.length();
    }

    private String encryptPassword(String password, String randomSalt, String algorithm) throws NoSuchAlgorithmException {
        return encodeAlgorithm(algorithm) + ":" + randomSalt + ":" + Base64.getEncoder().encodeToString(hash(password + randomSalt, algorithm));
    }

    public String hash(String s) {
        try {
            return Base64.getEncoder().encodeToString(hash(s, algorithm));
        } catch (NoSuchAlgorithmException ex) {
            return Exceptions.chuck(ex);
        }
    }

    private String encodeAlgorithm(String alg) {
        // SHorten the stored strings a smidgen
        switch (alg) {
            case "SHA-512":
                return "a";
            case "SHA-1":
                return "b";
            case "MD2":
                return "c";
            case "MD5":
                return "d";
            case "SHA-256":
                return "e";
            case "SHA-384":
                return "f";
        }
        return alg;
    }

    private String decodeAlgorithm(String alg) {
        // Reconstitute algorithm strings
        switch (alg) {
            case "a":
                return "SHA-512";
            case "b":
                return "SHA-1";
            case "c":
                return "MD2";
            case "d":
                return "MD5";
            case "e":
                return "SHA-256";
            case "f":
                return "SHA-384";
        }
        return alg;
    }

    public String encryptPassword(String password) {
        try {
            String randomSalt = guids.newId();
            String result = encryptPassword(password, randomSalt, algorithm);
            return result;
        } catch (NoSuchAlgorithmException ex) {
            return Exceptions.chuck(ex);
        }
    }

    private byte[] hash(String unhashed, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest dg = MessageDigest.getInstance(algorithm);
        byte[] b = (unhashed + ":" + new String(saltBytes)).getBytes(charset);
        byte[] check = dg.digest(b);
        return check;
    }
}
