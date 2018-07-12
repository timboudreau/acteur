package com.mastfrog.acteur;

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.PutTest.SM;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Types;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.io.IOException;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, SM.class}) // Use these Guice modules
@RunWith(GuiceRunner.class) // Use the Guice-Tests JUnit runner
public class PutTest {

    public static void main(String[] args) throws IOException, InterruptedException {
        System.setProperty("acteur.debug", "true");
        new ServerBuilder().applicationClass(EchoServer.class).add(
                Settings.builder().add("port", 8123).build()).build().start().await();
    }

    // Just subclasses ServerModule to provide the application class
    static class SM extends ServerModule<EchoServer> implements RequestLogger {

        SM() {
            super(EchoServer.class, 2, 2, 3);
        }

        @Override
        protected void configure() {
            super.configure();
            bind(RequestLogger.class).toInstance(this);
        }

        @Override
        public void onBeforeEvent(RequestID rid, Event<?> event) {
            // do nothing
        }

        @Override
        public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
            // do nothing
        }
    }

    @Test(timeout = 180000L)
    public void testPuts(TestHarness harn, Application application) throws Throwable {
        System.setProperty("acteur.debug", "true");
        harn.get("foo/bar/baz").go().assertStatus(OK).assertContent("Hello world");
        harn.get("/").go().assertStatus(OK).assertContent("Hello world");
        for (int i = 0; i < 20; i++) {

            harn.put("/").addHeader(Headers.header("X-Iteration"), "" + i)
                    .onEvent(new Receiver<com.mastfrog.netty.http.client.State<?>>() {
                        @Override
                        public void receive(com.mastfrog.netty.http.client.State<?> state) {
                            if (state.get() instanceof ByteBuf) {
                                ByteBuf buf = (ByteBuf) state.get();
                                buf.resetReaderIndex();
                            }
                        }
                    })
                    .setBody("Test " + i + " iter", MediaType.PLAIN_TEXT_UTF_8).go()
                    .await()
                    .assertStatus(OK)
                    //                    .assertStateSeen(com.mastfrog.netty.http.client.StateType.FullContentReceived)
                    .assertContent("Test " + i + " iter");
        }
        harn.get(veryLongUrl(35)).setTimeout(Duration.ofMinutes(2)).go().await().assertStatus(OK);
    }

    private String veryLongUrl(int amt) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < amt; i++) {
            sb.append("/0123456789");
        }
        return sb.toString();
    }

    static class EchoServer extends Application {

        EchoServer() {
            add(EchoPage.class);
        }

        @Methods({PUT, GET})
        private static final class EchoPage extends Page {

            @Inject
            EchoPage(ActeurFactory af) {
                add(EchoActeur.class);
            }
        }
    }

    static int ix = 0;

    static class EchoActeur extends Acteur {

        @Inject
        EchoActeur(HttpEvent evt) throws IOException {
            add(Headers.CACHE_CONTROL, CacheControl.$(CacheControlTypes.Public));
            if (evt.method() == Method.GET) {
                setState(new RespondWith(HttpResponseStatus.OK, "Hello world"));
            } else {
                if (evt.content() == null || evt.content().readableBytes() == 0) {
                    badRequest("No request body in PUT");
                    return;
                }
                setState(new RespondWith(HttpResponseStatus.OK));
                boolean chunked = ix++ % 2 != 0;
                setChunked(chunked);
                if (!chunked) {
                    add(Headers.CONTENT_LENGTH, evt.content().readableBytes());
                }
                setResponseWriter(RWriter.class);
            }
        }
    }

    private static class RWriter extends ResponseWriter {

        volatile boolean ran;

        @Override
        public ResponseWriter.Status write(Event<?> evt, Output out) throws Exception {
            if (ran) {
                return ResponseWriter.Status.DONE;
            }
            FullHttpRequest req = evt.request() instanceof FullHttpRequest
                    ? (FullHttpRequest) evt.request() : null;
            if (req != null) {
                ran = true;
                ByteBuf buf = req.content();
                out.write(buf);
                ran = true;
            } else {
                throw new AssertionError("Not a FullHttpRequest: " + evt.request() + " " + Types.list(evt.request().getClass()));
            }
            return ResponseWriter.Status.NOT_DONE;
        }
    }
}
