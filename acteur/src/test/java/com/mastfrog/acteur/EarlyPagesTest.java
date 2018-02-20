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

import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.POST;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class EarlyPagesTest {

    private final PagePathAndMethodFilter pgs = new PagePathAndMethodFilter();
    private static final Class<?>[] classes = new Class<?>[]{
        ExactPage.class, ExactNoLeadingSlash.class, ExactWithTrailingSlash.class,
        GlobPage.class, MonkeysPage.class, RegexPage.class, ExactRegexPage.class, ExactRegexPage2.class
    };

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        for (Class<?> c : classes) {
            Class<? extends Page> pg = (Class<? extends Page>) c;
            pgs.add(pg);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSomeMethod() {
        for (Class<?> c : classes) {
            Class<? extends Page> pg = (Class<? extends Page>) c;
            ShouldMatch shoulds = pg.getAnnotation(ShouldMatch.class);
            if (shoulds != null) {
                Method mth = pg.getAnnotation(Methods.class).value()[0];
                for (String uri : shoulds.value()) {
                    HttpRequest req = req(mth, uri);
                    assertTrue(mth + " " + uri + " should be matched but isn't", pgs.match(req));
                }
            }
            ShouldNotMatch shouldntss = pg.getAnnotation(ShouldNotMatch.class);
            if (shouldntss != null) {
                Method mth = pg.getAnnotation(Methods.class).value()[0];
                for (String uri : shouldntss.value()) {
                    HttpRequest req = req(mth, uri);
                    assertFalse(mth + " " + uri + " should be matched but isn't", pgs.match(req));
                }
            }
        }
    }

    private HttpRequest req(Method mth, String uri) {
        if (uri.charAt(0) != '/') {
            uri = "/" + uri;
        }
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(mth.name()), uri);
        return req;
    }

    @Methods(GET)
    @Path("/foo/bar")
    @ShouldMatch({"foo/bar", "/foo/bar", "foo/bar/", "/foo/bar/", "/foo/bar?skiddoo=23"})
    @ShouldNotMatch("/foo/bar/baz")
    static final class ExactPage {

    }

    @Methods(GET)
    @Path("bar/baz/whee")
    static final class ExactNoLeadingSlash {

    }

    @Methods(GET)
    @Path("mo/fun/now/")
    @ShouldNotMatch("mo/fun/now/dear")
    @ShouldMatch({"mo/fun/now", "/mo/fun/now", "/mo/fun/now/"})
    static final class ExactWithTrailingSlash {

    }

    @Methods(PUT)
    @PathRegex("wig\\/wham\\/bam")
    @ShouldMatch({"wig/wham/bam", "/wig/wham/bam", "/wig/wham/bam/" /*, "/wig/wham/bam/boom/bar" */})
    static final class ExactRegexPage {

    }

    @Methods(PUT)
    @PathRegex("^vig\\/vham\\/vam")
    @ShouldMatch({"vig/vham/vam", "/vig/vham/vam", "/vig/vham/vam/"/* , "/vig/vham/vam/boom/bar" */})
    static final class ExactRegexPage2 {

    }

    @Methods(POST)
    @Path("/api/v2/things/*/new")
    @ShouldNotMatch({"api/things", "api/v2/things/are/new/but/long", "/api/v2/things/are/newer"})
    @ShouldMatch({"api/v2/things/abc/new", "api/v2/things/asdlfhasdljfalsdfahsdkljfhlasdf/new", "/api/v2/things/are/new"})
    static final class GlobPage {

    }

    @Methods(POST)
    @Path("big/monkeys")
    @ShouldMatch({"big/monkeys", "/big/monkeys"})
    @ShouldNotMatch({"big/bad/monkeys", "big/monkeys/are/bad"})
    static final class MonkeysPage {

    }

    @Methods(POST)
    @Path("trailing/glob/*")
    @ShouldMatch({"/trailing/glob/gods"})
    @ShouldNotMatch({"trailing/glob", "trailing/", "/trailing", "/trailing/glob/for/you"})
    static final class TrailingGLob {

    }

    @Methods(PUT)
    @PathRegex("hey\\/[0-9a-f]{3,5}\\/you$")
    @ShouldMatch({"/hey/0f3a2/you", "/hey/0a0/you"})
    @ShouldNotMatch({"/hey", "/hey/zzz/you", "/hey/00/you", "/hey/0f3a/you/are/here"})
    static final class RegexPage {

    }

    @Retention(RUNTIME)
    @Target(TYPE)
    @interface ShouldMatch {

        String[] value();
    }

    @Retention(RUNTIME)
    @Target(TYPE)
    @interface ShouldNotMatch {

        String[] value();
    }

}
