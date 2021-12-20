package com.mastfrog.acteur;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.net.PortFinder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TestMultiplePages {

    @Test
    @SuppressWarnings("deprecation")
    public void test() throws InterruptedException, IOException {
        ServerModule<A> m = new ServerModule<>(A.class, 1, 2, 64);
        Dependencies deps = new Dependencies(m);
        Server serv = deps.getInstance(Server.class);
        PortFinder pf = new PortFinder();
        // XXX what is this test for?
        serv.start(pf.findAvailableServerPort()).shutdown(true);
    }

    private static class A extends Application {

        A() {
            add(RP1.class);
            add(RP2.class);
            add(P.class);
        }
    }

    private final static class P extends Page {

        P() {
            add(ActA.class);
            add(Act.class);
        }
    }

    private static final class RP1 extends Page {

        RP1() {
            add(Rej.class);
        }
    }

    private static final class RP2 extends Page {

        RP2() {
            add(Rej.class);
        }
    }

    private static final class Rej extends Acteur {

        Rej() {
            setState(new RejectedState());
        }
    }

    private static final class ActA extends Acteur {

        ActA() {
            next();
        }
    }

    private static final class Act extends Acteur {

        @Inject
        Act(HttpEvent evt, RequestID id) {
            setResponseBodyWriter(BlockingBodyWriter.class);
            setState(new RespondWith(HttpResponseStatus.OK));
        }
    }

    private static class BlockingBodyWriter implements ChannelFutureListener {

        private final RequestID id;
        private final ExecutorService svc;
        private final HttpEvent evt;

        @Inject
        BlockingBodyWriter(RequestID id, @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService svc, HttpEvent evt) {
            this.id = id;
            this.svc = svc;
            this.evt = evt;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            future = future.channel().write(Unpooled.copiedBuffer(id
                    + " Okay, here goes nothing", CharsetUtil.UTF_8));
            future.addListener(CLOSE);
        }
    }
}
