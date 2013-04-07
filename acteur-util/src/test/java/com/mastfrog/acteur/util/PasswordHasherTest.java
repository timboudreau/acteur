package com.mastfrog.acteur.util;

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class PasswordHasherTest {
    

    @Test
    public void testCheckPassword() throws IOException {
        assertTrue(true);
        Dependencies deps = Dependencies.builder().addDefaultSettings().build();
        PasswordHasher h = deps.getInstance(PasswordHasher.class);
        
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
