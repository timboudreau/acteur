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
import static com.mastfrog.acteur.headers.Headers.CONTENT_LENGTH;
import static com.mastfrog.acteur.headers.Headers.IF_MODIFIED_SINCE;
import static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerLifecycleHook;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.harness.Assertions;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.util.time.TimeUtil;
import io.netty.channel.Channel;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({HttpTestHarnessModule.class, ITM.class, SilentRequestLogger.class})
public class InternalsTest {

    private static final ZonedDateTime WHEN = ZonedDateTime.now().with(ChronoField.MILLI_OF_SECOND, 0);

    @Test
    @Timeout(30)
    public void testHeadersSharedBetweenActeurs(HttpHarness harn) throws Throwable {
        harn.get("shared").applyingAssertions(
                a -> a.assertHasHeader("x-expect")
                        .assertHasHeader(LAST_MODIFIED)
                        .assertOk()
                        .assertBody("Found " + Headers.ISO2822DateFormat.format(ZERO)))
                .assertAllSucceeded();
        harn.rethrowServerErrors();
    }

    @Test
    @Timeout(30)
    public void testDateHeaderHandling(HttpHarness harn) throws Throwable {

        ZonedDateTime when = harn.get("lm")
                .applyingAssertions(a -> a.assertOk().assertHasHeader(LAST_MODIFIED)).assertAllSucceeded()
                .get().headers().firstValue(LAST_MODIFIED.name().toString())
                .map(LAST_MODIFIED::convert)
                .orElseThrow(() -> new RuntimeException("No last modiefied header"));

        assertEquals(when.toInstant(), WHEN.toInstant());

        harn.get("lm").setHeader(IF_MODIFIED_SINCE, when)
                .applyingAssertions(Assertions::assertNotModified)
                .assertAllSucceeded();

        harn.get("lm").setHeader(IF_MODIFIED_SINCE, WHEN)
                .applyingAssertions(Assertions::assertNotModified)
                .assertAllSucceeded();

        harn.get("lm").setHeader(IF_MODIFIED_SINCE, WHEN.plus(Duration.ofHours(1)))
                .applyingAssertions(Assertions::assertNotModified)
                .assertAllSucceeded();

        harn.get("lm").setHeader(IF_MODIFIED_SINCE, WHEN.minus(Duration.ofHours(1)))
                .applyingAssertions(Assertions::assertOk)
                .assertAllSucceeded();

        assertTrue(HOOK_RAN.get() > 0, "Startup hook was not run");
        harn.rethrowServerErrors();
    }

    @Test
    @Timeout(30)
    public void testEmptyResponsesHaveZeroLengthContentLengthHeader(HttpHarness harn) throws Throwable {
        String lenHeader = harn.get("/nothing").applyingAssertions(Assertions::assertOk)
                .assertAllSucceeded().get().headers().firstValue(CONTENT_LENGTH.name().toString())
                .orElse(null);
        assertEquals("0", lenHeader, "Should not have a length header");
    }

    @Test
    @Timeout(30)
    public void testEmptyResponsesForContentlessCodesHaveNoContentLengthHeader(HttpHarness harn) throws Throwable {
        String lenHeader = harn.get("/less").applyingAssertions(Assertions::assertNotModified)
                .assertAllSucceeded().get().headers().firstValue(CONTENT_LENGTH.name().toString())
                .orElse(null);
        assertNull(lenHeader, "Should not have a length header on a 304 response, but got " + lenHeader);
    }

    @Test
    @Timeout(30)
    public void testNoContentResponseHasNoContentLength(HttpHarness harn) throws Throwable {
        String lenHeader = harn.get("/evenless").applyingAssertions(Assertions::assertNoContent)
                .assertAllSucceeded().get().headers().firstValue(CONTENT_LENGTH.name().toString())
                .orElse(null);
        assertNull(lenHeader, "Should not have a length header on a no-content response, but got " + lenHeader);

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
        HookImpl(Registry reg) {
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
            add(NothingChunked.class);
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
        @Path("/nothingchunked")
        static class NothingChunked extends Page {

            NothingChunked() {
                add(NothingChunkedActeur.class);
            }

            static class NothingChunkedActeur extends Acteur {

                NothingChunkedActeur() {
                    setChunked(true);
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
                    ok("Found " + (found == null ? "null" : Headers.ISO2822DateFormat.format(ZERO)));
                }
            }
        }
    }
}
