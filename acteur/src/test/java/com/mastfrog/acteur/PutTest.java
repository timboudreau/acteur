package com.mastfrog.acteur;

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.PutTest.SM;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.Types;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, SM.class})
@RunWith(GuiceRunner.class)
public class PutTest {
    
    static class SM extends ServerModule {
        SM() {
            super (EchoServer.class, 2, 2, 3);
        }
    }

    @Test(timeout = 20000L)
    public void testPuts(TestHarness harn) throws Throwable {
        
        harn.get("foo/bar/baz").go().assertStatus(OK).assertContent("Hello world");
        
        harn.get("/").go().assertStatus(OK).assertContent("Hello world");
        
        
        List<Integer> s = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            harn.put("/").addHeader(Headers.stringHeader("X-Iteration"), "" + i)
                    .setBody("Poodle hoover " + i + " iter", MediaType.PLAIN_TEXT_UTF_8).go()
                    .assertStatus(OK).assertContent("Poodle hoover " + i + " iter");
            
        }
        harn.get(veryLongUrl(3500)).go().assertStatus(OK);
    }

    private String veryLongUrl(int amt) {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < amt; i++) {
            sb.append("/0123456789");
        }
        return sb.toString();
    }

    static class EchoServer extends Application {

        EchoServer() {
            add(EchoPage.class);
        }

        private static final class EchoPage extends Page {

            @Inject
            EchoPage(ActeurFactory af) {
                getResponseHeaders().addCacheControl(CacheControlTypes.Public);
                add(af.matchMethods(Method.PUT, Method.GET));
                add(EchoActeur.class);
            }
        }
    }

    static class EchoActeur extends Acteur {

        @Inject
        EchoActeur(HttpEvent evt) {
            setChunked(true);
            if (evt.getMethod() == Method.GET) {
                setState(new RespondWith(HttpResponseStatus.OK, "Hello world"));
            } else {
                setState(new RespondWith(HttpResponseStatus.OK));
                setResponseBodyWriter(EchoWriter.class);
//                setResponseWriter(RWriter.class);
            }
        }
    }
    
    private static class RWriter extends ResponseWriter {
        @Override
        public ResponseWriter.Status write(Event<?> evt, Output out) throws Exception {
            FullHttpRequest req = evt.getRequest() instanceof FullHttpRequest
                    ? (FullHttpRequest) evt.getRequest() : null;
            if (req != null) {
                ByteBuf buf = req.content();
                out.write(buf);
            } else {
                throw new AssertionError("Not a FullHttpRequest: " + evt.getRequest() + " " + Types.list(evt.getRequest().getClass()));
            }
            return ResponseWriter.Status.DONE;
        }
    }

    private static class EchoWriter implements ChannelFutureListener {

        private final HttpEvent evt;

        @Inject
        EchoWriter(HttpEvent evt) {
            this.evt = evt;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
//            Channel ch = evt.getChannel();
            Channel ch = future.channel();
            FullHttpRequest req = evt.getRequest() instanceof FullHttpRequest
                    ? (FullHttpRequest) evt.getRequest() : null;
            if (req != null) {
                ByteBuf buf = req.content();
                future = ch.writeAndFlush(buf);
                future.addListener(CLOSE);
            } else {
                throw new AssertionError("Not a FullHttpRequest: " + evt.getRequest() + " " + Types.list(evt.getRequest().getClass()));
            }
        }
    }
}
