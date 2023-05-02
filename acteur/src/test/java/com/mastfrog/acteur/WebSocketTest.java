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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.mastfrog.acteur.WebSocketTest.WSM;
import com.mastfrog.acteur.header.entities.Connection;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.stringHeader;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.websocket.WebSocketUpgradeActeur;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.netty.http.client.ResponseFuture;
import com.mastfrog.netty.http.client.State;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.DataInput;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({WSM.class, TestHarnessModule.class, SilentRequestLogger.class})
public class WebSocketTest {

    @Test(timeout = 60000)
    public void test(TestHarness harn, PathFactory factory, ObjectMapper mapper) throws Throwable {
        URL url = factory.constructURL(Path.parse("/ws"), false);

        Map<String,Object> payload = map("ix").to(10).map("name").to("first").build();

        ResponseFuture fut = harn.post("ws")
                .setTimeout(Duration.ofSeconds(60000))
                .addHeader(Headers.CONNECTION, Connection.upgrade)
                .addHeader(stringHeader("origin"), url.toString())
                .addHeader(stringHeader("Upgrade"), "websocket")
                .dontAggregateResponse()
                .onEvent(new Receiver<com.mastfrog.netty.http.client.State<?>>() {
                    @Override
                    public void receive(State<?> object) {
//                        System.out.println("STATE " + object + " - " + object.get());
                    }
                }).execute();
        fut.sendOn(StateType.HeadersReceived, new TextWebSocketFrame(mapper.writeValueAsString(payload)));
        fut.await(5, TimeUnit.SECONDS).throwIfError();

    }

    static final class WSM extends AbstractModule {

        @Override
        protected void configure() {
            System.setProperty(ServerModule.PORT, "" + new PortFinder().findAvailableServerPort());
            System.setProperty(ServerModule.SETTINGS_KEY_URLS_HOST_NAME, "localhost");
            install(new ServerModule<>(WsApp.class));
        }
    }

    static final class WsApp extends Application {

        WsApp() {
            add(WsPage.class);
        }

        static final class WsPage extends Page {

            @SuppressWarnings("deprecation")
            @Inject
            WsPage(ActeurFactory af) {
                add(af.matchPath("ws"));
                add(WebSocketUpgradeActeur.class);
                add(WsActeur.class);
            }

            static final class WsActeur extends Acteur {

                @Inject
                @SuppressWarnings("unchecked")
                WsActeur(WebSocketFrame frame, ObjectMapper mapper) throws IOException {
                    Map<String, Object> m = mapper.readValue((DataInput) new ByteBufInputStream(frame.content()), Map.class);
                    Object o = m.get("ix");
                    assertNotNull(o);
                    assertTrue(o instanceof Number);
                    m = new HashMap<>(m);
                    m.put("prev", o);
                    m.put("ix", ((Number) o).intValue() + 1);
                    m.put("name", "replyTo" + ((Number) o).intValue());
                    ok(m);
                }
            }
        }
    }

}
