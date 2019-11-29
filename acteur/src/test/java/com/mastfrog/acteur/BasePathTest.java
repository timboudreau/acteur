/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.PORT;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_BASE_PATH;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.util.preconditions.Exceptions;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.io.IOException;
import java.time.Duration;
import org.junit.Test;

public class BasePathTest {

    private static final PortFinder pf = new PortFinder();
    private static final Duration TO = Duration.ofSeconds(5);

    @Test
    public void testBp1() throws Throwable {
        withRunningServer("foo", (port, harn) -> {
            try {

                String rc = harn.get("foo").setTimeout(TO)
                        .go().await().assertStatus(OK)
                        .assertHeader(Headers.stringHeader("X-Req-Path"), "/")
                        .assertContent("root: ")
                        .content();

                harn.get("foo/").setTimeout(TO)
                        .go().await().assertStatus(OK)
                        .assertHeader(Headers.stringHeader("X-Req-Path"), "/")
                        .assertContent("root: ")
                        .content();

                harn.get("/foo/").setTimeout(TO)
                        .go().await().assertStatus(OK)
                        .assertHeader(Headers.stringHeader("X-Req-Path"), "/")
                        .assertContent("root: ")
                        .content();

                harn.get("/foo").setTimeout(TO)
                        .go().await().assertStatus(OK)
                        .assertHeader(Headers.stringHeader("X-Req-Path"), "/")
                        .assertContent("root: ")
                        .content();

                System.out.println("rc: '" + rc + "'");

                harn.get("foo/content/moo").setTimeout(TO)
                        .go().await()
                        .assertStatus(OK)
                        .assertContent("content/moo")
                        .assertHeader(Headers.stringHeader("X-Req-Path"), "content/moo");

                harn.get("")
                        .setTimeout(TO)
                        .go().await().assertStatus(NOT_FOUND);

                harn.get("foo/x/y/z").setTimeout(TO)
                        .go().await().assertStatus(NOT_FOUND);

                harn.get("content/x").setTimeout(TO)
                        .go().await().assertStatus(NOT_FOUND);

            } catch (Throwable ex) {
                Exceptions.chuck(ex);
            }
        });
    }

    void withRunningServer(String base, ThrowingBiConsumer<Integer, TestHarness> bic) throws IOException, InterruptedException, Exception {
        SettingsBuilder sb = Settings.builder();
        int port = pf.findAvailableServerPort();
        System.setProperty("acteur.debug", "true");
        if (base != null) {
            sb.add(SETTINGS_KEY_BASE_PATH, base);
            sb.add(PORT, port);
        }
        Dependencies deps = new Dependencies(sb.build(), new ServerModule<>(App.class),
                new TestHarnessModule());
        try {
            bic.accept(port, deps.getInstance(TestHarness.class));
        } finally {
            deps.shutdown();
        }
    }

    static final class App extends Application {

        App() {
            add(P1.class);
            add(RootPage.class);
        }
    }

    @PathRegex("^\\/?$")
    static final class RootPage extends Page {

        RootPage() {
            add(RootActeur.class);
        }
    }

    static class RootActeur extends Acteur {

        @Inject
        RootActeur(HttpEvent evt) {
            ok("root: " + evt.path());
        }
    }

    @Path({
        "content/*",
        "content/*/*"
    })
    static final class P1 extends Page {

        @Inject
        P1(ActeurFactory f) {
            add(PathA.class);
        }
    }

    static final class PathA extends Acteur {

        @Inject
        PathA(HttpEvent evt) {
            add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
            ok(evt.path().toString());
        }
    }

    static final class HomeA extends Acteur {

        @Inject
        HomeA() {
            add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
            ok("This is the home page.\n");
        }
    }
}
