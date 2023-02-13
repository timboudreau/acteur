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
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({TestHarnessModule.class, DecoratorWithOnBeforeEventTest.M.class, SilentRequestLogger.class})
public class DecoratorWithOnBeforeEventTest {

    private static final HeaderValueType<CharSequence> THING_HEADER = header("x-thing");

    @Test(timeout = 1200000)
    public void test(TestHarness harn, DWOBEApp app) throws Throwable {
        int nextThing = Thing.COUNTER.get() + 1;

        TestHarness.CallResult cr = harn.get("/deco")
                .setTimeout(Duration.ofMinutes(2))
                .go()
                .await().assertCode(200)
                .assertHasHeader(THING_HEADER)
                .assertHeader(THING_HEADER, "Thing-" + nextThing)
                .assertContent("Thing-" + nextThing);

        cr = harn.get("/nothing/abcde").go().await().assertCode(404)
                .assertHasHeader(THING_HEADER)
                .assertHeader(THING_HEADER, "Thing-" + (nextThing + 1));

        assertEquals(2, app.ec);
        assertEquals(1, app.nf);
    }

    @Singleton
    static class DWOBEApp extends Application {

        int ec;
        int nf;

        DWOBEApp() {
            add(DecoPage.class);
        }

        @Override
        protected void onBeforeRespond(RequestID id, Event<?> event, HttpResponseStatus status) {
            ec++;
        }

        @Override
        protected void send404(RequestID id, Event<?> event, Channel channel) {
            nf++;
            super.send404(id, event, channel);
        }

        @Override
        protected HttpResponse createNotFoundResponse(Event<?> event) {
            return super.createNotFoundResponse(event);
        }

    }

    @Methods(GET)
    @PathRegex("^deco$")
    static class DecoPage extends Page {

        @Inject
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
