package com.mastfrog.acteur;

import com.google.inject.Inject;
import com.mastfrog.acteur.TestAnnotations.M;
import static com.mastfrog.acteur.headers.Method.POST;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PageAnnotationHandler;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.preconditions.RequiredUrlParameters;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.harness.Assertions;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({M.class, HttpTestHarnessModule.class, SilentRequestLogger.class})
public class TestAnnotations {

    @Test
    @Timeout(60)
    public void testCorsHeadersGetsNoContent(HttpHarness harn) throws Throwable {
        // Also test that default CORS headers work
        harn.options("foo").asserting(Assertions::assertNoContent).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testThreePost(HttpHarness harn) throws Throwable {
        harn.post("three").asserting(a -> a.assertOk().assertBody("three")).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testThreeGet(HttpHarness harn) throws Throwable {
        harn.get("three").asserting(Assertions::assertNotFound).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testTwoWithQuux(HttpHarness harn) throws Throwable {
        harn.get("two?quux=you").asserting(a -> a.assertOk().assertBody("two")).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testOne(HttpHarness harn) throws Throwable {
        harn.get("one").asserting(a -> a.assertStatus(400)).assertAllSucceeded();
        assertTrue(annotationHandlerCalled);
    }

    @Test
    @Timeout(60)
    public void testOneWithFoo(HttpHarness harn) throws Throwable {
        harn.get("one?foo=hey").asserting(a -> a.assertStatus(400)).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testOneWithFooAndBar(HttpHarness harn) throws Throwable {
        harn.get("one?foo=hey&bar=you").asserting(a -> a.assertOk().assertBody("one")).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testTwo(HttpHarness harn) throws Throwable {
        harn.get("two").asserting(Assertions::assertBadRequest).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testTwoWithBaz(HttpHarness harn) throws Throwable {
        harn.get("two?baz=hey").asserting(a -> a.assertOk().assertBody("two")).assertAllSucceeded();
    }

    @Methods
    @PathRegex("^one$")
    @Foo
    @RequiredUrlParameters(value = {"foo", "bar"}, combination = RequiredUrlParameters.Combination.ALL)
    static class One extends Page {

        @Inject
        One(ActeurFactory af) {
            add(af.respondWith(HttpResponseStatus.OK, "one"));
        }
    }

    @Methods
    @PathRegex("^two$")
    @RequiredUrlParameters(value = {"baz", "quux"}, combination = RequiredUrlParameters.Combination.AT_LEAST_ONE)
    static class Two extends Page {

        @Inject
        Two(ActeurFactory af) {
            add(af.respondWith(HttpResponseStatus.OK, "two"));
        }
    }

    @Methods(POST)
    @PathRegex("^three$")
    static class Three extends Page {

        @Inject
        Three(ActeurFactory af) {
            add(af.respondWith(HttpResponseStatus.OK, "three"));
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RUNTIME)
    public @interface Foo {

    }

    static boolean annotationHandlerCalled;

    static class PAH extends PageAnnotationHandler {

        @Inject
        PAH(PageAnnotationHandler.Registry reg) {
            super(reg, Foo.class);
        }

        @Override
        public <T extends Page> boolean processAnnotations(T page, List<? super Acteur> addTo) {
            annotationHandlerCalled = true;
            return true;
        }
    }

    static class A extends Application {

        A() {
            add(One.class);
            add(Two.class);
            add(Three.class);
            enableDefaultCorsHandling();
        }
    }

    static class M extends ServerModule<A> {

        M() {
            super(A.class);
        }

        @Override
        protected void configure() {
            super.configure();
            bind(PAH.class).asEagerSingleton();
        }
    }
}
