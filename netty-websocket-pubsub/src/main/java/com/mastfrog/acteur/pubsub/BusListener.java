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

import io.netty.channel.Channel;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

/**
 * Listener which can be notified of <i>all</i> events published on the
 * bus.  To use, simply implement and bind as an eager singleton.
 *
 * @author Tim Boudreau
 */
public abstract class BusListener {

    protected abstract <T> void onPublish(T obj, ChannelId to, Channel origin);

    @Singleton
    public static class Registry {
        private final List<BusListener> listeners = new ArrayList<>();

        void register(BusListener l) {
            listeners.add(l);
        }

        <T> void onPublish(T obj, ChannelId to, Channel origin) {
            for (BusListener l : listeners) {
                l.onPublish(obj, to, origin);
            }
        }
    }
}
