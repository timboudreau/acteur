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

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
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
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * Use in &#064;Precursors annotation before acteurs which consume and produce
 * web socket responses.
 *
 * @author Tim Boudreau
 */
public class WebSocketUpgradeActeur extends Acteur {

    public static final String SETTINGS_KEY_WEBSOCKET_SECURE_PROTOCOL = "websocket.secure.urls";
    public static final boolean DEFAULT_WEBSOCKET_SECURE_PROTOCOL = false;
    public static final String SETTINGS_KEY_WEBSOCKET_FRAME_MAX_LENGTH = "websocket.frame.max.bytes";
    public static final int DEFAULT_WEBSOCKET_FRAME_MAX_LENGTH = 5 * 1024 * 1024;
    public static final AttributeKey<Supplier<? extends Chain<? extends Acteur, ?>>> CHAIN_KEY
            = AttributeKey.valueOf(WebSocketUpgradeActeur.class, "websocket");
    public static final AttributeKey<Page> PAGE_KEY
            = AttributeKey.valueOf(WebSocketUpgradeActeur.class, "page");

    @Inject
    @SuppressWarnings("unchecked")
    protected WebSocketUpgradeActeur(HttpEvent evt, PathFactory paths, Settings settings, Page page, Deferral defer, Chain chain, ApplicationControl ctrl, OnWebsocketConnect onConnect) {
        Path pth = paths.toExternalPath(evt.path());
        int max = settings.getInt(SETTINGS_KEY_WEBSOCKET_FRAME_MAX_LENGTH, DEFAULT_WEBSOCKET_FRAME_MAX_LENGTH);

        boolean secure = settings.getBoolean(SETTINGS_KEY_WEBSOCKET_SECURE_PROTOCOL, DEFAULT_WEBSOCKET_SECURE_PROTOCOL);

        URL url = paths.constructURL(secure ? Protocols.WSS : Protocols.WS, pth);

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                url.toString(), null, true, max);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(evt.request());
        if (handshaker == null) {
            add(Headers.stringHeader(SEC_WEBSOCKET_VERSION), WebSocketVersion.V13.toHttpHeaderValue());
            reply(UPGRADE_REQUIRED);
        } else {
            ChannelFuture future = handshaker.handshake(evt.channel(), evt.request());
            future.addListener((ChannelFutureListener) (ChannelFuture future1) -> {
                if (future1.isSuccess()) {
                    Channel ch = future1.channel();
                    Object a = onConnect.connected(evt, ch);
                    Object b = connected(evt, ch); // allow subclasses
                    ch.attr(CHAIN_KEY).set(chain.remnantSupplier(arrayOf(a, b)));
                    ch.attr(PAGE_KEY).set(page);
                    return;
                } else if (future1.cause() != null) {
                    ctrl.internalOnError(future1.cause());
                }
                future1.addListener(ChannelFutureListener.CLOSE);
            });
            Resumer res = defer.defer(); // Intentionally never resume
            next();
        }
    }

    private Object[] arrayOf(Object a, Object b) {
        if (a != null && b != null) {
            return new Object[] { a, b };
        } else if (a == null && b != null) {
            return new Object[] { b };
        } else if (a != null && b == null) {
            return new Object[] { a };
        } else {
            return new Object[0];
        }
    }

    Object connected(HttpEvent evt, Channel channel) {
        // do nothing
        return null;
    }

    @ImplementedBy(DefaultOnWebsocketConnect.class)
    public interface OnWebsocketConnect {

        Object connected(HttpEvent evt, Channel channel);

    }

    private static final class DefaultOnWebsocketConnect implements OnWebsocketConnect {

        @Override
        public Object connected(HttpEvent evt, Channel channel) {
            // do nothing
            return null;
        }
    }
}
