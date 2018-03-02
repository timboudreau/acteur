/*
 * The MIT License
 *
 * Copyright 2018 tim.
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

package com.mastfrog.url;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class UnderscoreTest {

    @Test
    public void testUnderscoreInParameterName() throws Exception {
        URL url = URL.parse("http://foo.com/bar?first_name=joe&last_name=blow");
        assertEquals("joe", url.getParameters().toParsedParameters().getFirst("first_name"));
        assertEquals("blow", url.getParameters().toParsedParameters().getFirst("last_name"));

        System.out.println("ENCODED TO " + url);

        URL url2 = URL.builder(Protocols.HTTP).setHost("foo.com").setPath("bar")
                .addQueryPair("first_name", "joe")
                .addQueryPair("last_name", "blow")
                .create();
        assertEquals(url, url2);
        assertEquals(url.toString(), url2.toString());
        assertEquals(url.toJavaURL().toString(), url2.toJavaURL().toString());
        assertEquals(url.toURI(), url2.toURI());
        assertEquals(url.toUnescapedForm(), url2.toUnescapedForm());
        assertEquals("joe", url2.getParameters().toParsedParameters().getFirst("first_name"));
        assertEquals("blow", url2.getParameters().toParsedParameters().getFirst("last_name"));

        URL url3 = URL.parse("http://foo.com/bar/?first_name=joe&last_name=blow");
        assertEquals(url, url3);
    }

    @Test
    public void testUnderscoreInParameterNameAndValue() throws Exception {
        URL url = URL.parse("http://foo.com/bar?first_name=joe_is&last_name=blow_is");
        assertEquals("joe_is", url.getParameters().toParsedParameters().getFirst("first_name"));
        assertEquals("blow_is", url.getParameters().toParsedParameters().getFirst("last_name"));

        URL url2 = URL.builder(Protocols.HTTP).setHost("foo.com").setPath("bar")
                .addQueryPair("first_name", "joe_is")
                .addQueryPair("last_name", "blow_is")
                .create();
        assertEquals(url, url2);
        assertEquals(url.toString(), url2.toString());
        assertEquals(url.toJavaURL().toString(), url2.toJavaURL().toString());
        assertEquals(url.toURI(), url2.toURI());
        assertEquals(url.toUnescapedForm(), url2.toUnescapedForm());
        assertEquals("joe_is", url2.getParameters().toParsedParameters().getFirst("first_name"));
        assertEquals("blow_is", url2.getParameters().toParsedParameters().getFirst("last_name"));

        URL url3 = URL.parse("http://foo.com/bar/?first_name=joe_is&last_name=blow_is");
        assertEquals(url, url3);
    }

}
