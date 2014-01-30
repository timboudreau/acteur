package com.mastfrog.acteur.generic.app;

import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class GenericApplicationTest {

    @Test
    public void testSomeMethod() throws IOException {
        Dependencies deps = Dependencies.builder().add(new ServerModule(GenericApplication.class)).add(SettingsBuilder.createDefault().build(), Namespace.DEFAULT).build();
        GenericApplication app = deps.getInstance(GenericApplication.class);
        int ix = 0;
        for (Page p : app) {
            System.out.println(ix + " - " + p);
            switch (ix++) {
                case 0:
                    assertTrue(ix + "", p instanceof ZZZPage);
                    break;
                case 1:
                    assertTrue(ix + "", p instanceof FakePage);
                    break;
                case 2:
                    assertTrue(ix + "", p instanceof AnotherPage);
                    break;
                default:
                    throw new AssertionError(ix);
            }
            System.out.println("PAGE " + p);
        }
        assertEquals(3, ix);
    }
}
