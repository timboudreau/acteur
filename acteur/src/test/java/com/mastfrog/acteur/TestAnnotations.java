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
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({M.class, HttpTestHarnessModule.class, SilentRequestLogger.class})
public class TestAnnotations {

    private static final long TIMEOUT = 60_000;
    private static final Duration TO = Duration.ofMillis(TIMEOUT);

    @Test
    @Timeout(value = 1, unit = MINUTES)
    public void test(HttpHarness harn) throws Throwable {
        harn.get("one").applyingAssertions(a -> a.assertResponseCode(400)).assertAllSucceeded();
        assertTrue(annotationHandlerCalled);
        harn.get("one?foo=hey").applyingAssertions(a -> a.assertResponseCode(400)).assertAllSucceeded();
        harn.get("one?foo=hey&bar=you").applyingAssertions(a -> a.assertOk().assertBody("one")).assertAllSucceeded();
        harn.get("two").applyingAssertions(Assertions::assertBadRequest).assertAllSucceeded();
        harn.get("two?baz=hey").applyingAssertions(a -> a.assertOk().assertBody("two")).assertAllSucceeded();
        harn.get("two?quux=you").applyingAssertions(a -> a.assertOk().assertBody("two")).assertAllSucceeded();
        harn.get("three").applyingAssertions(Assertions::assertNotFound).assertAllSucceeded();
        harn.post("three").applyingAssertions(a -> a.assertOk().assertBody("three")).assertAllSucceeded();

//        harn.get("one").setTimeout(TO).addQueryPair("foo", "hey").go().assertStatus(BAD_REQUEST);
//        harn.get("one").setTimeout(TO).addQueryPair("foo", "hey").addQueryPair("bar", "you").go().assertStatus(OK).assertContent("one");
//        harn.get("two").setTimeout(TO).go().assertStatus(BAD_REQUEST);
//        harn.get("two").setTimeout(TO).addQueryPair("baz", "hey").go().assertStatus(OK).assertContent("two");
//        harn.get("two").setTimeout(TO).addQueryPair("quux", "you").go().assertStatus(OK).assertContent("two");
//        harn.get("three").setTimeout(TO).go().assertStatus(NOT_FOUND);
//        harn.post("three").setTimeout(TO).go().assertStatus(OK).assertContent("three");
        // Also test that default CORS headers work
        harn.options("foo").applyingAssertions(Assertions::assertNoContent).assertAllSucceeded();
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
