/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluator;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import com.mastfrog.acteur.errors.ResponseException;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.collections.StringObjectMap;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.EXPECTATION_FAILED;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.PAYMENT_REQUIRED;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({TestHarnessModule.class, AsyncActeursTest.Module.class, SilentRequestLogger.class})
public class AsyncActeursTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final long DUR = 2 * 1000 * 60;

    @Inject
    TestHarness harn;

    private CallResult get(String pth) throws Throwable {
        return harn.get(pth).setTimeout(TIMEOUT).go().await();
    }

    @Test(timeout = DUR)
    public void testAsynchronous(TestHarness harn) throws Throwable {
        Thing t = get("/p1").assertStatus(OK).content(Thing.class);
        assertNotNull(t);
        assertEquals(Thing.last("p1a1"), t);

        get("/p2").assertStatus(EXPECTATION_FAILED).content(ErrorMessage.class)
                .assertMessage("p2a1");

        get("/p3").assertStatus(INTERNAL_SERVER_ERROR).content(ErrorMessage.class).assertMessage("p3a1");

        Map<String, Object> m = get("/p4").assertStatus(CREATED).content(StringObjectMap.class);

        assertEquals("foo", m.get("txt"));
        assertEquals(InetAddress.getLoopbackAddress().getHostName(), m.get("addr"));
        assertTrue(m.get("thing") instanceof Map<?, ?>);
        Map<?, ?> m1 = (Map<?, ?>) m.get("thing");
        assertEquals("wubba", m1.get("name"));

        get("/p5").assertStatus(INTERNAL_SERVER_ERROR).content(ErrorMessage.class).assertMessage("uh oh");

        get("/p6").assertStatus(INTERNAL_SERVER_ERROR).content(ErrorMessage.class).assertMessage("uh oh");

        // Test that we run deferred after the timer expires
        String msg = get("/p7").assertStatus(OK).content();
        assertNotNull("Response body was empty", msg);
        assertTrue(msg, Pattern.compile("^\\d+$").matcher(msg).find());
        assertNotNull(TIMER_REF);
        long val = Long.parseLong(msg);
        // Test that the timer ran at least 200 ms after the acteur was called
        assertTrue(val > 110);
        // Force the VM to clear references and make sure the timer was disposed of
        for (int i = 0; i < 5; i++) {
            System.gc();
            System.runFinalization();
        }
        // Ensure ShutdownHookRegistry is not strongly referencing the timer
        assertNull("Timer not garbage collected", TIMER_REF.get());

        TIMER_REF = null;

        // Now make sure exception evaluation works properly
        CallResult res = harn.get("/p7").addQueryPair("fail", "true").setTimeout(TIMEOUT).go().await();
        res.assertStatus(PAYMENT_REQUIRED);
        res.assertContent("Hey");
    }

    static class ErrorMessage {

        public final String error;

        @JsonCreator
        public ErrorMessage(@JsonProperty("error") String error) {
            this.error = error;
        }

        public String toString() {
            return error;
        }

        public int hashCode() {
            return error.hashCode();
        }

        public boolean equals(Object o) {
            return o instanceof ErrorMessage && ((ErrorMessage) o).error.equals(error);
        }

        public static ErrorMessage of(String s) {
            return new ErrorMessage(s);
        }

        void assertMessage(String msg) {
            assertEquals(msg, error);
        }
    }

    static class AATApp extends Application {

        AATApp() {
            super(P1.class, P2.class, P3.class, P4.class, P5.class, P6.class, P7.class);
        }
    }

    @Methods(GET)
    @Path("/p1")
    static class P1 extends Page {

        P1() {
            add(P1A1.class);
        }
    }

    static class P1A1 extends Acteur {

        @Inject
        P1A1(ExecutorService svc) {
            then(CompletableFuture.supplyAsync(() -> {
                return Thing.of("p1a1");
            }, svc));
        }
    }

    @Methods(GET)
    @Path("/p2")
    static class P2 extends Page {

        P2() {
            add(P2A1.class);
        }
    }

    static class P2A1 extends Acteur {

        @Inject
        P2A1(ExecutorService svc) {
            then(CompletableFuture.supplyAsync(() -> {
                throw new ResponseException(EXPECTATION_FAILED, "p2a1");
            }, svc));
        }
    }

    @Methods(GET)
    @Path("/p3")
    static class P3 extends Page {

        P3() {
            add(P3A1.class);
        }
    }

    static class P3A1 extends Acteur {

        @Inject
        P3A1(ExecutorService svc) {
            then(CompletableFuture.supplyAsync(() -> {
                throw new IllegalThreadStateException("p3a1");
            }, svc));
        }
    }

    @Methods(GET)
    @Path("/p4")
    static class P4 extends Page {

        P4() {
            add(P4A1.class);
            add(P4A2.class);
        }
    }

    static class P4A1 extends Acteur {

        @Inject
        P4A1(ExecutorService svc) {
            this.continueAfter(CompletableFuture.completedFuture(Thing.of("wubba")), CompletableFuture.supplyAsync(() -> {
                return InetAddress.getLoopbackAddress();
            }, svc), CompletableFuture.supplyAsync(() -> {
                return new StringBuilder("foo");
            }));
        }
    }

    static class P4A2 extends Acteur {

        @Inject
        P4A2(InetAddress addr, Thing thing, StringBuilder sb) {
            reply(CREATED, map("addr").to(addr).map("thing").to(thing).map("txt").to(sb).build());
        }
    }

    @Methods(GET)
    @Path("/p5")
    static class P5 extends Page {

        P5() {
            add(P5A1.class);
            add(P4A2.class);
        }
    }

    static class P5A1 extends Acteur {

        @Inject
        P5A1(ExecutorService svc) {
            this.continueAfter(CompletableFuture.completedFuture(Thing.of("wubba")),
                    CompletableFuture.supplyAsync(() -> {
                        throw new IllegalMonitorStateException("uh oh");
                    }),
                    CompletableFuture.supplyAsync(InetAddress::getLoopbackAddress, svc),
                    CompletableFuture.supplyAsync(() -> {
                        return new StringBuilder("foo");
                    }));
        }
    }

    @Methods(GET)
    @Path("/p6")
    static class P6 extends Page {

        P6() {
            add(P4A1.class);
            add(P5A1.class);
            add(P4A2.class);
        }
    }

    static Reference<Timer> TIMER_REF = null;

    @Methods(GET)
    @Path("/p7")
    static class P7 extends Page {

        P7() {
            add(P7A1.class);
            add(P7A2.class);
        }

        static class P7A1 extends Acteur {

            @Inject
            P7A1(HttpEvent evt, ShutdownHookRegistry reg) {
                long now = System.currentTimeMillis();
                boolean fail = evt.urlParameter("fail") != null;
                continueAfter((c -> {
                    Timer t = new Timer();
                    TIMER_REF = new WeakReference<>(t);
                    reg.add(t);
                    t.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            long then = System.currentTimeMillis() - now;
                            if (fail) {
                                c.completeExceptionally(new FooException("Hey"));
                            } else {
                                c.complete(new LongWrapper(then));
                            }
                        }
                    }, 110);
                }));
            }
        }

        static class P7A2 extends Acteur {

            @Inject
            P7A2(LongWrapper lw) {
                reply(OK, lw.toString());
            }
        }
    }

    static class FooException extends RuntimeException {

        FooException(String msg) {
            super(msg);
        }
    }

    static class FooExceptionEval extends ExceptionEvaluator {

        @Inject
        public FooExceptionEval(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        protected int ordinal() {
            return -1;
        }

        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
            if (t instanceof FooException) {
                return ErrorResponse.create(HttpResponseStatus.PAYMENT_REQUIRED, t.getMessage());
            }
            return null;
        }
    }

    static class LongWrapper {

        private final long val;

        public LongWrapper(long val) {
            this.val = val;
        }

        public String toString() {
            return Long.toString(val);
        }
    }

    static class Module extends ServerModule<AATApp> {

        public Module() {
            this(new ReentrantScope());
        }

        public Module(ReentrantScope scope) {
            super(scope, AATApp.class, 6, 2, 2);
        }

        @Override
        protected void configure() {
            super.configure();
            bind(ExecutorService.class).toInstance(Executors.newCachedThreadPool());
            bind(FooExceptionEval.class).asEagerSingleton();
            scope.bindTypes(binder(), Thing.class, StringBuilder.class, InetAddress.class, LongWrapper.class);
        }
    }

    static int ix = 0;

    static class Thing {

        public final String name;
        public final int index;

        @JsonCreator
        public Thing(@JsonProperty("name") String name, @JsonProperty("index") int index) {
            this.name = name;
            this.index = index;
        }

        public static Thing last(String nm) {
            return new Thing(nm, ix - 1);
        }

        public static Thing of(String nm) {
            return new Thing(notNull("nm", nm), ix++);
        }

        @Override
        public String toString() {
            return "Thing{" + "name=" + name + ", index=" + index + '}';
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 23 * hash + Objects.hashCode(this.name);
            hash = 23 * hash + this.index;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Thing other = (Thing) obj;
            if (this.index != other.index) {
                return false;
            }
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return true;
        }

    }
}
