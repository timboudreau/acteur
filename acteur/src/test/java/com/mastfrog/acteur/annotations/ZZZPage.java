package com.mastfrog.acteur.annotations;

import com.google.inject.Inject;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Page;
import static com.mastfrog.acteur.headers.Method.POST;
import com.mastfrog.acteur.preconditions.Methods;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order=-1)
@Methods(POST)
public class ZZZPage extends Page {

    @Inject
    ZZZPage(ActeurFactory af) {
        add(af.respondWith(HttpResponseStatus.CREATED, "Foo."));
    }
}
