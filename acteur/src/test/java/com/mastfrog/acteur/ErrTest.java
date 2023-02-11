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

import com.google.inject.Inject;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ResponseException;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({TestHarnessModule.class, ErrTest.M.class, SilentRequestLogger.class})
public class ErrTest {

    @Test(timeout = 60000)
    public void testHeadersPropagateViaErr(TestHarness harn) throws Throwable {
        String ct = harn.get("errant").go()
                .await()
                .assertCode(503)
                .assertHeader(Headers.header("x-fwee"), "foober")
                .content();
        assertNotNull(ct);
        assertEquals("{\"error\":\"Glorg\",\"whatever\":\"hey\"}", ct.trim());
    }

    @Test(timeout = 60000)
    public void testHeadersPropagateViaThrow(TestHarness harn) throws Throwable {
        String ct = harn.get("throw").go()
                .await()
                .assertCode(502)
                .assertHeader(Headers.header("hork"), "snorg")
                .content();
        assertNotNull(ct);
        assertEquals("{\"error\":\"Woovle\"}", ct);
    }

    static class ErrApp extends Application {

        ErrApp() {
            add(ErrPage.class);
            add(ErrPage2.class);
        }
    }

    static class ErrPage extends Page {

        @Inject
        ErrPage(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^errant$"));
//            add(f.exactPathLength(1));
            add(ErrActeur.class);
        }
    }

    static class ErrPage2 extends Page {

        @Inject
        ErrPage2(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^throw$"));
//            add(f.exactPathLength(1));
            add(ErrActeur2.class);
        }
    }

    static class ErrActeur extends Acteur {

        ErrActeur() {
            reply(Err.withCode(503, "Glorg").put("whatever", "hey").withHeader("x-fwee", "foober"));
        }
    }

    static class ErrActeur2 extends Acteur {

        ErrActeur2() {
            throw new ResponseException(HttpResponseStatus.BAD_GATEWAY, "Woovle")
                    .withHeader("hork", "snorg")
                    .withHeader("yoop", "bloop");
        }
    }

    static class M extends ServerModule<ErrApp> {

        public M() {
            super(ErrApp.class);
        }

    }

    @Before
    public void setup() {

    }
}
