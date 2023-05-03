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

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.predicates.string.StringPredicates;
import com.mastfrog.url.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({SslTest.M.class, HttpTestHarnessModule.class, SilentRequestLogger.class})
public class SslTest {

    @Test
    @Timeout(35)
    public void testSsl(HttpHarness harn) {
        harn.get("/test").applyingAssertions(
                a -> a.assertOk().assertHasBody()
                        .assertBody(StringPredicates.startsWith("https\n" + MESSAGE))
                        .assertHasHeader("X-Request-Encrypted")
        ).assertAllSucceeded();
    }

    static class M implements Module {

        @Override
        public void configure(Binder binder) {
            binder.install(new ServerModule(SslApp.class));
        }
    }

    static final class SslApp extends Application {

        @Inject
        SslApp() {
            add(TestPage.class);
        }

        private static final class TestPage extends Page {

            @Inject
            TestPage() {
                add(TestActeur.class);
            }
        }

        private static final class TestActeur extends Acteur {

            @Inject
            TestActeur(HttpEvent evt, Protocol protocol) {
                if (evt.isSsl()) {
                    add(Headers.header("X-Request-Encrypted"), "true");
                }
                ok(protocol + "\n" + MESSAGE);
            }
        }

    }
    static final String MESSAGE;

    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("This is line ").append(i).append("\n");
        }
        MESSAGE = sb.toString();
    }
}
