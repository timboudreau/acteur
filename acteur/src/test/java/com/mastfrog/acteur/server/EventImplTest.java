/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_BASE_PATH;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_URLS_HOST_NAME;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import com.mastfrog.util.strings.Strings;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EventImplTest {

    private PathFactory emptySettings;
    private PathFactory withBasePath;
    private PathFactory withExternalSecureHost;
    private PathFactory withExternalSecureHostOnOddPort;

    @BeforeEach
    public void setUp() throws IOException {
        emptySettings = new DefaultPathFactory(new SettingsBuilder().build());
        withBasePath = new DefaultPathFactory(new SettingsBuilder()
                .add(SETTINGS_KEY_BASE_PATH, "foo/bar")
                .build());
        withExternalSecureHost = new DefaultPathFactory(new SettingsBuilder()
                .add(SETTINGS_KEY_URLS_HOST_NAME, "poodle.com")
                .add(ServerModule.SETTINGS_KEY_GENERATE_SECURE_URLS, true)
                .build());
        withExternalSecureHostOnOddPort = new DefaultPathFactory(new SettingsBuilder()
                .add(SETTINGS_KEY_URLS_HOST_NAME, "puddle.com")
                .add(ServerModule.SETTINGS_KEY_GENERATE_SECURE_URLS, true)
                .add(ServerModule.SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT, 7_443)
                .build());
    }

    @Test
    public void testSomeMethod() {
        EventImpl evt = newEvent(emptySettings, "http://foo.com/foo/bar/baz/quux");
        Assertions.assertEquals(Path.parse("foo/bar/baz/quux"), evt.path());
        Assertions.assertEquals("http://foo.com/foo/bar/baz/quux", evt.getRequestURL(false));

        evt = newEvent(withBasePath, "http://foo.com/foo/bar/baz/quux");
        Assertions.assertEquals(Path.parse("baz/quux"), evt.path());
        Assertions.assertEquals("http://foo.com/foo/bar/baz/quux", evt.getRequestURL(false));

        evt = newEvent(withExternalSecureHost, true, "http://foo.com/foo/bar/baz/quux");
        Assertions.assertEquals(Path.parse("foo/bar/baz/quux"), evt.path());
        Assertions.assertEquals("https://poodle.com/foo/bar/baz/quux", evt.getRequestURL(false));

        evt = newEvent(withExternalSecureHostOnOddPort, "http://foo.com/foo/bar/baz/quux");
        Assertions.assertEquals(Path.parse("foo/bar/baz/quux"), evt.path());
        Assertions.assertEquals("https://puddle.com:7443/foo/bar/baz/quux", evt.getRequestURL(false));

        evt = newEvent(withExternalSecureHostOnOddPort, "http://foo.com/foo/bar/baz/quux", "X-Forwarded-Host", "x.com");
        Assertions.assertEquals(Path.parse("foo/bar/baz/quux"), evt.path());
        Assertions.assertEquals("https://puddle.com:7443/foo/bar/baz/quux", evt.getRequestURL(false));

        evt = newEvent(withExternalSecureHostOnOddPort, "http://foo.com/foo/bar/baz/quux",
                "X-Forwarded-Host", "x.com:7445",
                "X-Forwarded-Proto", "https"
        );
        Assertions.assertEquals(Path.parse("foo/bar/baz/quux"), evt.path());
        Assertions.assertEquals("https://x.com:7445/foo/bar/baz/quux", evt.getRequestURL(true));
    }

    private EventImpl newEvent(PathFactory paths, String url, String... headers) {
        return newEvent(paths, false, url, headers);
    }

    private EventImpl newEvent(PathFactory paths, boolean forceHttps, String url, String... headers) {
        HttpRequest req = req(url, forceHttps, headers);
        SocketAddress addr = InetSocketAddress.createUnresolved("example.com", 80);
        EmbeddedChannel ch = new EmbeddedChannel();
        return new EventImpl(req, addr, null, paths, null, url.startsWith("https"));
    }

    private HttpRequest req(String url, String... headers) {
        return req(url, false, headers);
    }

    private HttpRequest req(String url, boolean forceHttps, String... headers) {
        URL u = URL.parse(url);
        Assertions.assertTrue((headers.length) % 2 == 0, Strings.join(',', headers));
        DefaultHttpHeaders hdrs = new DefaultHttpHeaders();
        for (int i = 0; i < headers.length; i += 2) {
            hdrs.add(headers[i], headers[i + 1]);
        }
        if (u.getPort().intValue() != u.getProtocol().getDefaultPort().intValue()) {
            hdrs.add("Host", u.getHostAndPort().toString());
        } else {
            hdrs.add("Host", u.getHost().toString());
        }
        if (forceHttps) {
            hdrs.add("X-Forwarded-Proto", "https");
        } else {
            hdrs.add("X-Forwarded-Proto", u.getProtocol().toString());
        }
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, u.getPathAndQuery(), hdrs);
    }

}
