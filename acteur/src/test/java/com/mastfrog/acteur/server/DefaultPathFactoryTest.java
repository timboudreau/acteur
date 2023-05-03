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
package com.mastfrog.acteur.server;

import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.url.Protocols.HTTP;
import com.mastfrog.url.URL;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class DefaultPathFactoryTest {

    @Test
    public void testNewerFeatures() throws Throwable {
        DefaultPathFactory f = new DefaultPathFactory(new SettingsBuilder()
                .add(ServerModule.SETTINGS_KEY_BASE_PATH, "/foo")
                .add(ServerModule.SETTINGS_KEY_GENERATE_SECURE_URLS, "true")
                .add(ServerModule.SETTINGS_KEY_URLS_HOST_NAME, "paths.example")
                .add(ServerModule.SETTINGS_KEY_URLS_EXTERNAL_PORT, 5720)
                .add(ServerModule.SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT, 5721)
                .build());

        URL url = f.constructURL("/hey/you");
        assertTrue(url.isValid());

        String test1 = "https://paths.example:5721/foo/hey/you";
        String test2 = "https://timboudreau.org:5223/foo/whee?this=that&you=me";

        assertEquals(test1, url.toString(), test1 + "\tversus\n" + url);
        assertEquals(f.constructURI("/hey/you").toString(), url.toString());

        // Test that the Host: header is used when present, and that query strings survive
        EventImpl fakeEvent = new EventImpl(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/up/down",
                new DefaultHttpHeaders().add(HttpHeaderNames.HOST, "timboudreau.org:5223")), f);

        url = f.constructURL("/whee?this=that&you=me", fakeEvent);
        assertTrue(url.isValid());

        assertEquals("timboudreau.org", url.getHost().toString());
        assertEquals(5223, url.getPort().intValue());
        assertEquals(test2, url.toString(), test2 + "\tversus\n" + url);

        // Test that anchors survive
        url = f.constructURL("/whee?this=that&you=me#woohoo", fakeEvent);
        assertTrue(url.isValid());
        assertEquals("woohoo", url.getAnchor().toString());
        assertNotNull(url.getParameters(), url + " has no parameters");
        assertTrue(url.getParameters().isValid());

        // Test that X-Forwarded-Proto overrides settings if present, and uses
        // the correct port
        fakeEvent = new EventImpl(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/up/down",
                new DefaultHttpHeaders()
                        .add(Headers.X_FORWARDED_PROTO.name(), "http")), f);

        url = f.constructURL("/should/be/insecure", fakeEvent);
        assertEquals("/foo/should/be/insecure", url.getPath().toStringWithLeadingSlash());
        assertEquals(HTTP, url.getProtocol());
        assertEquals("paths.example", url.getHost().toString());
        assertEquals(5720, url.getPort().intValue());

    }

}
