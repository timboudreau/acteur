/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import com.mastfrog.acteur.ResponseDecoratorTest.M;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.SET_COOKIE_B_STRICT;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, TestHarnessModule.class, SilentRequestLogger.class})
public class ResponseDecoratorTest {

    @Test
    public void testResponseDecorator(TestHarness harn, RD rd) throws Throwable {
        TestHarness.CallResult res = harn.get("/test")
                .setTimeout(Duration.ofMinutes(1))
                .go().await()
                .assertStatus(OK)
                .assertHasHeader(SET_COOKIE_B_STRICT)
                .assertHasHeader(Headers.stringHeader("X-foo"));

        Iterable<Cookie> cks = res.getHeaders(SET_COOKIE_B_STRICT);
        Set<Cookie> cookies = new HashSet<>();
        for (Cookie c : cks) {
            assertTrue(c.name(), c.name().equals("a") || c.name().equals("c"));
            cookies.add(c);
        }
        assertEquals(2, cookies.size());
        assertEquals(1, rd.nums.get());
    }

    static final class RDApp extends Application {

        RDApp() {
            add(RDPage.class);
        }

        @Path("/test")
        static final class RDPage extends Page {

            RDPage() {
                add(RDRespond.class);
            }

            static final class RDRespond extends Acteur {

                RDRespond() {
                    ok("test");
                }
            }
        }
    }

    static final class M extends ServerModule<RDApp> {

        M() {
            super(RDApp.class);
        }

        @Override
        protected void configure() {
            super.configure();
            RD rd = new RD();
            bind(ResponseDecorator.class).toInstance(rd);
            bind(RD.class).toInstance(rd);
        }
    }

    static final class RD implements ResponseDecorator {

        final AtomicInteger nums = new AtomicInteger();

        @Override
        public void onBeforeSendResponse(Application application, HttpResponseStatus status, Event<?> event, Response response, Acteur acteur, Page page) {
            assertTrue(application instanceof RDApp);
            response.add(Headers.stringHeader("X-Foo"), "whee-" + nums.getAndIncrement());
            DefaultCookie ck1 = new DefaultCookie("a", "b");
            DefaultCookie ck2 = new DefaultCookie("c", "d");
            response.add(Headers.SET_COOKIE_B_STRICT, ck1);
            response.add(Headers.SET_COOKIE_B_STRICT, ck2);
        }

    }
}
