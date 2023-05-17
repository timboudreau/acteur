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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.RequiredHeadersAndMimeTypesTest.HM;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.POST;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.preconditions.AbsenceAction;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.RequireContentType;
import com.mastfrog.acteur.preconditions.RequireHeader;
import com.mastfrog.acteur.preconditions.RequireHeaders;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import static io.netty.buffer.ByteBufUtil.writeUtf8;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.PAYMENT_REQUIRED;
import io.netty.handler.codec.http.HttpVersion;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import java.net.http.HttpRequest.BodyPublishers;
import static java.nio.charset.StandardCharsets.UTF_16;
import static org.bouncycastle.oer.OERDefinition.seq;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({SilentRequestLogger.class, HM.class, HttpTestHarnessModule.class})
public final class RequiredHeadersAndMimeTypesTest {

    @Test
    public void testSimpleHeader(HttpHarness harn) throws Exception {
        harn.get("flooger")
                .header("x-flooger", "hey")
                .asserting(a -> a.assertOk().assertBody("Flooger hey"))
                .assertAllSucceeded();

        harn.get("flooger")
                .asserting(a -> a.assertBadRequest()
                .assertBody("{\"error\":\"Missing header 'x-flooger'\"}"))
                .assertAllSucceeded().get().body();
    }

    @Test
    public void testTwoHeaders(HttpHarness harn) throws Exception {
        harn.get("fwookie")
                .header("x-flooger", "mook")
                .header("x-wookie", "blorb")
                .asserting(a -> a.assertOk().assertBody("Flooger mook wookie blorb"))
                .assertAllSucceeded().get().body();

        harn.get("fwookie")
                .asserting(a -> a.assertBadRequest()
                .assertBody("{\"error\":\"Missing header 'x-flooger'\"}"))
                .assertAllSucceeded().get().body();

        harn.get("fwookie")
                .header("x-flooger", "hey")
                .asserting(a -> a.assertBadRequest()
                .assertBody("{\"error\":\"Missing header 'x-wookie'\"}"))
                .assertAllSucceeded().get().body();
    }

    @Test
    public void testPatternWithFallback(HttpHarness harn) throws Exception {
        harn.get("fpat")
                .asserting(a -> a.assertCreated().assertBody("Fallback"))
                .assertAllSucceeded();

        harn.get("fpat")
                .header("x-flooger", "bwee")
                .asserting(a -> a.assertBadRequest()
                .assertBody("{\"error\":\"Invalid header 'bwee' for x-flooger\"}"))
                .assertAllSucceeded()
                .get().body();
    }

    @Test
    public void testRequiringMimeType(HttpHarness harn) throws Exception {
        byte[] body = "testRequiringMimeType".getBytes(UTF_16);
        harn.put("mimey", BodyPublishers.ofByteArray(body))
                .header("content-type", "text/plain;charset=utf-16")
                .asserting(a -> a.assertOk()
                .assertBody("Mimey text/plain;charset=utf-16: testRequiringMimeType"))
                .assertAllSucceeded()
                .get().body();

        harn.put("mimey", "This is stuff")
                .asserting(a -> a.assertNotFound())
                .assertAllSucceeded()
                .get()
                .body();

        harn.put("mimey", "This is stuff")
                .header("content-type", "text/x-foodbar;charset=utf-32")
                .asserting(a -> a.assertBadRequest()
                .assertBody(
                        "{\"error\":\"content-type header mismatch\",\"validOptions\":\"image/gif, text/plain, text/plain;charset=utf-16, text/unpleasant\"}"))
                .assertAllSucceeded()
                .get()
                .body();

        harn.put("mimey", BodyPublishers.ofByteArray(body))
                .header("content-type", "text/unpleasant;charset=utf-16")
                .asserting(a -> a.assertOk()
                .assertBody("Mimey text/unpleasant;charset=utf-16: testRequiringMimeType"))
                .assertAllSucceeded()
                .get().body();
    }

    @Test
    public void testFailureResponseFactory(HttpHarness harn) throws Exception {
        harn.post("notgood")
                .asserting(a -> a.assertStatus(PAYMENT_REQUIRED.code())
                .assertBody("A bad thing happened: IllegalThreadStateException")
                .assertHeader("content-type", "text/plain;charset=utf8")
                .assertHeader("x-foo", "bar"))
                .assertAllSucceeded()
                .get();

        harn.get("wurbledygurble")
                .asserting(a -> a.assertStatus(NOT_FOUND.code())
                .assertBody("Sorry, Charlie.")
                .assertHeader("x-mook", "blurb"))
                .assertAllSucceeded()
                .get();
    }

