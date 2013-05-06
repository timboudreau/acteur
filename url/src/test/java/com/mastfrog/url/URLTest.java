package com.mastfrog.url;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import org.junit.Test;
import static org.junit.Assert.*;
import org.netbeans.validation.api.Problems;

/**
 *
 * @author Tim Boudreau
 */
public class URLTest {
    
    @Test
    public void testHostEquality() {
        Label l1 = new Label("one");
        Label l2 = new Label("one");
        Label l3 = new Label("ONE");
        Label l4 = new Label("oNe");
        assertEquals(l1, l2);
        assertEquals(l2, l3);
        assertEquals(l3, l4);
        assertEquals(l1, l4);
        assertEquals(l2, l4);
        Host one = Host.parse("WWW.Test.CoM");
        Host two = Host.parse("www.test.com");
        assertEquals(one, two);
    }

    @Test
    public void testNormalization() {
        URLBuilder builder = new URLBuilder();
        builder.setProtocol(Protocols.HTTP);
        builder.setHost(Host.builder().add("com").add("timboudreau").add("www").create());
        builder.setPath(Path.builder().add("path").add("to").add("stuff.html").create());
        builder.setUserName("tim");
        builder.setPassword("monkey");
        builder.setQuery((ParsedParameters) Parameters.builder().add(new ParametersElement("foo", "bar")).add(new ParametersElement("moo", "goo")).create());
        URL url = builder.create();
        assertNotNull(url);

        builder = new URLBuilder();
        builder.setProtocol(Protocols.HTTP);
        builder.setHost("WWW.TIMBOUDREAU.COM");
        builder.setPath("path/to/stuff.html");
        builder.setQuery((ParsedParameters) Parameters.builder().add(new ParametersElement("foo", "bar")).add(new ParametersElement("moo", "goo")).create());
        builder.setUserName("tim");
        builder.setPassword("monkey");
        URL url2 = builder.create();
        assertNotNull(url2);
        assertEquals(url, url2);
        assertEquals(url.toString(), url2.toString());
        assertEquals("http://tim:monkey@www.timboudreau.com/path/to/stuff.html?moo=goo&foo=bar", url.toString());
        assertTrue(url2.isValid());
        assertFalse(url2.isSecure());
    }

    @Test
    public void testInferFileReference() {
        Path a = Path.parse("com/foo/index.html");
        Path b = Path.parse("com/foo/");
        assertTrue(a.isProbableFileReference());
        assertFalse(b.isProbableFileReference());

        URLBuilder builder = new URLBuilder();
        builder = new URLBuilder();
        builder.setProtocol(Protocols.HTTP);
        builder.setHost("WWW.TIMBOUDREAU.COM");
        builder.setPath("path/to/stuff.html");
        assertTrue(builder.create().getPath().isProbableFileReference());
        assertFalse(builder.create().getPath().toString().endsWith("/"));
        assertFalse(builder.create().toString().endsWith("/"));

        builder = new URLBuilder();
        builder.setProtocol(Protocols.HTTP);
        builder.setHost("WWW.TIMBOUDREAU.COM");
        builder.setPath("path/to/stuff");
        assertFalse(builder.create().getPath().isProbableFileReference());
    }

    @Test
    public void testValidity() {
        URLBuilder b = new URLBuilder();
        b.setProtocol("foo");
        b.setHost("-foo.com");
        assertFalse(b.create().isValid());
        assertFalse(b.create().isKnownProtocol());
    }

    @Test
    public void testPasswordValidity() {
        URLBuilder b = new URLBuilder();
        b.setProtocol(Protocols.HTTPS);
        assertFalse(b.create().isValid());
        b.setHost(Host.builder().add("com").add("timboudreau").add("www").create());
        assertTrue(b.create().isValid());
        b.setUserName("foo");
        assertTrue(b.create().isValid());
        b.setPassword("illegal password");
        assertFalse(b.create().isValid());
        assertTrue(b.create().isSecure());
        assertTrue(b.create().isKnownProtocol());
    }

    @Test
    public void testIllegalCharactersValidity() {
        URLBuilder b = new URLBuilder();
        b.setProtocol(Protocols.HTTPS);
        b.setHost("foo.テテ.טעסט");
        assertFalse(b.create().isValid());
        assertNotNull(b.create().getInvalidComponent());
        assertTrue(b.create().getInvalidComponent() instanceof Label);
    }

