/*
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.net.SocketAddress;

/**
 * An HTTP request or similar, which is passed to Pages for their Acteurs to
 * respond to.
 *
 * @author Tim Boudreau
 */
public interface Event<T> {

    /**
     * Get the Netty channel this request is travelling through.
     *
     * @return The channel
     */
    Channel channel();

    /**
     * Get the actual HTTP request.
     *
     * @return An http request
     */
    T request();

    /**
     * Get the remote address of whoever made the request. The framework will
     * take care of interpreting HTTP headers used by proxies to represent the
     * real origin.
     *
     * @return An address
     */
    SocketAddress remoteAddress();

    /**
     * Will use Jackson to parse the request body and return an object of the
     * type requested if possible.
     * <p/>
     *
     * @param <T> The type
     * @param type The type of object to return
     * @return An object of type T
     * @throws IOException if the body is malformed or for some other reason,
     * cannot be parsed
     */
    <T> T jsonContent(Class<T> type) throws IOException;

    ChannelHandlerContext ctx();

    /**
     * Get the raw request body, if any.
     *
     * @return A bytebuf if one is available.
     * @throws IOException If something goes wrong
     */
    ByteBuf content() throws IOException;

    @Deprecated
    default Channel getChannel() {
        return channel();
    }

    /**
     * Get the actual HTTP request.
     *
     * @return An http request
     * @deprecated use request()
     */
    @Deprecated
    default T getRequest() {
        return request();
    }

    /**
     * Get the remote address of whoever made the request, if necessary,
     * interpreting HTTP proxy headers to give the <i>real</i> remote address,
     * not that of the reverse proxy.
     *
     * @return An address
     * @deprecated use remoteAddress().
     */
    @Deprecated
    default SocketAddress getRemoteAddress() {
        return remoteAddress();
    }

    /**
     * Will use Jackson to parse the request body and return an object of the
     * type requested if possible.
     * <p/>
     *
     * @param <T> The type
     * @param type The type of object to return
     * @return An object of type T
     * @throws IOException if the body is malformed or for some other reason,
     * cannot be parsed
     * @deprecated use jsonContent()
     */
    @Deprecated
    default <T> T getContentAsJSON(Class<T> type) throws IOException {
        return jsonContent(type);
    }

    /**
     * Get the Netty channel this request is travelling through.
     *
     * @return The channel
     * @deprecated use content()
     */
    @Deprecated
    default ByteBuf getContent() throws IOException {
        return content();
    }
}
