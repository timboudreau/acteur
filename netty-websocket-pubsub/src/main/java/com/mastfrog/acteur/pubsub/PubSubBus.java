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
package com.mastfrog.acteur.pubsub;

import com.google.inject.ImplementedBy;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import java.util.concurrent.Future;

/**
 * A publish/subscribe bus for netty channels.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(Bus.class)
public interface PubSubBus {

    /**
     * Broadcast a message to all channels known to this bus.
     *
     * @param <T> The object type
     * @param obj The payload
     * @return A promise
     * @throws Exception If something goes wrong encoding the message
     */
    <T> ChannelPromise broadcast(T obj) throws Exception;

    /**
     * Broadcast a message to all channels known to this bus.
     *
     * @param <T> The object type
     * @param obj The payload
     * @param origin The origin channel, which will not receive the message
     * @return A promise
     * @throws Exception If something goes wrong encoding the message
     */
    <T> ChannelPromise broadcast(T obj, Channel origin) throws Exception;

    /**
     * Publish a message to a channel id.
     *
     * @param <T> The type of object
     * @param obj The payload
     * @param to The id of the channel to send to
     * @param origin The channel that the message originated on (the message
     * will not be resent to that channel)
     * @return A promise
     * @throws Exception If something goes wrong encoding the message
     */
    <T> ChannelPromise publish(T obj, ChannelId to, Channel origin) throws Exception;

    /**
     * Subscribe a channel to messages on the passed channel id.  Subscribing
     * may not be completed synchronously, so a future is returned.
     *
     * @param channel The channel (must have a handler that can deal with a
     * WebSocketFrame)
     * @param to The channel id
     * @return A promise
     */
    Future<Boolean> subscribe(Channel channel, ChannelId to);

    /**
     * Unsubscribe a channel from messages on the passed channel id.  Subscribing
     * may not be completed synchronously, so a future is returned.
     *
     * @param channel
     * @param from
     * @return
     */
    Future<Boolean> unsubscribe(Channel channel, ChannelId from);
}