    @Test
    public void testHighAsciiCharactersInPath() {
        PathElement el = new PathElement("foo");
        assertTrue(el.isValid());
        el = new PathElement(createHighAsciiString());
        assertTrue(el.isValid());
    }

    @Test
    public void testHighAsciiCharactersInLabel() {
        Label lbl = new Label("foo");
        assertTrue(lbl.isValid());
        lbl = new Label(createHighAsciiString());
        assertFalse(lbl.isValid());
    }

    private static String createHighAsciiString() {
        return createHighAsciiString(5);
    }

    private static String createHighAsciiString(int len) {
        char[] c = new char[5];
        char b = 128;
        for (int i = 0; i < c.length; i++) {
            b++;
            c[i] = b;
            assertTrue(URLBuilder.isEncodableInLatin1(c[i]));
        }
        return new String(c);
    }

    @Test
    public void testHighAsciiCharactersValidity() {
        URLBuilder b = new URLBuilder();
        b.setProtocol(Protocols.HTTP);
        b.setHost(Host.builder().add("com").add("foo").create());
        assertTrue(b.create().isValid());
        b = new URLBuilder(Protocols.HTTP);
        b.setHost(Host.builder().add("com").add("foo").create());
        b.setPath(Path.builder().add("foo").add(createHighAsciiString()).create());
        assertNull(b.create().getInvalidComponent());
        for (URLComponent c : b.create().allComponents()) {
            assertTrue(c + " (" + c.getClass().getName() + ")", c.isValid());
        }
        assertTrue(b.create().getPath().isValid());
        assertTrue("Invalid comp " + b.create().getInvalidComponent(), b.create().isValid());
    }

    @Test
    public void testCharactersAbove256AreInvalid() {
        URLBuilder b = new URLBuilder();
        b.setProtocol(Protocols.HTTP);
        char[] c = new char[5];
        for (int i = 0; i < c.length; i++) {
            c[i] = (char) (256 + i);
        }
        b.setHost(Host.builder().add("com").add(new String(c)).create());
        assertFalse(b.create().isValid());
    }

    @Test
    public void testHostParse() {
        Host h = Host.parse("COM.FOO.BAR");
        assertNotNull(h);
        assertEquals("com.foo.bar", h.toString());
        assertTrue(h.isValid());

        h = Host.parse("C M.FOO.BAR");
        assertNotNull(h);
        assertEquals("c m.foo.bar", h.toString());
        assertFalse(h.isValid());

        h = Host.parse("COM..foo...bar");
        assertEquals("com..foo...bar", h.toString());
        assertFalse(h.isValid());

    }

    @Test
    public void testPathConversion() {
        Path p1 = Path.parse("com/foo/bar/baz/index.html");
        Path p2 = Path.parse("com/foo/bar/baz");
        Path p3 = Path.parse("com/foo/bar/baz");
        assertFalse("".equals(p1.toString()));
        assertFalse("".equals(p2.toString()));
        assertFalse("".equals(p3.toString()));
        assertTrue(p2.isParentOf(p1));
        assertTrue(p1.isChildOf(p2));
        assertFalse(p2 + " should not be a child of " + p1, p2.isChildOf(p1));
        assertFalse(p1 + " should not be a parent of " + p2, p1.isParentOf(p2));
        assertEquals(p2, p1.getParentPath());

        assertTrue(p3 + " should be a parent of " + p1, p3.isParentOf(p1));
        assertTrue(p1 + " should be a child of " + p3, p1.isChildOf(p3));
        assertFalse(p3 + " should not be a child of " + p1, p3.isChildOf(p1));
        assertFalse(p1 + " should not be a parent of " + p3, p1.isParentOf(p3));
        assertEquals(p3, p1.getParentPath());
    }

    @Test
    public void testUrlConversion() throws MalformedURLException {
        java.net.URL url = url("http://timboudreau.com/stuff/index.html?bar=baz;foo=bar#anchor");
        URL real = URL.fromJavaUrl(url);
        assertEquals(url.toString(), real.toString());

        url = url("http://tim:password@timboudreau.com/stuff/index.html?bar=baz;foo=bar#anchor");
        real = URL.fromJavaUrl(url);
        assertEquals("Expected\n;" + url + " but got " + real, url.toString(), real.toString());
    }

