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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluator;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.BACKGROUND_THREAD_POOL_NAME;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.mime.MimeType;
import com.mastfrog.util.function.EnhCompletableFuture;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@TestWith({DeferralStyleTest.M.class, HttpTestHarnessModule.class, SilentRequestLogger.class})
public final class DeferralStyleTest {

    @Test
    @Timeout(30)
    public void testCompletableFutureDeferralWithNormalCompletion(HttpHarness harn) throws Exception {
        harn.get("one").asserting(a -> {
            a.assertCreated()
                    .assertBody(b -> {
                        return "D1 success".equals(b);
                    });
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testCompletableFutureDeferralWithExceptionalCompletion(HttpHarness harn) throws Exception {
        harn.get("two").asserting(a -> {
            a.assertGone()
                    .assertBody(b -> {
                        return "{\"error\":\"It is gone\",\"msg\":\"This is stuff\"}".equals(b);
                    });
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testCompetableFutureDeferralWithCompletionObjectInjectedIntoNextActeur(HttpHarness harn) throws Exception {
        harn.get("three").asserting(a -> {
            a.assertStatus(NON_AUTHORITATIVE_INFORMATION.code())
                    .assertHeader("content-type", "text/x-doohickey")
                    .assertBody(b -> {
                        return "{\"intValue\":23,\"stringValue\":\"skiddoo\"}".equals(b);
                    });
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testCompetableFutureDeferralWithCompletionObjectInjectedIntoNextActeurExceptional(HttpHarness harn, AtomicBoolean dhRan) throws Exception {
        harn.get("four").asserting(a -> {
            a.assertGone()
                    .assertBody(b -> {
                        return "{\"error\":\"It is gone\",\"msg\":\"Blort\"}".equals(b);
                    });
        }).assertAllSucceeded();
        assertFalse(dhRan.get());
    }

    @Test
    @Timeout(30)
    public void testDeferralResumingViaResumer(HttpHarness harn) throws Exception {
        harn.get("five").asserting(a -> {
            a.assertStatus(NON_AUTHORITATIVE_INFORMATION.code())
                    .assertHeader("content-type", "text/x-doohickey")
                    .assertBody(b -> {
                        return "{\"intValue\":37,\"stringValue\":\"Stuff\"}".equals(b);
                    });
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testDeferralWithExceptionInResumer(HttpHarness harn) throws Exception {
        harn.get("six").asserting(a -> {
            a.assertGone()
                    .assertBody(b -> {
                        return "{\"error\":\"It is gone\",\"msg\":\"Glork\"}".equals(b);
                    });
        }).assertAllSucceeded();
    }

    static class M implements Module {

        @Override
        public void configure(Binder binder) {
            binder.install(new ServerModule<>(A.class));
            binder.bind(ThingamabobEval.class).asEagerSingleton();
            binder.bind(AtomicBoolean.class).toInstance(new AtomicBoolean());
        }
    }

    @SuppressWarnings("deprecation")
    @com.mastfrog.acteur.ImplicitBindings({Doohickey.class})
    static class A extends Application {

        A() {
            add(P1.class);
            add(P2.class);
            add(P3.class);
            add(P4.class);
            add(P5.class);
            add(P6.class);
        }
    }

    static class P1 extends Page {

        @Inject
        P1(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^one$"));
            add(D1.class);
        }
    }

    static class P2 extends Page {

        @Inject
        P2(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^two$"));
            add(D2.class);
        }
    }

    static class P3 extends Page {

        @Inject
        P3(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^three$"));
            add(D3.class);
            add(DoohickeyEmitter.class);
        }
    }

    static class P4 extends Page {

        @Inject
        P4(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^four$"));
            add(D4.class);
            add(DoohickeyEmitter2.class);
        }
    }

    static class P5 extends Page {

        @Inject
        P5(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^five$"));
            add(D5.class);
            add(DoohickeyEmitter.class);
        }
    }

    static class P6 extends Page {

        @Inject
        P6(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^six$"));
            add(D6.class);
            add(DoohickeyEmitter.class);
        }
    }

    static class D1 extends Acteur {

        @Inject
        D1(@Named(BACKGROUND_THREAD_POOL_NAME) ExecutorService svc) {
            EnhCompletableFuture<String> fut = deferThenRespond(CREATED);
            svc.submit(() -> {
                Thread.sleep(100);
                fut.complete("D1 success");
                return null;
            });
        }
    }

    static class D2 extends Acteur {

        @Inject
        D2(@Named(BACKGROUND_THREAD_POOL_NAME) ExecutorService svc) {
            EnhCompletableFuture<?> fut = deferThenRespond(CREATED);
            svc.submit(() -> {
                fut.completeExceptionally(new Thingamabob("This is stuff"));
                return null;
            });
        }
    }

    static class D3 extends Acteur {

        @Inject
        D3(@Named(BACKGROUND_THREAD_POOL_NAME) ExecutorService svc) {
            add(CONTENT_TYPE, MimeType.parse("text/x-doohickey"));
            EnhCompletableFuture<Doohickey> fut = defer();
            svc.submit(() -> {
                fut.complete(new Doohickey(23, "skiddoo"));
                return null;
            });
        }
    }

    static class D4 extends Acteur {

        @Inject
        D4(@Named(BACKGROUND_THREAD_POOL_NAME) ExecutorService svc) {
            add(CONTENT_TYPE, MimeType.parse("text/x-doohickey"));
            EnhCompletableFuture<Doohickey> fut = defer();
            svc.submit(() -> {
                fut.completeExceptionally(new Thingamabob("Blort"));
                return null;
            });
        }
    }

    static class D5 extends Acteur {

        @Inject
        D5(Deferral def) {
            add(CONTENT_TYPE, MimeType.parse("text/x-doohickey"));
            def.defer(resumer -> {
                assertTrue(Thread.currentThread() instanceof FastThreadLocalThread);
                resumer.resume(new Doohickey(37, "Stuff"));
            });
            next();
        }
    }

    static class D6 extends Acteur {

        @Inject
        D6(Deferral def) {
            add(CONTENT_TYPE, MimeType.parse("text/x-doohickey"));
            def.defer(resumer -> {
                throw new Thingamabob("Glork");
            });
            next();
        }
    }

    static class DoohickeyEmitter extends Acteur {

        @Inject
        DoohickeyEmitter(Doohickey dh) {
            reply(NON_AUTHORITATIVE_INFORMATION, dh);
        }
    }

    static class DoohickeyEmitter2 extends Acteur {

        @Inject
        DoohickeyEmitter2(Doohickey dh, AtomicBoolean dhran) {
            dhran.set(true);
            reply(NON_AUTHORITATIVE_INFORMATION, dh);
        }
    }

    static class Thingamabob extends RuntimeException {

        Thingamabob(String message) {
            super(message);
        }
    }

    static class ThingamabobEval extends ExceptionEvaluator {

        @Inject
        ThingamabobEval(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
            if (t instanceof Thingamabob) {
                return Err.gone("It is gone").put("msg", t.getMessage());
            }
            return null;
        }
    }

    public static final class Doohickey {

        public final int intValue;
        public final String stringValue;

        @JsonCreator
        public Doohickey(@JsonProperty("intValue") int intValue, @JsonProperty("stringValue") String stringValue) {
            this.intValue = intValue;
            this.stringValue = stringValue;
        }

        @Override
        public String toString() {
            return "Doohickey{" + "intValue=" + intValue + ", stringValue=" + stringValue + '}';
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + this.intValue;
            hash = 59 * hash + Objects.hashCode(this.stringValue);
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
            final Doohickey other = (Doohickey) obj;
            if (this.intValue != other.intValue) {
                return false;
            }
            return Objects.equals(this.stringValue, other.stringValue);
        }

    }
}