    static class HM extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(A.class));
            bind(FailureResponseFactory.class).to(FRF.class);
        }
    }

    @SuppressWarnings("deprecation")
    @com.mastfrog.acteur.ImplicitBindings(String.class)
    static class A extends Application {

        A() {
            add(P2.class);
            add(P1.class);
            add(P3.class);
            add(P3Fallback.class);
            add(P4.class);
            add(P5.class);
        }
    }

    @Methods(GET)
    @Path("/flooger")
    @RequireHeader("x-flooger")
    static class P1 extends Page {

        P1() {
            add(WantsXFlooger.class);
        }
    }

    static class WantsXFlooger extends Acteur {

        @Inject
        WantsXFlooger(HttpEvent evt) {
            ok("Flooger " + evt.header("x-flooger"));
        }
    }

    @Methods(GET)
    @Path("/fwookie")
    @RequireHeaders({
        @RequireHeader("x-flooger"),
        @RequireHeader("x-wookie")
    })
    static class P2 extends Page {

        P2() {
            add(WantsXFloogerAndXWookie.class);
        }
    }

    static class WantsXFloogerAndXWookie extends Acteur {

        @Inject
        WantsXFloogerAndXWookie(HttpEvent evt) {
            ok("Flooger " + evt.header("x-flooger") + " wookie " + evt.header("x-wookie"));
        }
    }

    @Methods(GET)
    @Path("/fpat")
    @RequireHeaders({
        @RequireHeader(value = "x-flooger", whenAbsent = AbsenceAction.REJECT, mustMatchPatterns = {"^\\d+$", "^[e-p]+$"})
    })
    static class P3 extends Page {

        P3() {
            add(WantsFloogerWithPattterns.class);
        }
    }

    static class WantsFloogerWithPattterns extends Acteur {

        @Inject
        WantsFloogerWithPattterns(HttpEvent evt) {
            ok("Got " + evt.header("x-flooger"));
        }
    }

    @Methods(GET)
    @Path("/fpat")
    static class P3Fallback extends Page {

        @Inject
        P3Fallback(ActeurFactory af) {
            add(af.respondWith(HttpResponseStatus.CREATED, "Fallback"));
        }
    }

    @Methods(PUT)
    @Path("/mimey")
    @InjectRequestBodyAs(String.class)
    @RequireContentType(value = {"text/plain;charset=utf-16", "text/plain", "image/gif", "text/unpleasant"},
            whenHeaderAbsent = AbsenceAction.BAD_REQUEST)
    static class P4 extends Page {

        P4() {
            add(Mimey.class);
        }
    }

    static class Mimey extends Acteur {

        @Inject
        Mimey(HttpEvent evt, String str) {
            ok("Mimey " + evt.header(CONTENT_TYPE) + ": " + str);
        }
    }

    @Methods(POST)
    @Path("/notgood")
    static class P5 extends Page {

        P5() {
            add(Throws.class);
        }
    }

    static class Throws extends Acteur {

        Throws() {
            throw new IllegalThreadStateException("Blah!");
        }
    }

    static FRF frf;

    @Singleton
    static class FRF implements FailureResponseFactory {

        private final ByteBufAllocator alloc;
        private Throwable thrown;

        private boolean calledForNotFound;

        @Inject
        FRF(ByteBufAllocator alloc) {
            this.alloc = alloc;
            frf = this;
        }

        @Override
        public HttpResponse createNotFoundResponse(Event<?> evt) {
            calledForNotFound = true;
            DefaultFullHttpResponse r = new DefaultFullHttpResponse(HTTP_1_1,
                    NOT_FOUND, writeUtf8(alloc, "Sorry, Charlie."));
            r.headers().add("content-type", "text/plain;charset=utf8");
            r.headers().add("x-mook", "blurb");
            return r;
        }

        @Override
        public HttpResponse createFallbackResponse(Throwable thrown) {
            DefaultFullHttpResponse r = new DefaultFullHttpResponse(HTTP_1_1,
                    PAYMENT_REQUIRED, writeUtf8(alloc, "A bad thing happened: "
                            + thrown.getClass().getSimpleName()));
            r.headers().add("x-foo", "bar");
            r.headers().add("content-type", "text/plain;charset=utf8");
            return r;
        }

    }
}