    @Test
    public void testUnescape2() {
        StringBuilder sb = new StringBuilder();
        for (char c = 0; c < 5; c++) {
            sb.append(c);
        }
        String unescaped = sb.toString();
        String escaped = URLBuilder.escape(unescaped);
        String re_unescaped = URLBuilder.unescape(escaped);
        assertEquals(unescaped, re_unescaped);
    }

    @Test
    public void testUnescape3() {
        StringBuilder sb = new StringBuilder();
        for (char c = 5; c < 25; c++) {
            sb.append(c);
        }
        String unescaped = sb.toString();
        String escaped = URLBuilder.escape(unescaped);
        String re_unescaped = URLBuilder.unescape(escaped);
        assertEquals(unescaped, re_unescaped);
    }

    @Test
    public void testUnescapeMangled() {
        String mangled = "x%20%%y%%20zqr%20hello%20world%0g52rp";
        String expect = "x %%y% zqr hello world%0g52rp";
        assertEquals(expect, URLBuilder.unescape(mangled));
    }

    @Test
    public void testParseInvalidURLs() throws MalformedURLException {
        test("foo/bar/baz/");
        test("foo/bar/baz/" + URLBuilder.escape(")*(&#@FAIUSHFH;()@##") + "/");
    }

    @Test
    public void testParse() throws Exception {
        test("http://www.sun.com/");
        test("http://url.timboudreau.com/");
        test("http://url.timboudreau.com/stuff/");
        test("http://url.timboudreau.com/stuff/index.html");
        test("http://url.timboudreau.com/stuff/index.html#anchor");

        test("http://url.timboudreau.com/stuff/index.html?bar=baz;foo=bar");
//        test ("http://url.timboudreau.com/stuff/index.html?bar=baz;foo=bar;");
        test("http://url.timboudreau.com/stuff/index.html?bar=baz;foo=bar#anchor");

        test("http://url.timboudreau.com/stuff/index.html?bar=baz&foo=bar");
//        test ("http://url.timboudreau.com/stuff/index.html?bar=baz&foo=bar&");
        test("http://url.timboudreau.com/stuff/index.html?bar=baz&foo=bar#anchor");

        test("http://url.timboudreau.com/stuff/#anchor");
    }

    @Test
    public void testParseWithCredentials() throws Exception {
        test("http://tim:password@url.timboudreau.com/");
        test("http://tim:password@url.timboudreau.com/stuff/");
        test("http://tim:password@url.timboudreau.com/stuff/index.html");
        test("http://tim:password@url.timboudreau.com/stuff/index.html#anchor");
        test("http://tim:password@url.timboudreau.com/stuff/index.html?bar=baz;foo=bar#anchor");
//        test ("http://tim:password@url.timboudreau.com/stuff/index.html?bar=baz;foo=bar;#anchor");
        test("http://tim:password@url.timboudreau.com/stuff/index.html?bar=baz;foo=bar#anchor");
        test("http://tim:password@url.timboudreau.com/stuff/#anchor");
    }

    @Test
    public void testParseParameters() {
        ParsedParameters p;
        ParametersElement pe = ParametersElement.parse("foo=bar");
        assertEquals("foo=bar", pe.toString());
        pe = ParametersElement.parse("bar=baz");
        assertEquals("bar=baz", pe.toString());
        p = (ParsedParameters) Parameters.parse("foo=bar;bar=baz");
        p = (ParsedParameters) Parameters.parse("");
        assertNull(p);
        p = (ParsedParameters) Parameters.parse("=bar");
        assertNull("Key should be null but is " + p.getElements()[0].getKey(), p.getElements()[0].getKey());
        assertEquals("bar", p.getElements()[0].getValue());
        assertEquals("Got '" + p.toString() + "'", "?=bar", p.toString());
        p = (ParsedParameters) Parameters.parse("foo");
        assertEquals("?foo", p.toString());
        assertEquals("foo", p.getElements()[0].getKey());
        assertNull(p.getElements()[0].getValue());
    }

    @Test
    public void testHostParents() {
        Host h = Host.parse("www.timboudreau.com");
        assertEquals(Host.parse("timboudreau.com"), h.getParentDomain());

        Label[] l = h.getLabels();
        assertEquals(new Label("com"), l[0]);
        assertEquals(new Label("timboudreau"), l[1]);
        assertEquals(new Label("www"), l[2]);
        assertEquals(new Label("com"), h.getTopLevelDomain());
        assertEquals(new Label("timboudreau"), h.getDomain());
    }

