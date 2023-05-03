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
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import static java.util.concurrent.TimeUnit.MINUTES;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({HttpTestHarnessModule.class, ErrTest.M.class, SilentRequestLogger.class})
public class ErrTest {

    @Timeout(value = 1, unit = MINUTES)
    @Test
    public void testHeadersPropagateViaErr(HttpHarness harn) throws Throwable {
        harn.get("errant")
                .applyingAssertions(
                        a -> a.assertResponseCode(503)
                                .assertBody("{\"error\":\"Glorg\",\"whatever\":\"hey\"}")
                                .assertHeaderEquals("x-fwee", "foober"))
                .assertAllSucceeded();
    }

    @Timeout(value = 1, unit = MINUTES)
    @Test
    public void testHeadersPropagateViaThrow(HttpHarness harn) throws Throwable {
        harn.get("throw")
                .applyingAssertions(
                        a -> a.assertResponseCode(502)
                                .assertHeaderEquals("hork", "snorg")
                                .assertBody("{\"error\":\"Woovle\"}"))
                .assertAllSucceeded();
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
            add(ErrActeur.class);
        }
    }

    static class ErrPage2 extends Page {

        @Inject
        ErrPage2(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^throw$"));
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
}
