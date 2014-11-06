package com.mastfrog.acteur;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Names;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.acteur.PutTest.M2;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.util.Streams;
import com.mastfrog.util.Strings;
import com.mastfrog.util.Types;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
@TestWith(M2.class)
public class PutTest {

    static class M2 extends AbstractModule {

        @Override
        protected void configure() {
            bind(Charset.class).toInstance(CharsetUtil.UTF_8);
            bind(Application.class).to(AppTest.App.class);
            ReentrantScope scope = new ReentrantScope();
            bind(ReentrantScope.class).toInstance(scope);
            ExecutorService exe = Executors.newSingleThreadExecutor();
            bind(ExecutorService.class).annotatedWith(Names.named(ServerModule.BACKGROUND_THREAD_POOL_NAME)).toInstance(exe);
            bind(RequestID.class).toInstance(new RequestID());

            scope.bindTypes(binder(), Event.class, 
                    Page.class, BasicCredentials.class, AppTest.Thing.class);
        }
    }    
    
    @Test(timeout = 20000L)
    public void testPuts() throws IOException, InterruptedException {
        System.err.println("enter test");
        ServerModule m = new ServerModule(EchoServer.class, 2, 2, 2);
        System.out.println("start server");
        m.start(8193);
        System.out.println("server started");
        
        DefaultHttpClient client = new DefaultHttpClient();
        System.out.println("send first request");
        HttpGet get = new HttpGet("http://localhost:8193/foo/bar/baz");
        HttpResponse res = client.execute(get);
        System.out.println("first request done");
        for (Header h : res.getAllHeaders()) {
            System.out.println(h.getName() + ": " + h.getValue());
        }
        assertNotNull(res);
        assertEquals(200, res.getStatusLine().getStatusCode());
        String body = Streams.readString(res.getEntity().getContent());
        assertEquals("Hello world", body);
        get = new HttpGet("http://localhost:8193");
        res = client.execute(get);
        assertNotNull(res);
        assertEquals(200, res.getStatusLine().getStatusCode());
        body = Streams.readString(res.getEntity().getContent());
        System.out.println("GOT BODY '" + body + "'");
        assertEquals("Hello world", body);
        List<Integer> s = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            HttpPut put = new HttpPut("http://localhost:8193");
            put.addHeader("X-Iteration", "" + i);
            System.out.println("ITER " + i);
            try {
                String ent = "Poodle hoover " + i + " iter";
                put.setEntity(new StringEntity(ent));
                res = client.execute(put);
                body = Streams.readString(res.getEntity().getContent());
                for(Header h : res.getAllHeaders()) {
                    System.out.println(h.getName() + ": " + h.getValue());
                }
                assertEquals("Failed on " + i + " content length " 
                        + res.getEntity().getContentLength(), ent, body);
            } catch (NoHttpResponseException e) {
                s.add(i);
                System.out.println("No response on iteration " + i + "\n" + e);
                break;
            }
        }
        if (!s.isEmpty()) {
            fail("No response on iterations " + Strings.toString(s));
        }
        try {
            get = new HttpGet(veryLongUrl(3500));
            res = client.execute(get);
            assertEquals(200, res.getStatusLine().getStatusCode());
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private String veryLongUrl(int amt) {
        StringBuilder sb = new StringBuilder("http://localhost:8193");
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
            System.out.println("EVENT " + evt.getMethod() + " " + evt.getPath());
            setChunked(true);
            if (evt.getMethod() == Method.GET) {
                System.out.println("Send hello world");
                setState(new RespondWith(HttpResponseStatus.OK, "Hello world"));
            } else {
                setState(new RespondWith(HttpResponseStatus.OK));
//                setResponseBodyWriter(EchoWriter.class);
                setResponseWriter(RWriter.class);
            }
        }
    }
    
    private static class RWriter extends ResponseWriter {
        @Override
        public ResponseWriter.Status write(Event<?> evt, Output out) throws Exception {
            FullHttpRequest req = evt.getRequest() instanceof FullHttpRequest
                    ? (FullHttpRequest) evt.getRequest() : null;
            if (req != null) {
                System.out.println("READ CONTENT");
                ByteBuf buf = req.content();
                System.out.println("SIZE " + buf.capacity() + " rb " + buf.readableBytes());
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
                System.out.println("READ CONTENT");
                ByteBuf buf = req.content();
                System.out.println("SIZE " + buf.capacity());
                future = ch.write(buf);
                future.addListener(CLOSE);
            } else {
                throw new AssertionError("Not a FullHttpRequest: " + evt.getRequest() + " " + Types.list(evt.getRequest().getClass()));
            }
        }
    }
}