    @Test
    public void testSameDomain() {
        Host h = Host.parse("weblogs.java.net");
        assertTrue(h.isDomain("java.net"));
        assertTrue(h.isDomain("weblogs.java.net"));
        assertFalse(h.isDomain("bubble.java.net"));
        assertFalse(h.isDomain("java.com"));
    }

    @Test
    public void testAddedLabelsAreUsed() {
        URLBuilder builder = URL.builder(Protocols.HTTP);
        builder.addDomain("www");
        builder.addDomain("goofball");
        builder.addDomain("com");
        assertEquals("http://www.goofball.com/", builder.create().toString());
    }

    @Test
    public void testValidation() {
        URLBuilder builder = URL.builder(Protocols.HTTP);
        builder.addDomain("www");
        builder.addDomain("goofball");
        builder.addDomain("com");
        assertTrue(builder.create().getHost().isValid());

        builder = URL.builder(Protocols.HTTP);
        builder.addDomain("www");
        builder.addDomain("goofball");
        builder.addDomain("com");

        Host h = Host.parse("x%20%%y%%20zqr%20hello%20world%0g52rp.foo.com");
        assertFalse(h.isValid());
        assertNotNull(h.getProblems());
        assertTrue(h.getProblems().hasFatal());

        builder = URL.builder(Protocols.HTTP).setHost(h).addPathElement("foo").addPathElement("bar");
        assertNotNull(builder.create().getProblems());
        assertTrue(builder.create().getProblems().hasFatal());
        assertFalse(builder.create().isValid());

        builder = URL.builder(Protocols.HTTP).setHost(Host.parse("..wwwaa ..hoo.com")).addPathElement("stuff");
        assertNotNull(builder.create().getProblems());
        assertTrue(builder.create().getProblems().hasFatal());
        assertFalse(builder.create().isValid());
    }

    @Test
    public void testBadPathParsing() {
        String shouldKeepTrailingSlash = "bad/stuff/";
        Path p = Path.parse(shouldKeepTrailingSlash);
        assertTrue(p.toString().endsWith("/"));

        String bad = "bad///stuff";
        p = Path.parse(bad);
        assertEquals(bad, p.toString());

        p = Path.parse("relative/path/../../stuff/");
        assertEquals("stuff/", p.normalize().toString());

        p = Path.parse("local/path/./././stuff");
        assertEquals("local/path/stuff", p.normalize().toString());

        p = Path.parse("local/path/./././stuff/");
        assertEquals("local/path/stuff/", p.normalize().toString());

        p = Path.parse("bad/path/../../../../stuff");
        assertFalse(p.normalize().isValid());
        assertEquals("bad/path/../../../../stuff", p.normalize().toString());
    }

    @Test
    public void testLocalhost() {
        Host a = Host.parse("");
        Host b = Host.parse("localhost");
        Host c = Host.parse("127.0.0.1");
        assertTrue(a.isLocalhost());
        assertTrue(b.isLocalhost());
        assertTrue(c.isLocalhost());
        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a, c);

        URL u1 = URL.parse("file:///x/y/z.txt");
        URL u2 = URL.parse("file://localhost/x/y/z.txt");
//        assertEquals (u1.toString(), u2.toString());
        assertEquals(u1, u2);

