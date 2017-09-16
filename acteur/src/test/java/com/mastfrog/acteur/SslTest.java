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
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.client.ResponseHandler;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.url.Protocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.mastfrog.util.net.PortFinder;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.ssl.SslProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Tim Boudreau
 */
public class SslTest {

    private int port;
    private ServerControl serverControl;
    private HttpClient client;

    @Test(timeout=65000)
    public void test() throws Throwable {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<String> content = new AtomicReference<>();
        AtomicReference<HttpHeaders> headers = new AtomicReference<>();
        AtomicReference<HttpResponseStatus> status = new AtomicReference<>();
        client.get().setTimeout(Duration.ofSeconds(60)).setURL("https://localhost:" + port + "/test").execute(new ResponseHandler<String>(String.class) {
            @Override
            protected void onError(Throwable err) {
                thrown.set(err);
            }

            @Override
            protected void onErrorResponse(HttpResponseStatus stat, HttpHeaders hdrs, String ct) {
                status.set(stat);
                content.set(ct);
                headers.set(hdrs);
            }

            @Override
            protected void receive(HttpResponseStatus stat, HttpHeaders hdrs, String obj) {
                status.set(stat);
                content.set(obj);
                headers.set(hdrs);
            }
        }).await(60, TimeUnit.SECONDS);

        if (thrown.get() != null) {
            throw thrown.get();
        }
        assertNotNull(status.get());
        assertNotNull(content.get());
        assertNotNull(headers.get());
        assertEquals(OK, status.get());
        assertEquals("https\n" + MESSAGE, content.get());
        assertTrue(headers.get().contains("X-Request-Encrypted"));
    }

    static boolean useOpenSSL;
    
    @Before
    public void setup() throws IOException {
        port = new PortFinder().findAvailableServerPort();
        SettingsBuilder set = new SettingsBuilder().add(ServerModule.PORT, port);
        if (useOpenSSL) {
            set.add(ServerModule.SETTINGS_KEY_SSL_ENGINE, SslProvider.OPENSSL_REFCNT.name());
        }
        Server server = new ServerBuilder().applicationClass(SslApp.class).ssl()
                .add(set.build()).build();
        this.serverControl = server.start();
        client = HttpClient.builder().useCompression().build();
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
//        useOpenSSL = true;
        SslTest test = new SslTest();
        test.setup();
        test.serverControl.await();
    }

    @After
    public void tearDown() throws InterruptedException {
        try {
            if (client != null) {
                client.shutdown();
            }
        } finally {
            if (serverControl != null) {
                serverControl.shutdown(true);
            }
        }
    }

    static final class SslApp extends Application {

        @Inject
        SslApp() {
            add(TestPage.class);
        }

        private static final class TestPage extends Page {

            @Inject
            TestPage() {
                add(TestActeur.class);
            }
        }

        private static final class TestActeur extends Acteur {

            @Inject
            TestActeur(HttpEvent evt, Protocol protocol) {
                if (evt.isSsl()) {
                    add(Headers.header("X-Request-Encrypted"), "true");
                }
                ok(protocol + "\n" + MESSAGE);
            }
        }

    }
    static final String MESSAGE;

    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("This is line ").append(i).append("\n");
        }
        MESSAGE = sb.toString();
    }
}
