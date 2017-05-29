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

import com.mastfrog.acteur.CookieTest.CTM;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.SET_COOKIE_B;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, CTM.class})
@RunWith(GuiceRunner.class)
public class CookieTest {
/*
    @Test
    public void testOneCookie(TestHarness harn) throws Exception, Throwable {
        CallResult res = harn.get("one").go().await().assertStatus(OK)
                .assertHasHeader(Headers.SET_COOKIE_B.name())
                .assertContent("Set one cookie");
        Iterable<Cookie> cookies = res.getHeaders(SET_COOKIE_B);
        System.out.println("COOKIES: " + cookies);
        assertTrue("No cookies found", cookies.iterator().hasNext());
        res.assertCookieValue("hey", "you");
        for (Cookie ck : cookies) {
            System.out.println("COOKIE: " + ck);
        }
    }
    @Test
    public void testCookieEncoding(TestHarness harn) throws Exception, Throwable {
        CallResult res = harn.get("space").go().await().assertStatus(OK)
                .assertHasHeader(Headers.SET_COOKIE_B.name())
                .assertContent("Set encodable cookie");
        Iterable<Cookie> cookies = res.getHeaders(SET_COOKIE_B);
        System.out.println("COOKIES: " + cookies);
        assertTrue("No cookies found", cookies.iterator().hasNext());
        res.assertCookieValue("name", "Joe Blow");
        for (Cookie ck : cookies) {
            System.out.println("COOKIE: " + ck);
        }
    }
*/

    @Test
    public void testMultipleCookies(TestHarness harn) throws Exception, Throwable {
        CallResult res = harn.get("multi").go().await().assertStatus(OK)
                .assertHasHeader(Headers.SET_COOKIE_B.name())
                .assertContent("Set three cookies");
        Iterable<Cookie> cookies = res.getHeaders(SET_COOKIE_B);
        System.out.println("COOKIES: " + cookies);
        assertTrue("No cookies found", cookies.iterator().hasNext());
        int ct = 0;
        StringBuilder sb = new StringBuilder();
        for (Cookie ck : cookies) {
            System.out.println("COOKIE: " + ck);
            sb.append(ck.name()).append('=').append(ck.value()).append(", ");
            ct++;
        }
        assertEquals(sb.toString(), 3, ct);
        res.assertCookieValue("a", "hey");
        res.assertCookieValue("b", "you");
        res.assertCookieValue("c", "thing");
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
