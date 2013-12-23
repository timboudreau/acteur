package com.mastfrog.acteur;

import com.mastfrog.acteur.util.Headers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.acteur.AppTest.M;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.server.EventImpl;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.util.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(GuiceRunner.class)
@TestWith(M.class)
public class AppTest {

    static ReentrantScope scope = new ReentrantScope();

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(Charset.class).toInstance(CharsetUtil.UTF_8);
            bind(Application.class).to(App.class);
            ReentrantScope scope = new ReentrantScope();
            bind(ReentrantScope.class).toInstance(scope);
            ExecutorService exe = Executors.newSingleThreadExecutor();
            bind(ExecutorService.class).annotatedWith(Names.named(ServerModule.BACKGROUND_THREAD_POOL_NAME)).toInstance(exe);
            bind(RequestID.class).toInstance(new RequestID());
            
            //Generic madness - Event != Event<?>
            final Provider<Event> e = binder().getProvider(Event.class);
            bind(new TypeLiteral<Event<?>>(){}).toProvider(new Provider<Event<?>>() {

                @Override
                public Event<?> get() {
                    return e.get();
                }
                
            });
            

            scope.bindTypes(binder(), Event.class, HttpEvent.class,
                    Page.class, BasicCredentials.class, Thing.class);
            bind(ThreadFactory.class).annotatedWith(Names.named(ServerModule.WORKER_THREADS)).toInstance(new ThreadFactory() {

                @Override
                public Thread newThread(Runnable r) {
                    throw new UnsupportedOperationException("Not supported yet.");
                }
            });            
        }
    }

    @Test
    public void testApp(Application app, PathFactory paths, ReentrantScope scope) throws IOException, InterruptedException, Exception {
        assertTrue(app instanceof App);
        assertTrue("App has no pages", app.iterator().hasNext());
        Page page = app.iterator().next();
        assertNotNull(page);
        try (AutoCloseable cl = app.getRequestScope().enter(page)) {
            page.setApplication(app);
            ActeursImpl ai = (ActeursImpl) page.getActeurs(Executors.newSingleThreadExecutor(), scope);

            EventImpl event = createEvent(paths);

            R r = new R();

            ai.onEvent(event, r);

            r.await();
        }
    }

    static class App extends Application {

        App() {
            add(P.class);
        }
    }

    static class P extends Page {

        @Inject
        P(ActeurFactory f) {
            add(f.matchMethods(Method.GET));
            add(f.matchPath("^root/captcha/image.*"));
            add(f.requireParameters("select", "value", "realname", "name", "password"));
            add(AuthenticationAction.class);
            add(ConvertHeadersAction.class);
            add(ConvertBodyAction.class);
            add(WriteResponseAction.class);
        }
    }

    static class AuthenticationAction extends Acteur {

        @Inject
        AuthenticationAction(HttpEvent event) {
            BasicCredentials credentials = event.getHeader(Headers.AUTHORIZATION);
            if (credentials != null) {
                setState(new ConsumedLockedState(credentials));
                System.err.println("CREDENTIALS " + credentials.username + " pw=" + credentials.password);
            } else {
                System.err.println("NO CREDENTIALS");
                setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED));
            }
        }
    }

    static class ConvertHeadersAction extends Acteur {

        @Inject
        ConvertHeadersAction(HttpEvent event) {
            ReqParams p = event.getParametersAs(ReqParams.class);
            if (p == null) {
                System.err.println("PARAM name is " + p.name() + " realname " + p.realname());
                setState(new RejectedState());
            } else {
                System.err.println("PARAMS " + p.getClass().getName());
                setState(new ConsumedLockedState(p));
            }
        }
    }

    static class ConvertBodyAction extends Acteur {

        @Inject
        ConvertBodyAction(Event<?> event) throws IOException {
            System.err.println("Convert body ");
            Thing thing = event.getContentAsJSON(Thing.class);
            if (thing == null) {
                setState(new RejectedState());
            } else {
                setState(new ConsumedLockedState(thing));
            }
        }
    }
    static boolean done;

    static class WriteResponseAction extends Acteur {

        @Inject
        WriteResponseAction(BasicCredentials creds, Thing thing) {
            Checks.notNull("creds", creds);
            Checks.notNull("thing", thing);
            System.err.println("Got to write response action");
            done = true;
            add(Headers.DATE, new DateTime());
            add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
            add(Headers.ETAG, "1234");
            add(Headers.LAST_MODIFIED, new DateTime().minus(Duration.standardHours(1)));
            add(Headers.EXPIRES, new DateTime().plus(Duration.standardHours(1)));
            add(Headers.CACHE_CONTROL, new CacheControl().add(CacheControlTypes.no_store, CacheControlTypes.no_cache));

            setResponseCode(HttpResponseStatus.FORBIDDEN);
            setMessage("Hello " + creds.username + "\n" + thing.foo);
            setState(new ConsumedState());
        }
    }

    private EventImpl createEvent(PathFactory paths) throws IOException {
        ByteBuf buf = Unpooled.directBuffer(256);
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/root/captcha/image/test.png?select=1&value=3&realname=Joe%20Blow&name=joey&password=abcdefg", buf);

        Headers.write(Headers.DATE, new DateTime(), req);
        Headers.write(Headers.LAST_MODIFIED, new DateTime().minus(Duration.standardHours(1)), req);

        Headers.write(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8, req);
        Headers.write(Headers.AUTHORIZATION, new BasicCredentials("joey", "abcdefg"), req);

        CacheControl cc = new CacheControl().add(CacheControlTypes.Public, CacheControlTypes.must_revalidate).add(CacheControlTypes.max_age, Duration.standardSeconds(25));
        Headers.write(Headers.CACHE_CONTROL, cc, req);

        CacheControl cc1 = Headers.read(Headers.CACHE_CONTROL, req);
        assertEquals(cc, cc1);

        System.err.println("Cache-Control: " + req.headers().get(Headers.CACHE_CONTROL.name()));

        System.err.println("AUTH HEADER IS " + req.headers().get(HttpHeaders.Names.AUTHORIZATION));
        assertNotNull(req.headers().get(HttpHeaders.Names.AUTHORIZATION));
        BasicCredentials c = Headers.read(Headers.AUTHORIZATION, req);
        assertNotNull(c);
        assertEquals("joey", c.username);
        assertEquals("abcdefg", c.password);

        ObjectMapper m = new ObjectMapper();
        m.writeValue(new ByteBufOutputStream(buf), new Thing());
        return new EventImpl(req, paths);
    }

    static interface ReqParams {

        String realname();

        int value();

        int select();

        String name();

        String hashcode();
    }

    static class Thing {

        public String foo = "Foo foo fru";
        public long bar = System.currentTimeMillis();
    }

    static class R implements ResponseSender {

        State received;
        private int count = 0;
        private int max = 5;

        void await() throws InterruptedException {
            synchronized (this) {
                while (received == null) {
                    count++;
                    wait(1000);
                    if (count == max) {
                        if (this.received == null) {
                            fail("Never called");
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public void receive(Acteur action, State state, ResponseImpl response) {
            assertNotNull(state);
            System.err.println("Received " + state);
            synchronized (this) {
                this.received = state;
                notifyAll();
            }
            System.err.println(response);
        }

        @Override
        public void uncaughtException(Thread thread, Throwable thrwbl) {
            thrwbl.printStackTrace();
            fail(thrwbl.getMessage());
        }
    }
}
