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

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;
import io.netty.channel.Channel;

/**
 * Global interface called when a websocket connection has been successfully
 * handshaken via WebSocketUpgradeActeur. If you need other objects from the
 * current event, simply inject them and do not bind as a singleton.
 */
@ImplementedBy(value = DefaultOnWebsocketConnect.class)
public interface OnWebsocketConnect {

    /**
     * Called on connection success. May return an object or array of objects to
     * include in the scope for acteurs which receive ongoing web socket events.
     *
     * @param evt The event
     * @param channel The channel, which can be used for future communication
     * with the client initiating the websocket.
     * @return An object or array of objects which should be included in the
     * scope for injection into acteurs that follow the one which initiates the
     * websocket.  Return null if you have noting to add into the request scope.
     */
    Object connected(HttpEvent evt, Channel channel);
}