        u1 = URL.parse("file:///c:/WINDOWS/clock.avi");
        assertEquals("file:///c:/WINDOWS/clock.avi", u1.toString());
        assertNotNull(u1.getHost());
        assertTrue(u1.getHost().isLocalhost());
        assertEquals("c:/WINDOWS/clock.avi", u1.getPath().toString());
    }

    @Test
    public void testParseFileURLs() throws Exception {
        test("http://foo.com/Users/tim/someFile.html?bar=baz;foo=bar#stuff");
        test("file:///Users/tim/#stuff");
        test("file:///Users/tim/someFile.html#stuff");

        URL url = URL.parse("file://somehost/path/to/stuff");
        assertNotNull(url.getHost());
        assertEquals("somehost", url.getHost().toString());

    }

    private void test(String urlString) throws MalformedURLException {
        URL url = URL.parse(urlString);
        assertTrue("Expected\n" + urlString + " but got \n" + url, urlString.equals(url.toString()));

        try {
            java.net.URL u = url(urlString);
            String uString = u.toExternalForm();
            if (uString.startsWith("file:/") && !uString.startsWith("file:///")) {
                uString = "file:///" + uString.substring(6);
            }
            URL real = URL.fromJavaUrl(u);
            assertEquals("Expected\n'" + uString + "' but got \n'" + real + "'", uString, real.toString());
        } catch (MalformedURLException mue) {
        }
    }

    private java.net.URL url(String s) throws MalformedURLException {
        return new java.net.URL(s);
    }

    @Test
    public void testUnescape() {
        assertEquals("hello world", URLBuilder.unescape("hello%20world"));
        assertEquals(" ", URLBuilder.unescape("%20"));

        StringBuilder sb = new StringBuilder();
        for (char c = 0; c < 255; c++) {
            if (c == 25) {
                continue;
            }
            sb.append(c);
        }
        String unescaped = sb.toString();
        String escaped = URLBuilder.escape(unescaped);
        String re_unescaped = URLBuilder.unescape(escaped);
        assertEquals(examine(unescaped, re_unescaped), unescaped, re_unescaped);
    }

    private String examine(String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (a.length() != b.length()) {
            sb.append("Lengths different: ").append(a.length()).append(",").append(b.length());
        }
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                sb.append("\nMismatch at ").append(i).append(": ").append(a.charAt(i)).append(",").append(b.charAt(i));
            }
        }
        sb.append("\n").append(a);
        sb.append("\n").append(b);
        sb.append("\n");
        return sb.toString();
    }

    @Test
    public void testDifferentiateIPandNot() {
        URL url = URL.parse("http://127.0.0.1:8080/foo.txt");
        Host h = url.getHost();
        assertTrue(h.isIpAddress());
        assertNull("Domain should be null but is " + h.getDomain(), h.getDomain());

        url = URL.parse("http://foo.com:8080/foo.txt");
        h = url.getHost();
        assertFalse(h.isIpAddress());
        assertNotNull(h.getDomain());
    }

    @Test
    public void testAccuracy() {
        testUrlToString("http://127.0.0.1:8080/?kind=anything&recipient=moe%40foo.com&url=http://food.com/food.com",
                "http://127.0.0.1:8080/?kind=anything&recipient=moe@foo.com&url=http://food.com/food.com");
        //should always append a trailing slash to host-only URLs
        testUrlToString("http://food.com/", "http://food.com");
        //host only URLs w/ trailing slash should not be altered
        testUrlToString("http://food.com/");
        testUrlToString("http://food.com/boo/");
        testUrlToString("http://food.com/boo/bar");
        //Ensure parameters parsing creates something reproducible
        testUrlToString("http://food.com/boo/foo.html?q=x?a=3");
        testUrlToString("http://www.quirksmode.org/css/textoverflow.html");
//        testUrlToString("http://stackoverflow.com/questions/868288/getting%2dthe%2dvisible%2drect%2dof%2dan%2duiscrollviews%2dcontent", "http://stackoverflow.com/questions/868288/getting-the-visible-rect-of-an-uiscrollviews-content");
        testUrlToString("http://stackoverflow.com/questions/868288/getting-the-visible-rect-of-an-uiscrollviews-content", "http://stackoverflow.com/questions/868288/getting-the-visible-rect-of-an-uiscrollviews-content");
        testUrlToString("http://www.google.com/search?hl=en&client=safari&rls=en&q=javascript+get+bounding+rectangle+of+the+window&aq=f&aqi=&aql=&oq=&gs%5frfai=", "http://www.google.com/search?hl=en&client=safari&rls=en&q=javascript+get+bounding+rectangle+of+the+window&aq=f&aqi=&aql=&oq=&gs_rfai=");
        testUrlToString("http://www.p01.org/releases/Drawing%5flines%5fin%5fJavaScript/", "http://www.p01.org/releases/Drawing_lines_in_JavaScript/");
        testUrlToString("http://www.p01.org/releases/Drawing%5flines%5fin%5fJavaScript/x.html#", "http://www.p01.org/releases/Drawing_lines_in_JavaScript/x.html#");
        URL url = URL.parse("http://www.p01.org/releases/Drawing_lines_in_JavaScript/x.html#");
        assertNotNull(url.getAnchor());
        testUrlToString("http://food.com/badpath/////stuff");
        testUrlToString("http://food.com/stuff", "http://food.com/relative/path/../../stuff");
        testUrlToString("http://food.com/stuff", "http://food.com/relative/path/../.././././stuff");
        testUrlToString("http://food.com/relative/path/stuff", "http://food.com/relative/path/./././stuff");
        //should not try to normalize invalid paths
        testUrlToString("http://food.com/relative/path/stuff/../../../../../../../../..", "http://food.com/relative/path/stuff/../../../../../../../../..");
        testUrlToString("http://food.com/relative/", "http://food.com/relative/path/stuff/../..");
        //ensure unparseable parameters are handled as-is
        testUrlToString("http://food.com/boo/foo.html?q=x?a=3????z=q=b=4;??;a?e==f");
    }

    private void testUrlToString(String expect) {
        testUrlToString(expect, expect);
    }

    private void testUrlToString(String expect, String u) {
        URL url = URL.parse(u);
        String s = url.toString();
        assertEquals(s, url.toString());
        assertEquals(decombobulate(url, expect), expect, url.toString());
        if ("http://food.com".equals(u)) {
            u = "http://food.com/";
        }
        if (!u.contains("/..") && !u.contains("/.") && !url.getHost().isLocalhost()) {
            assertEquals(decombobulate(url, expect), u, url.toUnescapedForm());
        }
        if (!u.contains("/../../../../../../../../..")) {
            assertValid(u);
        }
    }

    @Test
    public void validity() {
        assertInvalid("http:///a/b/c");
        assertInvalid("http://x/a/b/c");
        assertInvalid("http://127.z.b.32/a/b/c");
        assertInvalid("http://127.z.b.32/a/b/c");

        StringBuilder lng = new StringBuilder();
        for (int i = 0; i < 270; i++) {
            lng.append('a');
        }
        lng.append(".com");
        assertInvalid("http://" + lng + "/x.txt");
        assertInvalid("http://");
        assertInvalid("http://foo");
        assertInvalid("a");
        assertInvalid("a:b");
        assertInvalid("a:b:c");
        assertInvalid("a:b:2308");
        assertInvalid("http://foo.com:00badPort/x.html");
        assertInvalid("http://foo.com:00badPort/");
        assertInvalid("http://foo.com:00badPort");

        URL url = URL.parse("http://localhost:8080/?kind=anything&recipient=moe@foo.com&url=http://food.com/food.com");
        assertEquals("localhost", url.getHost().toString());
        assertFalse(url.getHost().isIpAddress());
        assertEquals(Host.parse("127.0.0.1"), url.getHost());
        assertNull(url.getHost().getTopLevelDomain());
        assertNull(url.getHost().getDomain());
        assertEquals("8080", url.getPort().toString());
        assertTrue(url.isValid());
        assertNull("Problems should be null but is " + url.getProblems(), url.getProblems());
    }

    private void assertValid(String ur) {
        URL url = URL.parse(ur);
        testValidity(ur);
        assertTrue("'" + url + "' should be valid but reports isValid "
                + url.isValid() + " with problems '" + url.getProblems() + "'",
                url.isValid());
    }

    private void assertInvalid(String ur) {
        URL url = URL.parse(ur);
        assertFalse("'" + url + "' should be invalid", url.isValid());
        testValidity(ur);
    }

    private void testValidity(String ur) {
        URL url = URL.parse(ur);
        Problems p = url.getProblems();
        boolean contradiction = (p == null) != url.isValid();
        String msg = "URL '" + url + "' reports problems " + p + " with contradictory value of isValid() " + url.isValid();
        assertFalse(msg, contradiction);
    }

    private static String decombobulate(URL url, String expect) {
        StringBuilder sb = new StringBuilder();
        for (URLComponent c : url.components()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(c.getComponentName()).append("=").append(c.toString());
        }
        sb.append('\n');
        sb.append(expect);
        sb.append('\n').append(url).append('\n');
        return sb.toString();
    }

    @Test
    public void testProblematic() {
        String s = "http://www.p01.org/releases/Drawing_lines_in_JavaScript/x.html#";
        URL url = URL.parse(s);
        assertTrue(s + " should be valid", url.isValid());
    }

    @Test
    public void testEquality() {
        URL a = URL.parse("http://goofball.com/");
        URL b = URL.parse("http://goofball.com/");

        URLComponent[] ac = a.allComponents();
        URLComponent[] bc = b.allComponents();
        for (int i = 0; i < ac.length; i++) {
            assertEquals(i + ": " + ac[i] + " (" + ac[i].getComponentName() + " - " + ac[i].getClass().getName() + ") equal? " + ac[i].equals(bc[i]), ac[i], bc[i]);
        }
        ac = a.components();
        bc = b.components();
        for (int i = 0; i < ac.length; i++) {
            assertEquals(i + ": " + ac[i] + " (" + ac[i].getComponentName() + " - " + ac[i].getClass().getName() + ") equal? " + ac[i].equals(bc[i]), ac[i], bc[i]);
        }

        URL c = URL.parse("http://goofball.com");
        assertEquals(a, b);
        assertEquals(a, c);

        URL d = URL.parse("http://foo.com");
        assertFalse(a.equals(d));

        a = URL.parse("http://goofball.com/");
        b = URL.parse("http://goofball.com:80/");
        assertEquals(a, b);

        a = URL.parse("https://goofball.com/");
        assertFalse(a.equals(b));

        b = URL.parse("https://goofball.com:443");
        assertEquals(a, b);

        b = URL.parse("https://goofball.com:443/");
        assertEquals(a, b);

        b = URL.parse("https://goofball.com:/ "); //XXX legal?
        a = URL.parse("https://goofball.com/%20");
        assertEquals(a, b);
    }

    @Test
    public void testHorriblyMangledURLsDoNotThrowExceptions() {
        char[] c = new char[1024];
        c[512] = ':';
        c[513] = ':';
        URL u = URL.parse(new String(c));

        u = URL.parse("");
        assertFalse(u.isValid());
        u = URL.parse(" ");
        assertFalse(u.isValid());
        u = URL.parse("  ");
        assertFalse(u.isValid());
        u = URL.parse("  :");
        assertFalse(u.isValid());
        u = URL.parse("  /:");
        assertFalse(u.isValid());
        u = URL.parse("  :/:");
        assertFalse(u.isValid());
        u = URL.parse("/:");
        assertFalse(u.isValid());
        u = URL.parse(":/:");
        assertFalse(u.isValid());
        u = URL.parse("///:");
        assertFalse(u.isValid());
        u = URL.parse("/:/:");
        assertFalse(u.isValid());
        u = URL.parse(":");
        assertFalse(u.isValid());
        u = URL.parse("/");
        assertFalse(u.isValid());
        u = URL.parse("h");
        assertFalse(u.isValid());
        u = URL.parse("1");
        assertFalse(u.isValid());
        u = URL.parse("12");
        assertFalse(u.isValid());
        u = URL.parse(":12");
        assertFalse(u.isValid());
        u = URL.parse(":12/");
        assertFalse(u.isValid());
        u = URL.parse("::::::::::::::::::::::::::::::::::::::::::::::::::");
        assertFalse(u.isValid());
        u = URL.parse("..................................................");
        assertFalse(u.isValid());
        u = URL.parse("//////////////////////////");
        assertFalse(u.isValid());
        u = URL.parse("http://////////////////////////");
        assertFalse(u.isValid());
        u = URL.parse("http:foo@bar//////////////////////////");
        assertFalse(u.isValid());
        u = URL.parse("http:foo@//////////////////////////");
        assertFalse(u.isValid());
        u = URL.parse(":./:./:./:./:");
        assertFalse(u.isValid());
        long l = Integer.MAX_VALUE + 1;
        u = URL.parse("http://goofball.com:" + l);
        assertFalse(u.isValid());
    }

    @Test
    public void testTrailingSlashes() {
        URL a = URL.parse("http://foo.com");
        URL b = URL.parse("http://foo.com/");
        assertEquals(a, b);

        URLBuilder ab = URL.builder(a);
        URLBuilder bb = URL.builder(b);
        URL a1 = ab.add("hey").create();
        URL b1 = bb.add("hey").create();

        assertEquals(a1, b1);

        URL a2 = URL.builder(a).add("foo").add("bar").create();
        URL b2 = URL.builder(a).add("/foo").add("bar").create();

        assertEquals(a2, b2);

        URL a3 = URL.builder(a).add("foo").add("bar").add("").add("/").create();
        URL b3 = URL.builder(a).add("/foo").add("bar").create();

        assertEquals(a3, b3);
    }

    @Test
    public void testValidityAndEscaping() {
        URL a = URL.parse("http://foo.com/stuff-here/more-stuff.foo");
        assertTrue(a.getProblems() + "", a.isValid());

        URL b = URL.builder(Protocols.HTTP).setHost("foo.com").add("stuff-here").add("more-stuff.foo").add("with spaces").create();
        assertTrue(b.getProblems() + "", b.isValid());
    }

    @Test
    public void testHyphens() {
        URL a = URL.parse("proto-http+more://foo.com/stuff-here");
        assertEquals("proto-http+more", a.getProtocol().toString());
        assertTrue(a.isValid());
    }

    @Test
    public void testCyrillic() {
        URL a = URL.parse("http://www.socialskidka.com.ua/%D0%A7%D0%B5%D1%80%D0%BA%D0%B0%D1%81%D1%81%D1%8B");
        String s = a.toUnescapedForm();
        URL b = URL.parse(s);
        assertEquals(a, b);
    }

    @Test
    public void testFileURLs() throws Exception {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        // If tmp was a symlink, the test below was failing, so resolve it here
        tmp = tmp.getCanonicalFile();
        File f = new File(tmp, getClass().getName() + "test");
        java.net.URL url = f.toURI().toURL();
        URL mine = URL.parse(url.toString());

        assertEquals (url, mine.toJavaURL());

        URL other = URL.fromJavaUrl(url);
        assertEquals (url, other.toJavaURL());

        File f1 = new File (mine.toJavaURL().toURI()).getCanonicalFile();
        File f2 = new File (other.toJavaURL().toURI()).getCanonicalFile();

        assertEquals (f, f1);
        assertEquals (f, f2);

        f = new File (new java.net.URL("file:/tmp/1294067737125_0/ev1NYGFiVryRzOrUB3iZ2Pn8Tn0=").toURI());

        URL x = URL.fromJavaUrl(new java.net.URL("file:/tmp/1294067737125_0/ev1NYGFiVryRzOrUB3iZ2Pn8Tn0="));
        assertEquals ("file:///tmp/1294067737125%5f0/ev1NYGFiVryRzOrUB3iZ2Pn8Tn0=", x.toString());

        URLBuilder b = URL.builder(x);
        b.addPathElement("xyz");
        URL y = b.create();
        assertTrue (y.getProblems() + "", y.isValid());
    }

    @Test
    public void testPathDecoding() throws UnsupportedEncodingException {
        if (true) {
            return;
        }
        String orig = "path/to/My Image-small.gif";
        String encoded = "path/to/" + URLEncoder.encode("My Image-small.gif", "UTF-8").replace("+", "%20").replace("-", "%2d");
        assertNotEquals(orig, encoded);
        Path p = Path.parse(orig);
        assertEquals(encoded, p.toString());
        Path p2 = Path.parse(encoded, true);
        assertEquals(orig, p2.toString());
        assertEquals (p, p2);
    }

    @Test
    public void testAddParameters() throws Throwable {
        URL url = URL.parse("http://foo.com/foo/bar");
        url = url.withParameter("baz", "quux");
        assertEquals("http://foo.com/foo/bar?baz=quux", url.toString());
        
        url = URL.parse("http://foo.com/foo/bar?monkey=beetle");
        url = url.withParameter("baz", "quux");
        assertEquals("http://foo.com/foo/bar?monkey=beetle&baz=quux", url.toString());
    }

    @Test
    public void testHyphenatedHost() throws Throwable {
        Label l = new Label("mail-vm");
        assertTrue(l.isValid());
        
        Host host = Host.parse("mail-vm.timboudreau.org");
        assertTrue(host.isValid());
        assertNull(host.getProblems());
    }

    @Test
    public void testHyphenation() throws Throwable {
        URL url = URL.parse("http://mail-vm.timboudreau.org/blog/api-list");
        assertNull(url.getProblems());
        assertTrue(url.isValid());
    }

    @Test
    public void testParameters() throws Throwable {
        URL url = URL.builder(Protocols.HTTP).addDomain("timboudreau")
                .addDomain("com").addPathElement("foo").addPathElement("bar")
                .addQueryPair("quux", "baz").addQueryPair("money", "gone").create();
        String q = url.getPathAndQuery();
        assertEquals("/foo/bar?quux=baz&money=gone", q);
        assertEquals("http://timboudreau.com/foo/bar?quux=baz&money=gone", url.toString());
    }
}
