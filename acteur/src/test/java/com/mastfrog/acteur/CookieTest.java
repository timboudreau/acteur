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
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_ALLOW;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.mastfrog.acteur.headers.Headers.ACCESS_CONTROL_MAX_AGE;
import static com.mastfrog.acteur.headers.Headers.COOKIE_B;
import static com.mastfrog.acteur.headers.Headers.SET_COOKIE_B;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.util.collections.StringObjectMap;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({HttpTestHarnessModule.class, CTM.class, SilentRequestLogger.class})
public class CookieTest {

    @Test
    @Timeout(60)
    public void testOneCookie(HttpHarness harn) throws Throwable {
        HttpResponse<String> resp = harn.get("one").applyingAssertions(a -> a.assertHasHeader(SET_COOKIE_B)
                .assertBody("Set one cookie")).assertAllSucceeded().get();
        resp.headers().allValues(SET_COOKIE_B.toString());
        List<Cookie> cookies = cookies(resp);
        assertTrue(cookies.iterator().hasNext(), "No cookies found");
        assertEquals(1, cookies.size());
        assertEquals(cookies.get(0).name(), "hey");
        assertEquals(cookies.get(0).value(), "you");
    }

    private static List<Cookie> cookies(HttpResponse<?> resp) {
        List<Cookie> cookies = new ArrayList<>();
        for (String h : resp.headers().allValues(SET_COOKIE_B.toString())) {
            cookies.add(SET_COOKIE_B.convert(h));
        }
        return cookies;
    }

    @Test
    @Timeout(60)
    public void testCookieEncoding(HttpHarness harn) throws Throwable {
        HttpResponse<String> resp = harn.get("space").applyingAssertions(a -> a.assertHasHeader(SET_COOKIE_B)
                .assertBody("Set encodable cookie")).assertAllSucceeded().get();

        List<Cookie> cookies = cookies(resp);
        assertTrue(cookies.iterator().hasNext(), "No cookies found");
        assertEquals(1, cookies.size());
        assertEquals(cookies.get(0).name(), "name");
        assertEquals(cookies.get(0).value(), "Joe Blow");
    }

    @Test
    @Timeout(60)
    public void testMultipleCookies(HttpHarness harn) throws Throwable {

        HttpResponse<String> resp = harn.get("multi").applyingAssertions(a -> a.assertHasHeader(SET_COOKIE_B)
                .assertBody("Set three cookies")).assertAllSucceeded().get();

        List<Cookie> all = cookies(resp);
        assertTrue(all.iterator().hasNext(), "No cookies found");
        assertEquals(3, all.size(), () -> "Should have gotten 3 cookies, not " + all.size());
        int ct = 0;
        StringBuilder errorMessage = new StringBuilder();
        for (Cookie ck : all) {
            errorMessage.append(ck.name()).append('=').append(ck.value()).append(", ");
            ct++;
        }
        BiConsumer<String, String> assertCookieValue = (name, val) -> {
            for (Cookie ck : all) {
                if (name.equals(ck.name()) && val.equals(ck.value())) {
                    return;
                }
            }
            fail("No cookie " + name + "=" + val + " in " + errorMessage);
        };

        assertCookieValue.accept("a", "hey");
        assertCookieValue.accept("b", "you");
        assertCookieValue.accept("c", "thing");

        Map<String, Object> m = harn.get("return")
                .setHeader(COOKIE_B, all.toArray(Cookie[]::new))
                .applyingAssertions(a -> a.assertOk().assertHasBody())
                .assertAllSucceeded().get(StringObjectMap.class);

        assertEquals(3, m.size(), "All cookies were not found: " + m.toString());
        assertEquals("hey", m.get("a"));
        assertEquals("you", m.get("b"));
        assertEquals("thing", m.get("c"));
    }

    @Test
    @Timeout(60)
    public void testCORSHandling(HttpHarness harn) throws Throwable {
        harn.options("one").applyingAssertions(
                a -> a.assertHasHeader(ACCESS_CONTROL_ALLOW)
                        .assertHasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS)
                        .assertHasHeader(ACCESS_CONTROL_ALLOW_ORIGIN)
                        .assertHasHeader(ACCESS_CONTROL_MAX_AGE))
                .assertAllSucceeded();
    }

    static final class CTM extends ServerModule<CTApp> {

        CTM() {
            super(CTApp.class, 2, 2, 3);
        }
    }

    private static class CTApp extends Application {

        CTApp() {
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
