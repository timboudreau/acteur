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
package com.mastfrog.acteur.websocket;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.stringHeader;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.giulius.annotations.Setting;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.url.Protocols;
import com.mastfrog.url.URL;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
import static io.netty.handler.codec.http.HttpResponseStatus.UPGRADE_REQUIRED;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.AttributeKey;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * Use in &#064;Precursors annotation before acteurs which consume and produce
 * web socket responses. All acteurs following this one will be re-created and
 * called for each web socket event - so effectively the programming model is
 * still a chain of Acteurs; subsequent ones simply ask for a WebSocketEvent
 * instead of an HttpEvent to get their payload. Such acteurs can reply with
 * ok(someObject) to have the object converted to JSON per normal use and sent
 * on the websocket instead of as an http response. Header methods do nothing,
 * for obvious reasons, in acteurs that process websocket events.
 * <p>
 * As with any acteur, all objects provided by preceding acteurs in the chain
 * may be injected into acteurs that process a websocket connection. So the
 * pattern looks more like:
 * <ul>
 * <li>Check url path (e.g. &#064;Path annotation handler as normal)</li>
 * <li>Check method (e.g. &#064;Methods annotation handler as normal)</li>
 * <li>Authenticate (e.g. &#064;Authenticated triggers whatever acteur subclass
 * you bound to AuthenticationActeur)</li>
 * <li>WebSocketUpgradeActeur
 * <ul>
 * <li>Event validity checker acteur
 * <li>Event processor acteur</li>
 * </ul>
 * </ul>
 * where the indented elements are constructed/called repeantedly for each
 * WebSocketFrame.
 *
 * @author Tim Boudreau
 */
public class WebSocketUpgradeActeur extends Acteur {

    public static final boolean DEFAULT_WEBSOCKET_SECURE_PROTOCOL = false;
    @Setting(value = "Websockets: Generate HTTPS URLs", type = Setting.ValueType.BOOLEAN,
            defaultValue = DEFAULT_WEBSOCKET_SECURE_PROTOCOL + "")
    public static final String SETTINGS_KEY_WEBSOCKET_SECURE_PROTOCOL = "websocket.secure.urls";

    public static final int DEFAULT_WEBSOCKET_FRAME_MAX_LENGTH = 5 * 1_024 * 1_024;
    @Setting(value = "Max bytes per websocket frame", type = Setting.ValueType.INTEGER, defaultValue = "" + DEFAULT_WEBSOCKET_FRAME_MAX_LENGTH)
    public static final String SETTINGS_KEY_WEBSOCKET_FRAME_MAX_LENGTH = "websocket.frame.max.bytes";

    public static final AttributeKey<Supplier<? extends Chain<? extends Acteur, ?>>> CHAIN_KEY
            = AttributeKey.valueOf(WebSocketUpgradeActeur.class, "websocket");
    public static final AttributeKey<Page> PAGE_KEY
            = AttributeKey.valueOf(WebSocketUpgradeActeur.class, "page");

    @Inject
    @SuppressWarnings({"unchecked", "deprecation"})
    protected WebSocketUpgradeActeur(HttpEvent evt, PathFactory paths, Settings settings, Page page,
            Deferral defer, Chain chain, ApplicationControl ctrl, OnWebsocketConnect onConnect, ReentrantScope scope) {
        Path pth = paths.toExternalPath(evt.path());
        int max = settings.getInt(SETTINGS_KEY_WEBSOCKET_FRAME_MAX_LENGTH, DEFAULT_WEBSOCKET_FRAME_MAX_LENGTH);

        boolean secure = settings.getBoolean(SETTINGS_KEY_WEBSOCKET_SECURE_PROTOCOL, DEFAULT_WEBSOCKET_SECURE_PROTOCOL);

        URL url = paths.constructURL(secure ? Protocols.WSS : Protocols.WS, pth);

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                url.toString(), null, true, max);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(evt.request());
        if (handshaker == null) {
            add(stringHeader(SEC_WEBSOCKET_VERSION), WebSocketVersion.V13.toHttpHeaderValue());
            reply(UPGRADE_REQUIRED);
        } else {
            ChannelFuture future = handshaker.handshake(evt.channel(), evt.request());
            AtomicReference<Channel> channel = new AtomicReference<>();
            // Ensure that we call OnWebsocketConnect with the scope contents
            // set as they were when this constructor was called
            Runnable runConnect = scope.wrap(() -> {
                Channel ch = channel.get();
                try {
                    System.out.println("CALL ONCONNECT " + onConnect);
                    Object a = onConnect.connected(evt, ch);
                    Object b = connected(evt, channel.get());
                    ch.attr(CHAIN_KEY).set(chain.remnantSupplier(flatten(a, b)));
                    ch.attr(PAGE_KEY).set(page);
                } catch (Exception | Error e) {
                    System.out.println("HAVE ERROR - CLOSE");
                    ch.close();
                    ctrl.internalOnError(e);
                }
            });

            future.addListener((ChannelFuture future1) -> {
                Channel ch = future1.channel();
                if (future1.isSuccess()) {
                    channel.set(future1.channel());
                    runConnect.run();
//                    future1.addListener(ChannelFutureListener.CLOSE);
                } else if (future1.cause() != null) {
                    ctrl.internalOnError(future1.cause());
                    System.out.println("HAVE ERROR 2 CLOSE");
                    if (ch.isOpen()) {
                        future1.channel().close(); // probably is already, but be sure
                    }
                }
            });
            Resumer res = defer.defer(); // Intentionally never resume
            next();
        }
    }

    private static Object[] flatten(Object a, Object b) {
        List<Object> result = new LinkedList<>();
        populate(a, result);
        populate(b, result);
        return result.toArray(Object[]::new);
    }

    private static void populate(Object o, List<? super Object> into) {
        if (o == null) {
            return;
        }
        if (o instanceof Object[]) {
            Object[] o1 = (Object[]) o;
            for (Object o2 : o1) {
                populate(o2, into);
            }
        } else {
            into.add(o);
        }
    }

    Object connected(HttpEvent evt, Channel channel) {
        // do nothing
        return null;
    }
}
