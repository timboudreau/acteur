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

import com.google.inject.util.Providers;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.POST;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.util.strings.Strings;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class PagePathAndMethodFilterTest {

    private final PagePathAndMethodFilter pgs = new PagePathAndMethodFilter();
    private static final Class<?>[] classes = new Class<?>[]{
        ExactPage.class, ExactNoLeadingSlash.class, ExactWithTrailingSlash.class,
        GlobPage.class, MonkeysPage.class, RegexPage.class, ExactRegexPage.class, ExactRegexPage2.class
    };

    @Test
    public void testInstanceMatching() {
        PagePathAndMethodFilter filter = new PagePathAndMethodFilter();
        filter.add(new PageWithInstanceActeurs());
        filter.add(new PageWithRegex());
        filter.add(new PageWithDecode());
        List<Object> l = filter.listFor(get("/"));
        assertNotNull(l);
        assertTrue(l.isEmpty(), Strings.join(',', l));

        l = filter.listFor(get("api/v3/foo"));
        assertOne(l, PageWithInstanceActeurs.class);

        l = filter.listFor(get("foo/23/bar"));
        assertOne(l, PageWithRegex.class);

        l = filter.listFor(get("foo/23a/bar"));
        assertNotNull(l);
        assertTrue(l.isEmpty());

        l = filter.listFor(post("api/v1/wiggles/bada-boom"));
        assertOne(l, PageWithDecode.class);

        l = filter.listFor(post("api/v1/wiggles/-bada-boom"));
        assertNotNull(l);
        assertTrue(l.isEmpty());

        // домен-продаётся
        String domainForSale = Strings.urlEncode("домен-продаётся");
        l = filter.listFor(post("api/v1/wiggles/" + domainForSale));
        assertOne(l, PageWithDecode.class);

        String mondoPath = "/api/v1/training/%D0%B4%D0%BE%D0%BC%D0%B5%D0%BD-%D0%BF%D1%80%D0%BE%D0%B4%D0%B0%D1%91%D1%82%D1%81%D1%8F";

        l = filter.listFor(post(mondoPath));
        assertOne(l, PageWithDecode.class);
    }

    @Test
    public void testDecodeWithAnnotation() {
        PagePathAndMethodFilter filter = new PagePathAndMethodFilter();
        filter.add(new PageWithInstanceActeurs());
        filter.add(new PageWithRegex());
        filter.add(new PageWithDecode());
        filter.add(PageWithAnnoDecode.class);
        List<Object> l = filter.listFor(post("hello/v1/wiggles/-bada-boom"));
        assertNotNull(l);
        assertTrue(l.isEmpty());

        l = filter.listFor(post("hello/v1/wiggles/gurble-whatzit"));
        assertNotNull(l);
        assertOne(l, PageWithAnnoDecode.class);

        l = filter.listFor(post("hello/v1/wiggles/" + Strings.urlEncode("домен-продаётся")));
        assertNotNull(l);
        assertOne(l, PageWithAnnoDecode.class);

        l = filter.listFor(post("hello/v1/wiggles/" + Strings.urlEncode("-домен-продаётся")));
        assertNotNull(l);
        assertTrue(l.isEmpty());

        l = filter.listFor(post("hello/v1/wiggles/bada-boom/wham"));
        assertNotNull(l);
        assertTrue(l.isEmpty());

        l = filter.listFor(post("whatevs"));
        assertNotNull(l);
        assertOne(l, PageWithAnnoDecode.class);
    }

    @Test
    public void testUnknownsAlwaysReturned() {
        PagePathAndMethodFilter filter = new PagePathAndMethodFilter();
        filter.add(new PageWithInstanceActeurs());
        filter.add(new PageWithRegex());
        filter.add(new PageWithDecode());
        filter.add(new MysteryPage());
        List<Object> l = filter.listFor(get("/"));
        assertNotNull(l);
        assertOne(l, MysteryPage.class);

        l = filter.listFor(get("api/v3/foo"));
        assertNotNull(l);
        Assertions.assertEquals(2, l.size());
        assertTrue(l.stream().anyMatch(i -> i instanceof PageWithInstanceActeurs));
    }

    private Object assertOne(List<Object> l, Class<?> type) {
        assertNotNull(l);
        assertFalse(l.isEmpty());
        Assertions.assertEquals(1, l.size(), Strings.join(',', l));
        assertTrue(type.isInstance(l.iterator().next()) || type == l.iterator().next(), l.iterator().next() + "");
        return l.iterator().next();
    }

    private HttpRequest get(String url) {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url);
    }

    private HttpRequest post(String url) {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, url);
    }

    static final class PageWithInstanceActeurs extends Page {

        PageWithInstanceActeurs() {
            add(new ActeurFactory.MatchMethods(Providers.of(null), true, UTF_8, Method.GET, Method.POST));
            add(new ActeurFactory.ExactMatchPath(Providers.of(null), "api/v3/foo", false));
        }
    }

    static final class PageWithRegex extends Page {

        PageWithRegex() {
            PathPatterns pp = new PathPatterns();
            add(new ActeurFactory.MatchMethods(Providers.of(null), true, UTF_8, Method.GET, Method.POST));
            add(new ActeurFactory.MatchPath(Providers.of(null), pp, false, "^foo\\/\\d+\\/bar"));
        }
    }
    private static final String INTL_PATTERN = "^api\\/v1\\/[^\\/]+\\/" + "[[\\p{IsAlphabetic}&&[\\p{javaLowerCase}]]\\d]"
            + "[[\\p{IsAlphabetic}&&[\\p{javaLowerCase}]]\\d\\-]{3,30}"
            + "[[\\p{IsAlphabetic}&&[\\p{javaLowerCase}]]\\d]" + "$";

    private static final String INTL_PATTERN_B = "^hello\\/v1\\/[^\\/]+\\/" + "[[\\p{IsAlphabetic}&&[\\p{javaLowerCase}]]\\d]"
            + "[[\\p{IsAlphabetic}&&[\\p{javaLowerCase}]]\\d\\-]{3,30}"
            + "[[\\p{IsAlphabetic}&&[\\p{javaLowerCase}]]\\d]" + "$";

    static final class PageWithDecode extends Page {

        PageWithDecode() {
            PathPatterns pp = new PathPatterns();
            add(new ActeurFactory.MatchMethods(Providers.of(null), true, UTF_8, Method.GET, Method.POST));
            add(new ActeurFactory.MatchPath(Providers.of(null), pp, true, INTL_PATTERN));
        }
    }

    @Methods(POST)
    @PathRegex(value = {INTL_PATTERN_B, "^whatevs$"}, decode = true)
    static final class PageWithAnnoDecode extends Page {

    }

    static final class MysteryPage extends Page {

    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        for (Class<?> c : classes) {
            Class<? extends Page> pg = (Class<? extends Page>) c;
            pgs.add(pg);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTypeMatching() {
        for (Class<?> c : classes) {
            Class<? extends Page> pg = (Class<? extends Page>) c;
            ShouldMatch shoulds = pg.getAnnotation(ShouldMatch.class);
            if (shoulds != null) {
                Method mth = pg.getAnnotation(Methods.class).value()[0];
                for (String uri : shoulds.value()) {
                    HttpRequest req = req(mth, uri);
                    assertTrue(pgs.match(req), mth + " " + uri + " should be matched but isn't");
                }
            }
            ShouldNotMatch shouldnts = pg.getAnnotation(ShouldNotMatch.class);
            if (shouldnts != null) {
                Method mth = pg.getAnnotation(Methods.class).value()[0];
                for (String uri : shouldnts.value()) {
                    HttpRequest req = req(mth, uri);
                    assertFalse(pgs.match(req), mth + " " + uri + " should be matched but isn't");
                }
            }
        }
    }

    private HttpRequest req(Method mth, String uri) {
        if (uri.charAt(0) != '/') {
            uri = "/" + uri;
        }
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(mth.name()), uri);
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
