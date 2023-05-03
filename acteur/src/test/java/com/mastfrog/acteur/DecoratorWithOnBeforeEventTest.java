/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.acteur.headers.HeaderValueType;
import static com.mastfrog.acteur.headers.Headers.header;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import static java.util.concurrent.TimeUnit.MINUTES;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({HttpTestHarnessModule.class, DecoratorWithOnBeforeEventTest.M.class, SilentRequestLogger.class})
public class DecoratorWithOnBeforeEventTest {

    private static final HeaderValueType<CharSequence> THING_HEADER = header("x-thing");

    @Test
    @Timeout(value = 2, unit = MINUTES)
    public void test(HttpHarness harn, DWOBEApp app) throws Throwable {
        assertNotNull(app, "App is null");
        int nextThing = Thing.COUNTER.get() + 1;

        harn.get("/deco")
                .applyingAssertions(
                        a -> a.assertOk()
                                .assertHasHeader(THING_HEADER)
                                .assertHeaderEquals("x-thing", "Thing-" + nextThing)
                                .assertBody("Thing-" + nextThing)
                ).assertAllSucceeded();

        harn.get("/nothing/abcde").applyingAssertions(a -> a.assertNotFound()
                .assertHasHeader(THING_HEADER).assertHeaderEquals("x-thing", "Thing-" + (nextThing + 1)))
                .assertAllSucceeded();

        synchronized (app) {
            assertEquals(2, app.ec);
            assertEquals(1, app.nf);
        }
    }

    @Singleton
    static class DWOBEApp extends Application {

        int ec;
        int nf;

        DWOBEApp() {

            add(NothingPageOne.class);
            add(NothingPageTwo.class);
            add(DecoPage.class);
        }

        @Override
        protected synchronized void onBeforeRespond(RequestID id, Event<?> event, HttpResponseStatus status) {
            ec++;
        }

        @Override
        protected void send404(RequestID id, Event<?> event, Channel channel) {
            synchronized (this) {
                nf++;
            }
            super.send404(id, event, channel);
        }
    }

    @Methods(GET)
    @PathRegex("^deco$")
    static class DecoPage extends Page {

        @Inject
        @SuppressWarnings("deprecation")
        DecoPage(ActeurFactory f) {
            f.matchMethods(GET);
            f.matchPath("^deco$");
            add(DecoActeur.class);
        }
    }

    @Methods(GET)
    @PathRegex("^deco$")
    static class DecoActeur extends Acteur {

        @Inject
        DecoActeur(Thing thing, HttpEvent e) {
            System.out.println("DECO FOR " + e.path());
            ok(thing.toString());
        }
    }

    static class OnBefore implements OnBeforeEvent {

        @Override
        public Object[] onBeforeEvent(Event<?> event, Channel channel, Object internalId) {
            return new Object[]{internalId, new Thing()};
        }
    }

    static class RD implements ResponseDecorator {

        private final Provider<Thing> p;

        @Inject
        RD(Provider<Thing> p) {
            this.p = p;
        }

        @Override
        public void onBeforeSendResponse(Application application, HttpResponseStatus status,
                Event<?> event, Response response, Acteur acteur, Page page) {
            Thing t = p.get();
            response.add(header("x-thing"), t.toString());
        }
    }

    @Methods(GET)
    @PathRegex("^aot1$")
    static class NothingPageOne extends Page {

        @Inject
        @SuppressWarnings("deprecation")
        NothingPageOne(ActeurFactory f) {
            f.matchMethods(GET);
            f.matchPath("^aot1$");
            add(DecoActeur.class);
        }

        static class NothingActeur extends Acteur {

            NothingActeur() {
                ok("nothing-one");
            }
        }
    }

    @Methods(GET)
    @PathRegex("^aot1$")
    static class NothingPageTwo extends Page {

        @Inject
        @SuppressWarnings("deprecation")
        NothingPageTwo(ActeurFactory f) {
            f.matchMethods(GET);
            f.matchPath("^aot2$");
            add(DecoActeur.class);
        }

        static class NothingActeurTwo extends Acteur {

            NothingActeurTwo() {
                ok("nothing-two");
            }
        }
    }

    static class M extends ServerModule<DWOBEApp> {

        public M() {
            super(DWOBEApp.class);
        }

        @Override
        protected void configure() {
            super.configure();
            bind(ResponseDecorator.class).to(RD.class);
            bind(OnBeforeEvent.class).to(OnBefore.class);
            scope.bindTypes(binder(), Thing.class);
        }
    }

    static class Thing {

        static AtomicInteger COUNTER = new AtomicInteger(0);
        final int value = COUNTER.incrementAndGet();

        @Override
        public String toString() {
            return "Thing-" + value;
        }
    }
}
