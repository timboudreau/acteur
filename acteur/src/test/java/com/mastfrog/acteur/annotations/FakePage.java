package com.mastfrog.acteur.annotations;

import com.google.inject.Inject;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.annotations.FakePage.Foo.Bar;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.util.strings.RandomStrings;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order = 1_000, scopeTypes = {String.class, Integer.class, RandomStrings.class, Bar.class})
@Methods(PUT)
public class FakePage extends Page {

    @Inject
    public FakePage(ActeurFactory af) {
        add(af.respondWith(HttpResponseStatus.CREATED, "I am created"));
    }

    public static class Foo {

        public static class Bar {

        }
    }
}
