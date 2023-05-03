package com.mastfrog.acteur;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import static com.mastfrog.acteur.headers.Headers.CONTENT_LENGTH;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestMultiplePages.M.class, HttpTestHarnessModule.class})
public class TestMultiplePages {

    @Test
    public void test(HttpHarness harn) throws InterruptedException, IOException {
        harn.get("doesntmatter").applyingAssertions(a -> a.assertOk()).assertAllSucceeded();
    }

    static class M implements Module {

        @Override
        public void configure(Binder binder) {
            binder.install(new ServerModule(A.class, 1, 2, 64));
        }

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
            String text = id + " Okay, here goes nothing";
            add(CONTENT_LENGTH, text.getBytes(UTF_8).length);
        }
    }

    private static final class BlockingBodyWriter implements ChannelFutureListener {

        private final RequestID id;
        private final ExecutorService svc;
        private final HttpEvent evt;

        @Inject
        BlockingBodyWriter(RequestID id,
                @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService svc, HttpEvent evt) {
            this.id = id;
            this.svc = svc;
            this.evt = evt;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            future = future.channel().writeAndFlush(Unpooled.copiedBuffer(id
                    + " Okay, here goes nothing", UTF_8));
            future.addListener(CLOSE);
        }
    }
}
