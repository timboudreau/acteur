package com.mastfrog.acteur.util;

import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class PasswordHasherTest {
    
    @Test
    public void testCheckPassword() throws IOException, NoSuchAlgorithmException {
        assertTrue(true);
        PasswordHasher h = new PasswordHasher(new SettingsBuilder().build(), Charset.forName("UTF-8"));
        
        String pw = "password";
        String enc = h.encryptPassword(pw);
        assertNotNull(enc);
        
        String pw2 = "somethingElse";
        String enc2 = h.encryptPassword(pw2);
        assertNotNull(enc2);
        
        assertNotEquals(enc, enc2);
        
        boolean matches = h.checkPassword(pw2, enc2);
        assertTrue(matches);
        
        matches = h.checkPassword(pw, enc);
        assertTrue(matches);
        
        matches = h.checkPassword(pw2, enc);
        assertFalse(matches);
        
        matches = h.checkPassword(pw, enc2);
        assertFalse(matches);
    }
}
