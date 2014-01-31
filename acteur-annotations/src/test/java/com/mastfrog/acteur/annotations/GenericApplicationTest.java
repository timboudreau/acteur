package com.mastfrog.acteur.annotations;

import com.google.inject.AbstractModule;
import com.mastfrog.acteur.Page;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.GUIDFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class GenericApplicationTest {

    @Test
    public void testRegistry() {
        HttpCallRegistryLoader ldr = new HttpCallRegistryLoader(GenericApplicationTest.class);
        Set<Class<?>> types = ldr.implicitBindings();
        assertNotNull("Types is null", types);
        Set<Class<?>> expect = new LinkedHashSet<>(Arrays.asList(new Class<?>[]{String.class, Integer.class, GUIDFactory.class, FakePage.Foo.Bar.class}));
        assertTrue("GOT " + types, types.equals(expect));
    }

    @Test
    public void testRegistration() throws IOException {
        Settings settings = SettingsBuilder.createDefault().build();
        Dependencies deps = Dependencies.builder().add(new GenericApplicationModule(settings)).add(settings, Namespace.DEFAULT).build();
        Short moduleCheck = deps.getInstance(Short.class);
        assertNotNull(moduleCheck);
        assertEquals(537, moduleCheck.intValue());
        assertEquals("Kilroy was here", deps.getInstance(StringBuilder.class) + "");
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
                case 3 :
                    assertTrue(ix +"", p instanceof X__GenPage);
                    break;
                default:
                    throw new AssertionError(ix);
            }
            System.out.println("PAGE " + p);
        }
        assertEquals(4, ix);
    }

    @GuiceModule
    static class DummyModule extends AbstractModule {

        @Override
        protected void configure() {
            System.out.println("Configure dummy module");
            bind(Short.class).toInstance((short)537);
        }
    }

    @GuiceModule
    static class AnotherDummyModule extends AbstractModule {

        AnotherDummyModule(Settings settings) {

        }

        @Override
        protected void configure() {
            System.out.println("Configure dummy module");
            bind(StringBuilder.class).toInstance(new StringBuilder("Kilroy was here"));
        }
    }

}
