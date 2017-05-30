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

import com.mastfrog.acteur.InternalsTest.ITM;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, ITM.class})
@RunWith(GuiceRunner.class)
public class InternalsTest {
    
    static {
        System.setProperty("acteur.debug", "true");
    }

    @Test
    public void testHeadersSharedBetweenActeurs(TestHarness harn) throws Throwable {
        harn.get("shared").go()
                .assertHasHeader("x-expect")
                .assertHasHeader(Headers.LAST_MODIFIED)
                .assertStatus(OK)
                .assertContent("Found " + Headers.ISO2822DateFormat.print(ZERO));
    }

    static final class ITM extends ServerModule<ITApp> {

        ITM() {
            super(ITApp.class, 2, 2, 3);
        }
    }

    static final DateTime ZERO = new DateTime(0).withZone(DateTimeZone.getDefault());

    static class ITApp extends Application {

        ITApp() {
            add(SharedHeadersPage.class);
        }

        @Methods(GET)
        @Path("/shared")
        static class SharedHeadersPage extends Page {

            SharedHeadersPage() {
                add(A1.class);
                add(A2.class);
            }

            static final class A1 extends Acteur {

                A1() {
                    add(Headers.LAST_MODIFIED, ZERO);
                    add(Headers.stringHeader("x-expect"), Headers.ISO2822DateFormat.print(ZERO));
                    next(ZERO);
                }
            }

            static class A2 extends Acteur {

                A2() {
                    DateTime found = response().get(Headers.LAST_MODIFIED);
                    System.out.println("FOUND " + found);
                    ok("Found " + (found == null ? "null" : Headers.ISO2822DateFormat.print(ZERO)));
                }
            }
        }
    }
}
