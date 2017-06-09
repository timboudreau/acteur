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
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.Types;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, SM.class}) // Use these Guice modules
@RunWith(GuiceRunner.class) // Use the Guice-Tests JUnit runner
public class PutTest {

    // Just subclasses ServerModule to provide the application class
    static class SM extends ServerModule<EchoServer> {
        SM() {
            super(EchoServer.class, 2, 2, 3);
        }
    }

    @Test(timeout = 5000L)
    public void testPuts(TestHarness harn) throws Throwable {
        harn.get("foo/bar/baz").go().assertStatus(OK).assertContent("Hello world");
        harn.get("/").go().assertStatus(OK).assertContent("Hello world");
        for (int i = 0; i < 20; i++) {
            harn.put("/").addHeader(Headers.header("X-Iteration"), "" + i)
                    .setBody("Test " + i + " iter", MediaType.PLAIN_TEXT_UTF_8).go()
                    .assertStatus(OK).assertContent("Test " + i + " iter");
        }
        harn.get(veryLongUrl(3500)).go().assertStatus(OK);
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

    static class EchoActeur extends Acteur {
        @Inject
        EchoActeur(HttpEvent evt) {
            add(Headers.CACHE_CONTROL, CacheControl.$(CacheControlTypes.Public));
            if (evt.method() == Method.GET) {
                setState(new RespondWith(HttpResponseStatus.OK, "Hello world"));
            } else {
                setState(new RespondWith(HttpResponseStatus.OK));
                setResponseWriter(RWriter.class);
            }
        }
    }

    private static class RWriter extends ResponseWriter {
        @Override
        public ResponseWriter.Status write(Event<?> evt, Output out) throws Exception {
            FullHttpRequest req = evt.request() instanceof FullHttpRequest
                    ? (FullHttpRequest) evt.request() : null;
            if (req != null) {
                ByteBuf buf = req.content();
                out.write(buf);
            } else {
                throw new AssertionError("Not a FullHttpRequest: " + evt.request() + " " + Types.list(evt.request().getClass()));
            }
            return ResponseWriter.Status.DONE;
        }
    }
}
