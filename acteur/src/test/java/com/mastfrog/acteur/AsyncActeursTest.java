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
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.collections.StringObjectMap;
import static com.mastfrog.util.preconditions.Checks.notNull;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.EXPECTATION_FAILED;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.PAYMENT_REQUIRED;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.concurrent.TimeUnit.MINUTES;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({HttpTestHarnessModule.class, AsyncActeursTest.Module.class, SilentRequestLogger.class})
public class AsyncActeursTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final long DUR = 2 * 1_000 * 60;

    @Timeout(value = 2, unit = MINUTES)
    @Test
    public void testAsynchronous(HttpHarness harn) throws Throwable {

        Thing t = harn.get("/p1").asserting(assertions -> {
            assertions.assertOk();
        }).await().assertAllSucceeded().get(Thing.class);

        assertNotNull(t);
        assertEquals(Thing.last("p1a1"), t);

        harn.get("/p2").asserting(asserts -> {
            asserts.assertVersion(HttpClient.Version.HTTP_1_1)
                    .assertStatus(EXPECTATION_FAILED.code());
        }).assertAllSucceeded().get(ErrorMessage.class).assertMessage("p2a1");

        harn.get("p3").asserting(asserts -> {
            asserts.assertVersion(HttpClient.Version.HTTP_1_1)
                    .assertStatus(INTERNAL_SERVER_ERROR.code());
        }).assertAllSucceeded().get(ErrorMessage.class).assertMessage("p3a1");

        Map<String, Object> m = harn.get("/p4").asserting(asserts -> {
            asserts.assertStatus(CREATED.code());
        }).assertAllSucceeded().get(StringObjectMap.class);

        assertEquals("foo", m.get("txt"));
        assertEquals(InetAddress.getLoopbackAddress().getHostName(), m.get("addr"));
        assertTrue(m.get("thing") instanceof Map<?, ?>);
        Map<?, ?> m1 = (Map<?, ?>) m.get("thing");
        assertEquals("wubba", m1.get("name"));

        harn.get("/p5").asserting(asserts -> {
            asserts.assertStatus(INTERNAL_SERVER_ERROR.code());
        }).assertAllSucceeded().get(ErrorMessage.class).assertMessage("uh oh");

        harn.get("p6").asserting(asserts -> {
            asserts.assertStatus(INTERNAL_SERVER_ERROR.code());
        }).assertAllSucceeded().get(ErrorMessage.class).assertMessage("uh oh");

        String msg = harn.get("/p7").asserting(asserts -> {
            asserts.assertOk();
        }).assertAllSucceeded().get(String.class);

        // Test that we run deferred after the timer expires
        assertNotNull(msg, "Response body was empty");
        assertTrue(Pattern.compile("^\\d+$").matcher(msg).find(), msg);
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
        assertNull(TIMER_REF.get(), "Timer not garbage collected");

        TIMER_REF = null;

        harn.get("/p7?fail=true").asserting(asserts -> {
            asserts.assertStatus(PAYMENT_REQUIRED.code())
                    .assertBody("Hey");
        }).assertAllSucceeded();
    }

    record ErrorMessage(String error) {

            @JsonCreator
            ErrorMessage(@JsonProperty("error") String error) {
                this.error = error;
            }

            @Override
            public String toString() {
                return error;
            }

        @Override
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
        FooExceptionEval(ExceptionEvaluatorRegistry registry) {
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

        LongWrapper(long val) {
            this.val = val;
        }

        @Override
        public String toString() {
            return Long.toString(val);
        }
    }

    static class Module extends ServerModule<AATApp> {

        Module() {
            this(new ReentrantScope());
        }

        Module(ReentrantScope scope) {
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

    record Thing(String name, int index) {

            @JsonCreator
            Thing(@JsonProperty("name") String name, @JsonProperty("index") int index) {
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
                return Objects.equals(this.name, other.name);
            }
        }
}
