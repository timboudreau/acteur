package com.mastfrog.acteur;

import com.google.inject.Inject;
import com.mastfrog.acteur.TestAnnotations.M;
import static com.mastfrog.acteur.headers.Method.POST;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PageAnnotationHandler;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.preconditions.RequiredUrlParameters;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.List;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(M.class)
public class TestAnnotations {

    @Test
    public void test(TestHarness harn) throws IOException, Throwable {
        harn.get("one").go().assertStatus(BAD_REQUEST);
        assertTrue(annotationHandlerCalled);
        harn.get("one").addQueryPair("foo", "hey").go().assertStatus(BAD_REQUEST);
        harn.get("one").addQueryPair("foo", "hey").addQueryPair("bar", "you").go().assertStatus(OK).assertContent("one");
        harn.get("two").go().assertStatus(BAD_REQUEST);
        harn.get("two").addQueryPair("baz", "hey").go().assertStatus(OK).assertContent("two");
        harn.get("two").addQueryPair("quux", "you").go().assertStatus(OK).assertContent("two");
        harn.get("three").go().assertStatus(NOT_FOUND);
        harn.post("three").go().assertStatus(OK).assertContent("three");
        // Also test that default CORS headers work
        harn.options("foo").go().assertStatus(NO_CONTENT);
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
        }
    }

    static class M extends ServerModule {

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
