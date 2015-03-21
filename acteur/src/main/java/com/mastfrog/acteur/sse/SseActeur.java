/*
 * The MIT License
 *
 * Copyright 2014 tim.
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

package com.mastfrog.acteur.sse;

import com.google.common.net.MediaType;
import com.google.inject.Provider;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.Connection;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import javax.inject.Inject;

/**
 * Use for HTTP requests that get a text/event-stream response which remains
 * open indefinitely. Use this with the &#064;Concluders annotation to have it
 * handle everything related to publishing server-sent events in the response to
 * this request.
 * <p/>
 * To feed events to be streamed to all open responses, simply ask for an EventSink
 * to be injected and use its <code>publish()</code> method.  By default,
 * EventSink is bound as a singleton;  for per-URL or per-user or something else
 * event sinks, you can
 * <ul>
 * <li>Precede this with an Acteur that locates the correct EventSink instance
 * and puts it in its state, so it is the instance that is found</li>
 * <li>Subclass this and used &#064;Named to look up a specific EventSource 
 * (make sure to bind it in Scopes.SINGLETON)</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public class SseActeur extends Acteur {

    private static final MediaType TYPE = MediaType.parse("text/event-stream; charset=UTF-8");

    @Inject
    public SseActeur(EventSink sink, Provider<EventChannelName> name) {
        add(Headers.CONTENT_TYPE, TYPE);
        add(Headers.CACHE_CONTROL, CacheControl.PRIVATE_NO_CACHE_NO_STORE);
        add(Headers.CONNECTION, Connection.keep_alive);
        setState(new RespondWith(OK));
        setResponseBodyWriter(new L(sink, name.get()));
        setChunked(true);
    }

    private static class L implements ChannelFutureListener {

        private final EventSink sink;
        private final EventChannelName name;

        public L(EventSink sink, EventChannelName name) {
            this.sink = sink;
            this.name = name;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            // At this point we know the headers have been sent, so it is
            // safe to start sending events
            if (name == null) {
                sink.register(future.channel());
            } else {
                sink.register(name, future.channel());
            }
        }
    }
}
