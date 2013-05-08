package com.mastfrog.acteur;

import com.mastfrog.acteur.util.Headers;
import io.netty.handler.codec.http.HttpResponseStatus;
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.AbstractScope;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.acteur.Acteur.RespondWith;
import com.mastfrog.acteur.TestHeaderWriting.EM;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.acteur.auth.file.Role;
import com.mastfrog.acteur.auth.file.User;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Server;
import java.io.IOException;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.MutableSettings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import io.netty.channel.ChannelFutureListener;
import java.util.concurrent.Callable;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Tim Boudreau
 */
@TestWith(EM.class)
@RunWith(GuiceRunner.class)
public class TestHeaderWriting {

    @TestWith(MD.class)
    public void testChain(final Dependencies deps) throws Exception {
        final AbstractScope scope = deps.getInstance(ReentrantScope.class);
        final MD evt = deps.getInstance(MD.class);
        final E e = deps.getInstance(E.class);
        final Application app = deps.getInstance(Application.class);
        scope.run(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Event evt = scope.provider(Event.class, Providers.<Event>of(null)).get();
                assertNotNull(evt);
                e.setApplication(app);
                Page.set(e);
                Iterable<Acteur> all = CollectionUtils.toIterable(CollectionUtils.convertedIterator(new Converter<Acteur, Object>() {
                    @Override
                    public Acteur convert(Object r) {
                        if (r instanceof Acteur) {
                            return (Acteur) r;
                        }
                        if (r instanceof Class<?>) {
                            return (Acteur) deps.getInstance((Class<?>) r);
                        }
                        throw new AssertionError(r);
                    }

                    @Override
                    public Object unconvert(Acteur t) {
                        throw new AssertionError();
                    }
                }, e.getActeurs().iterator()));
                int ct = 0;
                for (Acteur a : all) {
                    State s = a.getState();
                    System.out.println("STATE at " + ct++ + " " + s + " for " + a);
                    if (s instanceof RespondWith) {
                        assertEquals(200, a.getResponse().getResponseCode().code());
                    }
                }
                assertEquals (3, ct);
                return null;
            }
        }, evt, e);
    }

    @Test
    public void test(Server server) throws InterruptedException, IOException {
        if (true) {
            return;
        }
        Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
//                System.exit(1);
            }
        });

        server.start();
        System.out.println("started");
        try {
            DefaultHttpClient cl = new DefaultHttpClient();
            HttpGet get;
            HttpResponse resp;
            
            get = new HttpGet("http://localhost:8283");
            resp = cl.execute(get);
            System.out.println("execute get");
            EntityUtils.consume(resp.getEntity());
            if (true) return;

            System.out.println("huh");
//            get = new HttpGet("http://localhost:8283/testMod/");
//            get.setHeader("Connection", "close");
//            resp = cl.execute(get);
//
//            assertEquals(resp.getStatusLine() + "", 200, resp.getStatusLine().getStatusCode());
//            EntityUtils.consume(resp.getEntity());
            
            get = new HttpGet("http://localhost:8283/testMod/");
            get.setHeader("Connection", "close");
            get.setHeader(Headers.IF_MODIFIED_SINCE.name(), Headers.IF_MODIFIED_SINCE.toString(START));
            resp = cl.execute(get);
            EntityUtils.consume(resp.getEntity());

            assertEquals(resp.getStatusLine() + "", 304, resp.getStatusLine().getStatusCode());


            get = new HttpGet("http://localhost:8283/testTag");
            resp = cl.execute(get);
            try {
                EntityUtils.consume(resp.getEntity());
            } catch (Exception e) {
                e.printStackTrace();
            }
            assertEquals(resp.getStatusLine() + "", 200, resp.getStatusLine().getStatusCode());

            get = new HttpGet("http://localhost:8283/testTag");
            get.setHeader(Headers.IF_NONE_MATCH.name(), Headers.ETAG.toString(ETAG));
            resp = cl.execute(get);
            EntityUtils.consume(resp.getEntity());
            assertEquals(resp.getStatusLine() + "", 304, resp.getStatusLine().getStatusCode());

        } finally {
            server.shutdown(true);
        }
    }

    @ImplicitBindings({User.class, Role.class, Realm.class})
    static class A extends Application {

        A() {
            add(P.class);
            add(E.class);
            add(FallThrough.class);
        }
    }
    private static DateTime START = new DateTime();

    static {
        START = new DateTime(START.getYear(), START.getMonthOfYear(), START.getDayOfMonth(), START.getHourOfDay(), START.getMinuteOfHour(), START.getSecondOfMinute(), 0);
    }

    static class FallThrough extends Page {

        FallThrough() {
            add(FT.class);
        }

        static class FT extends Acteur {

            FT() {
                setState(new RespondWith(HttpResponseStatus.FORBIDDEN));
                setMessage("Fall through");
            }
        }
    }

    static class P extends Page {

        @Inject
        P(ActeurFactory f) {
            responseHeaders.setLastModified(START);
            add(f.matchPath("^testMod$"));
            add(f.sendNotModifiedIfIfModifiedSinceHeaderMatches());
            add(PA.class);
        }

        static class PA extends Acteur {

            @Inject
            PA() {
                setResponseCode(HttpResponseStatus.OK);
                setMessage("Hello World");
                setState(new RejectedState());
            }
        }
    }
    private static final String ETAG = "abcde";

    static class E extends Page {

        @Inject
        E(ActeurFactory f, Dependencies d) {
            System.out.println("Created an ");
            responseHeaders.setEtag(ETAG);
            add(wrap(f.matchPath("^testTag$")));
            add(wrap(f.sendNotModifiedIfETagHeaderMatches()));
            add(E.PA.class);
        }

        static class PA extends Acteur {

            @Inject
            PA() {
                System.out.println("Created a PA");
                setState(new RespondWith(HttpResponseStatus.OK, "Hello World"));
                setResponseBodyWriter(ChannelFutureListener.CLOSE);
            }

            @Override
            public State getState() {
                State result = super.getState();
                System.out.println("Really return " + result);
                return result;
            }
        }
    }

    private static <T extends Acteur> Acteur wrap(final Class<T> type, final Dependencies deps) {
        return wrap(new Acteur() {
            Acteur del;

            private Acteur del() {
                if (del == null) {
                    System.out.println("Create an instance of " + type);
                    del = deps.getInstance(type);
                }
                return del;
            }

            @Override
            ResponseImpl getResponse() {
                return del().getResponse();
            }

            @Override
            public State getState() {
                return del().getState();
            }

            @Override
            protected void setState(State state) {
                del().setState(state);
            }

            @Override
            public String toString() {
                return del() + " (wrapped)";
            }
        });
    }

    private static Acteur wrap(Acteur a) {
        return new Wrapper(a);
    }

    private static class Wrapper extends Acteur {

        private final Acteur wrapped;

        public Wrapper(Acteur wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        ResponseImpl getResponse() {
            return wrapped.getResponse();
        }

        @Override
        public State getState() {
            State res = wrapped.getState();
            System.out.println("State " + res + " for " + wrapped);
            return res;
        }
    }

    static class EM extends ServerModule<A> {

        EM(Settings s) {
            super(A.class);
            assertEquals("8283", s.getString("port"));
        }

        @Override
        protected void onInit(Settings settings) {
            ((MutableSettings) settings).setString("neverKeepAlive", "true");
        }
    }
}
