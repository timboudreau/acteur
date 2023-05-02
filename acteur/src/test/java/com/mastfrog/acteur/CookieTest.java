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
import com.mastfrog.acteur.CookieTest.CTM;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.COOKIE_B;
import static com.mastfrog.acteur.headers.Headers.SET_COOKIE_B;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.collections.StringObjectMap;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, CTM.class, SilentRequestLogger.class})
@RunWith(GuiceRunner.class)
public class CookieTest {

    private final Duration timeout = Duration.ofMinutes(1);

    @Test
    public void testOneCookie(TestHarness harn) throws Throwable {
        CallResult res = harn.get("one").setTimeout(timeout).go().await().assertStatus(OK)
                .assertHasHeader(Headers.SET_COOKIE_B.name())
                .assertContent("Set one cookie");
        Iterable<Cookie> cookies = res.getHeaders(SET_COOKIE_B);
        assertTrue("No cookies found", cookies.iterator().hasNext());
        res.assertCookieValue("hey", "you");
    }

    @Test
    public void testCookieEncoding(TestHarness harn) throws Throwable {
        CallResult res = harn.get("space").setTimeout(timeout).go().await().assertStatus(OK)
                .assertHasHeader(Headers.SET_COOKIE_B.name())
                .assertContent("Set encodable cookie");
        Iterable<Cookie> cookies = res.getHeaders(SET_COOKIE_B);
        assertTrue("No cookies found", cookies.iterator().hasNext());
        res.assertCookieValue("name", "Joe Blow");
    }

    @Test
    public void testMultipleCookies(TestHarness harn) throws Throwable {
        CallResult res = harn.get("multi").go().await().assertStatus(OK)
                .assertHasHeader(Headers.SET_COOKIE_B.name())
                .assertContent("Set three cookies");
        Iterable<Cookie> cookies = res.getHeaders(SET_COOKIE_B);
        assertTrue("No cookies found", cookies.iterator().hasNext());
        int ct = 0;
        StringBuilder sb = new StringBuilder();
        List<Cookie> all = new ArrayList<>(3);
        for (Cookie ck : cookies) {
            all.add(ck);
            sb.append(ck.name()).append('=').append(ck.value()).append(", ");
            ct++;
        }
        assertEquals(sb.toString(), 3, ct);
        res.assertCookieValue("a", "hey");
        res.assertCookieValue("b", "you");
        res.assertCookieValue("c", "thing");

        Map<String, Object> m = harn.get("return")
                .setTimeout(timeout)
                .addHeader(COOKIE_B, all.toArray(new Cookie[0]))
                .go().await().assertStatus(OK)
                .content(StringObjectMap.class);
        assertEquals("All cookies were not found: " + m.toString(), 3, m.size());
        assertEquals("hey", m.get("a"));
        assertEquals("you", m.get("b"));
        assertEquals("thing", m.get("c"));
    }
    
    @Test
    public void testCORSHandling(TestHarness harn) throws Throwable {
        harn.options("one").setTimeout(timeout).go().await().assertHasHeader(Headers.ACCESS_CONTROL_ALLOW)
                .assertHasHeader(Headers.ACCESS_CONTROL_ALLOW_CREDENTIALS)
                .assertHasHeader(Headers.ACCESS_CONTROL_MAX_AGE)
                .assertHasHeader(Headers.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    static final class CTM extends ServerModule<CTApp> {

        CTM() {
            super(CTApp.class, 2, 2, 3);
        }
    }

    private static class CTApp extends Application {

        public CTApp() {
            add(OneCookiePage.class);
            add(MultiCookiePage.class);
            add(SpaceCookiePage.class);
            add(ReturnCookiesPage.class);
            super.enableDefaultCorsHandling();
        }

        @Path("/multi")
        static class MultiCookiePage extends Page {

            MultiCookiePage() {
                add(MultiCookieActeur.class);
            }
        }

        static class MultiCookieActeur extends Acteur {

            MultiCookieActeur() {
                DefaultCookie a = new DefaultCookie("a", "hey");
                DefaultCookie b = new DefaultCookie("b", "you");
                DefaultCookie c = new DefaultCookie("c", "thing");
                add(SET_COOKIE_B, a)
                        .add(SET_COOKIE_B, b)
                        .add(SET_COOKIE_B, c)
                        .ok("Set three cookies");
            }
        }

        @Path("/return")
        static class ReturnCookiesPage extends Page {

            ReturnCookiesPage() {
                add(ReturnCookiesActeur.class);
            }
        }

        static class ReturnCookiesActeur extends Acteur {

            @Inject
            ReturnCookiesActeur(HttpEvent evt) {
                Map<String, String> m = new HashMap<>();
                List<Cookie[]> l = evt.headers(COOKIE_B);
                for (Cookie[] c : l) {
                    for (Cookie ck : c) {
                        m.put(ck.name(), ck.value());
                    }
                }
                ok(m);
            }
        }

        @Path("/one")
        static class OneCookiePage extends Page {

            OneCookiePage() {
                add(OneCookieActeur.class);
            }
        }

        static class OneCookieActeur extends Acteur {

            OneCookieActeur() {
                DefaultCookie a = new DefaultCookie("hey", "you");
                add(SET_COOKIE_B, a);
                ok("Set one cookie");
            }
        }

        @Path("/space")
        static class SpaceCookiePage extends Page {

            SpaceCookiePage() {
                add(SpaceCookieActeur.class);
            }
        }

        static class SpaceCookieActeur extends Acteur {

            SpaceCookieActeur() {
                DefaultCookie a = new DefaultCookie("name", "Joe Blow");
                add(SET_COOKIE_B, a);
                ok("Set encodable cookie");
            }
        }
    }
}
