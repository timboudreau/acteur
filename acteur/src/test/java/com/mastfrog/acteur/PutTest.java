package com.mastfrog.acteur;

import com.google.inject.Inject;
import com.mastfrog.acteur.PutTest.SM;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.CacheControlTypes;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import static com.mastfrog.mime.MimeType.PLAIN_TEXT_UTF_8;
import com.mastfrog.settings.Settings;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({HttpTestHarnessModule.class, SM.class}) // Use these Guice modules
public class PutTest {

    public static void main(String[] args) throws IOException, InterruptedException {
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

    @Test
    @Timeout(value = 360000, unit = MILLISECONDS)
    public void testPuts(HttpHarness harn, Application application) throws Throwable {
        harn.get("foo/bar/baz").applyingAssertions(a -> a.assertResponseCode(200).assertBody("Hello world"))
                .assertAllSucceeded();
        harn.get("/").applyingAssertions(a -> a.assertResponseCode(200).assertBody("Hello world"))
                .assertAllSucceeded();

        for (int i = 0; i < 2; i++) {
            int ix = i;
            harn.put("/", "Test " + i + " iter", UTF_8)
                    .setHeader(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                    .setHeader("X-Iteration", "" + i)
                    .applyingAssertions(a -> a.assertOk()
                    .assertBody("Test " + ix + " iter")).assertAllSucceeded();

//            harn.put("/")
//                    .setTimeout(Duration.ofSeconds(20))
//                    .addHeader(Headers.header("X-Iteration"), "" + i)
//                    .onEvent(new Receiver<com.mastfrog.netty.http.client.State<?>>() {
//                        @Override
//                        public void receive(com.mastfrog.netty.http.client.State<?> state) {
//                            if (state.get() instanceof ByteBuf) {
//                                ByteBuf buf = (ByteBuf) state.get();
//                                buf.resetReaderIndex();
//                            }
//                        }
//                    })
//                    .setBody("Test " + i + " iter", MimeType.PLAIN_TEXT_UTF_8).go()
//                    .await()
//                    .assertStatus(OK)
//                    //                    .assertStateSeen(com.mastfrog.netty.http.client.StateType.FullContentReceived)
//                    .assertContent("Test " + i + " iter");
        }
//        harn.get(veryLongUrl(35)).setTimeout(Duration.ofMinutes(3)).go().await().assertStatus(OK);
        harn.get(veryLongUrl(35)).applyingAssertions(a -> a.assertOk()).assertAllSucceeded();
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
                out.write(buf.retainedDuplicate());
                ran = true;
            } else {
                throw new AssertionError("Not a FullHttpRequest: " + evt.request() + " " + evt.request().getClass().getName());
            }
            return ResponseWriter.Status.NOT_DONE;
        }
    }
}
