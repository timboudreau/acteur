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

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.mime.MimeType;
import com.mastfrog.util.streams.Streams;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests that if we set the basepath setting, so the application is "mounted" on
 * some subpath, that paths are interpreted correctly (acteurs do not see the
 * base path as part of the url path, and the application does not serve paths
 * <i>above</i> the base path).
 *
 * @author Tim Boudreau
 */
@TestWith({HttpTestHarnessModule.class, BasePathTest.M.class, SilentRequestLogger.class})
public class BasePathTest {

    @Test
    @Timeout(30)
    public void testUnqualifiedPath(HttpHarness harn) {
        harn.get("foo").asserting(
                a -> a.assertOk().assertHeader("X-Req-Path", "/")
                        .assertBody("root:")).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testTrailingSlashPath(HttpHarness harn) {
        harn.get("foo/").asserting(
                a -> a.assertOk().assertHeader("X-Req-Path", "/")
                        .assertBody("root:")).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testLeadingAndTrailingSlashPath(HttpHarness harn) {
        harn.get("/foo/").asserting(
                a -> a.assertOk().assertHeader("X-Req-Path", "/")
                        .assertBody("root:")).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testLeadingSlashPath(HttpHarness harn) {
        // XXX the new jdk http test harness will always prepend the
        // slash
        harn.get("/foo").asserting(
                a -> a.assertOk().assertHeader("X-Req-Path", "/")
                        .assertBody("root:")).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testValidSubpath(HttpHarness harn) {
        harn.get("foo/content/moo").asserting(
                a -> a.assertOk().assertHeader("X-Req-Path", "content/moo")
                        .assertBody("content/moo")).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testEmptyPath(HttpHarness harn) {
        // Paths below the base path should always return not found.
        harn.get("").asserting(
                a -> a.assertNotFound()).assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testValidSubpathButAtTopLevel(HttpHarness harn) {
        harn.get("content/x").asserting(a -> a.assertNotFound())
                .assertAllSucceeded();
    }

    @Test
    @Timeout(30)
    public void testInvalidSubpath(HttpHarness harn) {
        harn.get("foo/x/y/z").asserting(a -> a.assertNotFound())
                .assertAllSucceeded();
    }

    static class M implements Module {

        @Override
        public void configure(Binder binder) {
            binder.install(new ServerModule<>(App.class));
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
            add(Headers.CONTENT_TYPE, MimeType.PLAIN_TEXT_UTF_8);
            ok(evt.path().toString());
        }
    }

    static final class HomeA extends Acteur {

        @Inject
        HomeA() {
            add(Headers.CONTENT_TYPE, MimeType.PLAIN_TEXT_UTF_8);
            ok("This is the home page.\n");
        }
    }

    private static PrintStream originalSystemOut;

    @BeforeEach
    public void enableDebug() {
        // This must be set - the test rely on the X-Req-Path header to
        // determine what path the server thinks it was responding to,
        // which we use to test path translation
        System.setProperty("acteur.debug", "true");
    }

    @BeforeAll
    public static void beforeAll() {
        // Acteur debug is very noisy, and we do not need noisy tests.
        System.setOut(Streams.nullPrintStream());
    }

    @AfterAll
    public static void afterAll() {
        if (originalSystemOut != null) {
            System.setOut(originalSystemOut);
        }
        System.setProperty("acteur.debug", "false");
    }
}
