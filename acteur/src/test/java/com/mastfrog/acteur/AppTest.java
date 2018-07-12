package com.mastfrog.acteur;

import com.mastfrog.acteur.headers.Headers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.acteur.AppTest.M;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.EventImplFactory;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.DELAY_EXECUTOR;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.giulius.InjectionInfo;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(GuiceRunner.class)
@TestWith({M.class, SilentRequestLogger.class})
public class AppTest {

    static ReentrantScope scope = new ReentrantScope(new InjectionInfo());

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(Charset.class).toInstance(CharsetUtil.UTF_8);
            bind(Application.class).to(App.class);
            ReentrantScope scope = new ReentrantScope(new InjectionInfo());
            bind(ReentrantScope.class).toInstance(scope);
            ExecutorService exe = Executors.newSingleThreadExecutor();
            bind(ExecutorService.class).annotatedWith(Names.named(ServerModule.BACKGROUND_THREAD_POOL_NAME)).toInstance(exe);
            bind(RequestID.class).toInstance(new RequestID.Factory().next());
            bind(ScheduledExecutorService.class).annotatedWith(Names.named(DELAY_EXECUTOR)).toInstance(Executors.newScheduledThreadPool(2));
            bind(Codec.class).toInstance(new Codec() {
                final ObjectMapper mapper = new ObjectMapper();

                @Override
                public <T> String writeValueAsString(T object) throws IOException {
                    return mapper.writeValueAsString(object);
                }

                @Override
                public <T> void writeValue(T object, OutputStream out) throws IOException {
                    mapper.writeValue(out, object);
                }

                @Override
                public <T> byte[] writeValueAsBytes(T object) throws IOException {
                    return mapper.writeValueAsBytes(object);
                }

                @Override
                public <T> T readValue(InputStream byteBufInputStream, Class<T> type) throws IOException {
                    return mapper.readValue(byteBufInputStream, type);
                }
            });
            //Generic madness - Event != Event<?>
            final Provider<Event> e = binder().getProvider(Event.class);
            bind(new TypeLiteral<Event<?>>() {
            }).toProvider(new Provider<Event<?>>() {

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
    public void testApp(Application app, PathFactory paths, ReentrantScope scope, Settings settings) throws IOException, InterruptedException, Exception {
        assertTrue(app instanceof App);
        assertTrue("App has no pages", app.iterator().hasNext());
        Page page = app.iterator().next();
        assertNotNull(page);
    }

    static class App extends Application {

        App() {
            add(P.class);
        }
    }

    static class P extends Page {

        @Inject
        @SuppressWarnings("deprecation")
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
            BasicCredentials credentials = event.header(Headers.AUTHORIZATION);
            if (credentials != null) {
                next(credentials);
            } else {
                reply(HttpResponseStatus.UNAUTHORIZED);
            }
        }
    }

    static class ConvertHeadersAction extends Acteur {

        @Inject
        ConvertHeadersAction(HttpEvent event) {
            ReqParams p = event.urlParametersAs(ReqParams.class);
            if (p == null) {
                setState(new RejectedState());
            } else {
                next(p);
            }
        }
    }

    static class ConvertBodyAction extends Acteur {

        @Inject
        ConvertBodyAction(HttpEvent event, ContentConverter cvt) throws Exception {
            Thing thing = cvt.readObject(event.content(), event.header(Headers.CONTENT_TYPE), Thing.class);
            if (thing == null) {
                setState(new RejectedState());
            } else {
                next(thing);
            }
        }
    }
    static boolean done;

    static final class WriteResponseAction extends Acteur {

        @Inject
        WriteResponseAction(BasicCredentials creds, Thing thing) {
            Checks.notNull("creds", creds);
            Checks.notNull("thing", thing);
            done = true;
            add(Headers.DATE, ZonedDateTime.now());
            add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
            add(Headers.ETAG, "1234");
            add(Headers.LAST_MODIFIED, ZonedDateTime.now().minus(Duration.ofHours(1)));
            add(Headers.EXPIRES, ZonedDateTime.now().plus(Duration.ofHours(1)));
            add(Headers.CACHE_CONTROL, new CacheControl().add(CacheControlTypes.no_store, CacheControlTypes.no_cache));

            setResponseCode(HttpResponseStatus.FORBIDDEN);
            setMessage("Hello " + creds.username + "\n" + thing.foo);
            next();
        }
    }

    private Event createEvent(PathFactory paths) throws IOException {
        ByteBuf buf = Unpooled.directBuffer(256);
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/root/captcha/image/test.png?select=1&value=3&realname=Joe%20Blow&name=joey&password=abcdefg", buf);

        Headers.write(Headers.DATE, ZonedDateTime.now(), req);
        Headers.write(Headers.LAST_MODIFIED, ZonedDateTime.now().minus(Duration.ofHours(1)), req);

        Headers.write(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8, req);
        Headers.write(Headers.AUTHORIZATION, new BasicCredentials("joey", "abcdefg"), req);

        CacheControl cc = new CacheControl().add(CacheControlTypes.Public, CacheControlTypes.must_revalidate).add(CacheControlTypes.max_age, Duration.ofSeconds(25));
        Headers.write(Headers.CACHE_CONTROL, cc, req);

        CacheControl cc1 = Headers.read(Headers.CACHE_CONTROL, req);
        assertEquals(cc, cc1);

        assertNotNull(req.headers().get(HttpHeaderNames.AUTHORIZATION));
        BasicCredentials c = Headers.read(Headers.AUTHORIZATION, req);
        assertNotNull(c);
        assertEquals("joey", c.username);
        assertEquals("abcdefg", c.password);

        ObjectMapper m = new ObjectMapper();
        m.writeValue((OutputStream) new ByteBufOutputStream(buf), new Thing());
        return EventImplFactory.newEvent(req, paths);
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
        public void receive(Acteur action, com.mastfrog.acteur.State state, ResponseImpl response) {
            assertNotNull(state);
            synchronized (this) {
                this.received = state;
                notifyAll();
            }
        }

        @Override
        public void uncaughtException(Thread thread, Throwable thrwbl) {
            thrwbl.printStackTrace();
            fail(thrwbl.getMessage());
        }
    }
}
