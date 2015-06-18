package com.mastfrog.acteur.annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.GUIDFactory;
import com.mastfrog.util.collections.MapBuilder;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.joda.time.Duration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({GenericApplicationModule.class, TestHarnessModule.class})
@RunWith(GuiceRunner.class)
public class GenericApplicationTest {

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings s = new SettingsBuilder().add("acteur.debug", "true").build();
        new ServerBuilder().add(s).build().start(8080).await();
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(new TypeLiteral<Class<?>[]>(){}).annotatedWith(Names.named("excluded")).toInstance(new Class[0]);
        }
    }

    @Test
    public void testRegistry() {
        HttpCallRegistryLoader ldr = new HttpCallRegistryLoader(GenericApplicationTest.class);
        Set<Class<?>> types = ldr.implicitBindings();
        assertNotNull("Types is null", types);
        Set<Class<?>> expect = new LinkedHashSet<>(Arrays.asList(new Class<?>[]{String.class, Integer.class, GUIDFactory.class, FakePage.Foo.Bar.class, FakePage.Foo.class, com.mastfrog.acteur.annotations.NumblePageParams.class}));
        assertTrue("GOT " + types, types.equals(expect));
    }

    @Test(timeout = 7000)
    @SuppressWarnings("unchecked")
    public void testNumble(TestHarness harn, ObjectMapper mapper) throws IOException, Throwable {
        Map<String, Object> m = new MapBuilder().put("host", "timboudreau.com")
                .put("port", 8080).put("bool", false).build();

        CallResult res = harn.put("/numble").log().setTimeout(Duration.standardSeconds(20))
                .setBody(m, MediaType.JSON_UTF_8).go().await().assertCode(200);
        Map<String, Object> nue = res.content(Map.class);
        assertEquals(m, nue);
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
            System.out.println(ix + " - " + p);
            switch (ix++) {
                case 0:
                    assertTrue(ix + " " + p.getClass().getName(), p instanceof NumblePage__GenPage);
                    break;
                case 1:
                    assertTrue(ix + " " + p.getClass().getName(), p instanceof ZZZPage);
                    break;
                case 2:
                    assertTrue(ix + " " + p.getClass().getName(), p instanceof FakePage);
                    break;
                case 3:
                    assertTrue(ix + " " + p.getClass().getName(), p instanceof AnotherPage);
                    break;
                case 4:
                    assertTrue(ix + " " + p.getClass().getName(), p instanceof X__GenPage);
                    break;
                default:
                    throw new AssertionError(ix);
            }
            System.out.println("PAGE " + p);
        }
        assertEquals(5, ix);
    }

    @GuiceModule
    static class DummyModule extends AbstractModule {

        @Override
        protected void configure() {
            System.out.println("Configure dummy module");
            bind(Short.class).toInstance((short) 537);
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
