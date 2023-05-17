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
package com.mastfrog.acteur.annotations;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Help;
import com.mastfrog.acteur.PutTest;
import com.mastfrog.acteur.SilentRequestLogger;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({SilentRequestLogger.class, HttpTestHarnessModule.class,
    GenericApplicationRuntimeTest.G.class})
public final class GenericApplicationRuntimeTest {

    @Test
    @Timeout(60)
    public void testWithNoParameters(HttpHarness harn) {
        harn.get("foo/bar").asserting(a -> {
            a.assertBadRequest();
            a.assertStatus(stat -> {
                return true;
            }).assertBody(bod -> {
                return "{\"error\":\"Must have at least one of 'wug', 'whee' as parameters\\n\"}".equals(bod);
            });
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testWithNonNumericParameter(HttpHarness harn) {
        harn.get("foo/bar?wug=wheeple").asserting(a -> {
            a.assertBadRequest();
            a.assertStatus(stat -> {
                return stat == 400;
            }).assertBody(bod -> {
                return "{\"error\":\"Parameter wug is not a legal number here: 'wheeple'\\n\"}".equals(bod);
            });
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testWithBannedParameter(HttpHarness harn) {
        harn.get("foo/bar?wug=wheeple&glink=glorp").asserting(a -> {
            a.assertBadRequest();
            a.assertStatus(stat -> {
                return stat == 400;
            }).assertBody(bod -> {
                return "{\"error\":\"glink not allowed in parameters\\n\"}".equals(bod);
            });
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testGood(HttpHarness harn) {
        harn.get("foo/bar?wug=1234").asserting(a -> {
            a.assertOk().assertBody("Hello foo bar");
        }).assertAllSucceeded();
    }

    @Test
    @Timeout(60)
    public void testHelp(HttpHarness harn) {
        harn.get("help").asserting(a -> {
            a.assertOk()
                    .assertBodyContains("foobley")
                    .assertBodyContains("barbley")
                    .assertBodyContains("I'm a wookie") //                    .assertBodyContains("You are not a wookie")
                    ;

        }).assertAllSucceeded();
    }

    static class G extends GenericApplicationModule<A> {

        public G(Settings settings) {
            super(settings, A.class, new Class<?>[0]);
        }

    }

    @Help
    static class A extends GenericApplication {

        public A() {
        }

    }

}
