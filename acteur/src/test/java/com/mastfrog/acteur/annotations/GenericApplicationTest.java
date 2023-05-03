package com.mastfrog.acteur.annotations;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.SilentRequestLogger;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.strings.RandomStrings;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

@TestWith({GenericApplicationModule.class, SilentRequestLogger.class})
public class GenericApplicationTest {

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(new TypeLiteral<Class<?>[]>() {
            }).annotatedWith(Names.named("excluded")).toInstance(new Class[0]);
        }
    }

    @Test
    public void testRegistry() {
        HttpCallRegistryLoader ldr = new HttpCallRegistryLoader(GenericApplicationTest.class);
        Set<Class<?>> types = ldr.implicitBindings();
        assertNotNull(types, "Types is null");
        Set<Class<?>> expect = new LinkedHashSet<>(Arrays.asList(new Class<?>[]{String.class, Integer.class, RandomStrings.class, FakePage.Foo.Bar.class, FakePage.Foo.class}));
        assertTrue(types.equals(expect), "GOT " + types);
    }

    @Test
    public void testRegistration(Dependencies deps) throws IOException {
        Short moduleCheck = deps.getInstance(Short.class);
        assertNotNull(moduleCheck);
        assertEquals(537, moduleCheck.intValue());
        assertEquals("Kilroy was here", deps.getInstance(StringBuilder.class) + "");
        GenericApplication app = deps.getInstance(GenericApplication.class);
        int ix = 0;
        for (Page p : app) {
            switch (ix++) {
                case 0:
                    assertTrue(p instanceof ZZZPage, ix + " " + p.getClass().getName());
                    break;
                case 1:
                    assertTrue(p instanceof FakePage, ix + " " + p.getClass().getName());
                    break;
                case 2:
                    assertTrue(p instanceof AnotherPage, ix + " " + p.getClass().getName());
                    break;
                case 3:
                    assertTrue(p instanceof X__GenPage, ix + " " + p.getClass().getName());
                    break;
                default:
                    throw new AssertionError(ix);
            }
        }
        assertEquals(4, ix);
    }

    @GuiceModule
    static class DummyModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(Short.class).toInstance((short) 537);
        }
    }

    @GuiceModule
    static class AnotherDummyModule extends AbstractModule {

        AnotherDummyModule(Settings settings) {

        }

        @Override
        protected void configure() {
            bind(StringBuilder.class).toInstance(new StringBuilder("Kilroy was here"));
        }
    }

}
