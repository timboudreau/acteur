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

import com.google.inject.Inject;
import com.mastfrog.acteur.InternalsTest.ITM;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerLifecycleHook;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.util.time.TimeUtil;
import io.netty.channel.Channel;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, ITM.class})
@RunWith(GuiceRunner.class)
public class InternalsTest {

    private static final ZonedDateTime WHEN = ZonedDateTime.now().with(ChronoField.MILLI_OF_SECOND, 0);

    static {
        System.setProperty("acteur.debug", "true");
    }

    @Test
    public void testHeadersSharedBetweenActeurs(TestHarness harn) throws Throwable {
        harn.get("shared").go()
                .assertHasHeader("x-expect")
                .assertHasHeader(Headers.LAST_MODIFIED)
                .assertStatus(OK)
                .assertContent("Found " + Headers.ISO2822DateFormat.format(ZERO));
    }

    @Test
    public void testDateHeaderHandling(TestHarness harn) throws Throwable {
        ZonedDateTime when = harn.get("lm").go().assertHasHeader(Headers.LAST_MODIFIED)
                .assertContent("Got here.")
                .getHeader(Headers.LAST_MODIFIED);
        assertEquals(when.toInstant(), WHEN.toInstant());

        harn.get("lm").addHeader(Headers.IF_MODIFIED_SINCE, when).go().assertStatus(NOT_MODIFIED);
        harn.get("lm").addHeader(Headers.IF_MODIFIED_SINCE, WHEN).go().assertStatus(NOT_MODIFIED);
        harn.get("lm").addHeader(Headers.IF_MODIFIED_SINCE, WHEN.plus(Duration.ofHours(1))).go().assertStatus(NOT_MODIFIED);
        harn.get("lm").addHeader(Headers.IF_MODIFIED_SINCE, WHEN.minus(Duration.ofHours(1))).go().assertStatus(OK);
        assertTrue("Startup hook was not run", HOOK_RAN.get() > 0);
    }

    @Test
    public void testEmptyResponsesHaveZeroLengthContentLengthHeader(TestHarness harn) throws Throwable {
        harn.get("/nothing").go().await().assertHeader(Headers.CONTENT_LENGTH, 0L).assertStatus(OK);
    }

    @Test
    public void testEmptyResponsesForContentlessCodesHaveNoContentLengthHeader(TestHarness harn) throws Throwable {
        assertNull("Should not have had a content length header", harn.get("/less").go().await()
                .assertStatus(NOT_MODIFIED)
                .getHeader(Headers.CONTENT_LENGTH));
        assertNull("Should not have had a content length header", harn.get("/evenless").go().await()
                .assertStatus(NO_CONTENT)
                .getHeader(Headers.CONTENT_LENGTH));
    }

    static final class ITM extends ServerModule<ITApp> {

        ITM() {
            super(ITApp.class, 2, 2, 3);
        }

        @Override
        protected void configure() {
            System.setProperty(ServerModule.PORT, "" + new PortFinder().findAvailableServerPort());
            bind(HookImpl.class).asEagerSingleton();
            super.configure();
        }
    }

    private static final AtomicInteger HOOK_RAN = new AtomicInteger();
    static final class HookImpl extends ServerLifecycleHook {

        @Inject
        public HookImpl(Registry reg) {
            super(reg);
        }

        @Override
        protected void onStartup(Application application, Channel channel) throws Exception {
            int amt = HOOK_RAN.incrementAndGet();
        }

    }

    static final ZonedDateTime ZERO = TimeUtil.fromUnixTimestamp(0).withZoneSameInstant(ZoneId.systemDefault());

    static class ITApp extends Application {

        ITApp() {
            add(SharedHeadersPage.class);
            add(LastModifiedPage.class);
            add(DoLittlePage.class);
            add(DoLessPage.class);
            add(DoEvenLessPage.class);
        }

        @Methods(GET)
        @Path("/lm")
        static class LastModifiedPage extends Page {

            LastModifiedPage() {
                add(LMActeur.class);
                add(CheckIfModifiedSinceHeader.class);
                add(MsgActeur.class);
            }
        }

        static class LMActeur extends Acteur {

            LMActeur() {
                add(Headers.LAST_MODIFIED, WHEN);
                next();
            }
        }

        static class MsgActeur extends Acteur {

            MsgActeur() {
                ok("Got here.");
            }
        }

        @Methods(GET)
        @Path("/nothing")
        static class DoLittlePage extends Page {
            DoLittlePage() {
                add(DoLittleActeur.class);
            }
            static class DoLittleActeur extends Acteur {
                DoLittleActeur() {
                    ok();
                }
            }
        }

        @Methods(GET)
        @Path("/less")
        static class DoLessPage extends Page {
            DoLessPage() {
                add(DoLessActeur.class);
            }
            static class DoLessActeur extends Acteur {
                DoLessActeur() {
                    reply(NOT_MODIFIED);
                }
            }
        }

        @Methods(GET)
        @Path("/evenless")
        static class DoEvenLessPage extends Page {
            DoEvenLessPage() {
                add(DoEvenLessActeur.class);
            }
            static class DoEvenLessActeur extends Acteur {
                DoEvenLessActeur() {
                    reply(NO_CONTENT);
                }
            }
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
                    add(Headers.header("x-expect"), Headers.ISO2822DateFormat.format(ZERO));
                    next(ZERO);
                }
            }

            static class A2 extends Acteur {

                A2() {
                    ZonedDateTime found = response().get(Headers.LAST_MODIFIED);
                    System.out.println("FOUND " + found);
                    ok("Found " + (found == null ? "null" : Headers.ISO2822DateFormat.format(ZERO)));
                }
            }
        }
    }
}
