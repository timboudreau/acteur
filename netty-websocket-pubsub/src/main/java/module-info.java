// Generated by com.dv.sourcetreetool.impl.App
open module com.mastfrog.netty.websocket.pubsub {
    exports com.mastfrog.acteur.pubsub;

    // Inferred from source scan
    requires com.mastfrog.collections;

    // Sibling com.mastfrog/giulius-3.0.0-dev

    // Transitive detected by source scan
    requires com.mastfrog.giulius;

    // Sibling com.mastfrog/marshaller-registry-3.0.0-dev
    requires com.mastfrog.marshaller.registry;

    // Inferred from source scan
    requires com.mastfrog.preconditions;

    // derived from io.netty/netty-codec-http-0.0.0-? in io/netty/netty-codec-http/4.1.76.Final/netty-codec-http-4.1.76.Final.pom
    requires io.netty.codec.http;
    requires io.netty.transport;
    requires javax.inject;
}
