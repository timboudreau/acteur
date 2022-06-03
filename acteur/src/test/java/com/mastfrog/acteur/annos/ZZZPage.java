package com.mastfrog.acteur.annos;

import com.google.inject.Inject;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.Page;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order=-1)
public class ZZZPage extends Page {

    @Inject
    ZZZPage(ActeurFactory af) {
        add(af.respondWith(HttpResponseStatus.CREATED, "Foo."));
    }
}
