package com.mastfrog.acteur.annotations;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.PutTest;
import com.mastfrog.acteur.SilentRequestLogger;
import com.mastfrog.acteur.annotations.X.Barble;
import com.mastfrog.acteur.annotations.X.Fooble;
import com.mastfrog.acteur.annotations.X.Wurg;
import com.mastfrog.function.misc.QuietAutoClosable;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.strings.RandomStrings;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

@TestWith({GenericApplicationModule.class, SilentRequestLogger.class})
public class GenericApplicationTest {

    static class M extends AbstractModule {

        @Override
        protected void configure() {
//            bind(String.class).annotatedWith(Names.named("realm")).toInstance("hellothere");
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
        assertEquals(types, expect, "GOT " + types);
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
                    try (QuietAutoClosable ac = PutTest.setPage(app, p)) {
                        List<Object> acteurs = PutTest.acteursFor(p);
                        for (int i = 0; i < acteurs.size(); i++) {
                            Object a = acteurs.get(i);
                            assertNotNull(a);
                            String desc = i + ". " + a.getClass().getSimpleName()
                                    + " " + (a instanceof Class<?> ? ((Class<?>) a).getSimpleName() : a);
//                            System.out.println(desc);
                            switch (i) {
                                case 0:
                                    assertEquals("MatchPath", a.getClass().getSimpleName(), desc);
                                    assertTrue(a instanceof Acteur, desc);
                                    break;
                                case 1:
                                    assertEquals("MatchMethods", a.getClass().getSimpleName(), desc);
                                    assertTrue(a instanceof Acteur, desc);
                                    break;
                                // 2 is an anonymous class
                                case 2:
                                    assertTrue(a instanceof Acteur, desc);
                                    break;
                                case 3:
                                    assertEquals("BanParameters", a.getClass().getSimpleName(), desc);
                                    assertTrue(a instanceof Acteur, desc);
                                    break;
                                case 4:
                                    assertEquals("RequireAtLeastOneParameter", a.getClass().getSimpleName(), desc);
                                    assertTrue(a instanceof Acteur, desc);
                                    break;
                                case 5:
                                    assertEquals("NumberParameters", a.getClass().getSimpleName(), desc);
                                    assertTrue(a instanceof Acteur, desc);
                                    break;
                                case 6:
                                case 7:
                                    assertTrue(a instanceof Acteur, desc);
                                    break;
//                                case 8:
//                                    assertEquals("WrapperActeur", a.getClass().getSimpleName(), desc);
//                                    assertTrue(a instanceof Acteur, desc);
//                                    assertTrue(a.toString().contains("AuthenticationActeur"), desc);
//                                    break;
                                case 8:
                                    assertSame(Fooble.class, a, desc);
                                    break;
                                case 9:
                                    assertSame(Barble.class, a, desc);
                                    break;
                                case 10:
                                    assertSame(X.class, a, desc);
                                    break;
                                case 11:
                                    assertSame(Wurg.class, a, desc);
                                    break;
                                default:
                                    fail("Unexpected acteur " + desc);
                            }
                        }
                    }
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
