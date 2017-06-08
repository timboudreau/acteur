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

import com.mastfrog.acteur.headers.HeaderValueType;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;

/**
 * Abstraction for a response
 *
 * @author Tim Boudreau
 */
public abstract class Response {

    /**
     * Add a header
     *
     * @param <T> The value type for the header
     * @param headerType The header type
     * @param value The header value
     * @return this
     */
    public abstract <T> Response add(HeaderValueType<T> headerType, T value);

    /**
     * Set a simple string message
     *
     * @param message A message
     * @return this
     */
    public abstract Response content(Object message);

    /**
     * Set the response code
     *
     * @param status The status
     * @return this
     */
    public abstract Response status(HttpResponseStatus status);

    /**
     * Set a ChannelFutureListener which will be called after headers are
     * written and flushed to the socket; prefer
     * <code>setResponseWriter()</code> to this method unless you are not using
     * chunked encoding and want to stream your response (in which case, be sure
     * to chunked(false) or you will have encoding errors).
     *
     * @param listener A ChannelFutureListener which will handle writing the
     * response body after the headers are sent
     */
    public abstract Response contentWriter(ChannelFutureListener listener);

    /**
     * Set a ResponseWriter which will be called after headers are written and
     * flushed to the socket; this will cause chunked encoding to be used; the
     * ResponseWriter will be called back repeatedly to stream more of the
     * response until it says that it is done.
     *
     * @param writer A response writer instance which will handle writing the
     * response body after the headers are sent
     * @return this
     */
    public abstract Response contentWriter(ResponseWriter writer);

    /**
     * Set chunked encoding - if set to true, the headers will indicate that
     * chunked encoding will be used and no <code>Content-Length</code> header
     * will be sent. Chunked encoding will also be defaulted to true if you call
     * <code>contentWriter(ResponseWriter)</code>.
     *
     * @param chunked
     * @return this
     */
    public abstract Response chunked(boolean chunked);

    /**
     * Set a delay before the response body should be sent; this is used for
     * cases such as authentication where failed requests should be throttled.
     * This does mean the request will be held in memory until the delay is
     * expired, so it may mean temporary increases in memory requirements.
     *
     * @param delay The delay
     * @return this
     */
    public abstract Response delayedBy(Duration delay);

    /**
     * Get a header which has been previously set
     *
     * @param <T> The type
     * @param header The header definition
     * @return The header value as type T
     */
    protected abstract <T> T get(HeaderValueType<T> header);

    /**
     * Set a simple string message
     *
     * @param message A message
     * @return this
     * @deprecated use content() instead
     */
    @Deprecated
    public final Response setMessage(Object message) {
        return content(message);
    }

    /**
     * Set the response code
     *
     * @param status The status
     * @return this
     * @deprecated use status() instead
     */
    @Deprecated
    public final Response setResponseCode(HttpResponseStatus status) {
        return status(status);
    }

    /**
     * Set a ChannelFutureListener which will be called after headers are
     * written and flushed to the socket; prefer
     * <code>setResponseWriter()</code> to this method unless you are not using
     * chunked encoding and want to stream your response (in which case, be sure
     * to setChunked(false) or you will have encoding errors).
     *
     * @param listener A ChannelFutureListener which will handle writing the
     * response body after the headers are sent
     * @deprecated use contentWriter() instead
     */
    @Deprecated
    public final Response setBodyWriter(ChannelFutureListener listener) {
        return contentWriter(listener);
    }

    /**
     * Set a ResponseWriter which will be called after headers are written and
     * flushed to the socket; this will cause chunked encoding to be used; the
     * ResponseWriter will be called back repeatedly to stream more of the
     * response until it says that it is done.
     *
     * @param writer A response writer instance which will handle writing the
     * response body after the headers are sent
     * @return this
     * @deprecated use contentWriter() instead
     */
    public final Response setBodyWriter(ResponseWriter writer) {
        return contentWriter(writer);
    }

    /**
     * Set chunked encoding - if set to true, the headers will indicate that
     * chunked encoding will be used and no <code>Content-Length</code> header
     * will be sent. Chunked encoding will also be defaulted to true if you call
     * <code>setBodyWriter(ResponseWriter)</code>.
     *
     * @param chunked
     * @return this
     * @deprecated use chunked() instead
     */
    @Deprecated
    public final Response setChunked(boolean chunked) {
        return chunked(chunked);
    }

    /**
     * Set a delay before the response body should be sent; this is used for
     * cases such as authentication where failed requests should be throttled.
     * This does mean the request will be held in memory until the delay is
     * expired, so it may mean temporary increases in memory requirements.
     *
     * @param delay The delay
     * @return this
     * @deprecated use delayedBy() instead
     */
    @Deprecated
    public final Response setDelay(Duration delay) {
        return delayedBy(delay);
    }

    /**
     * Set this response to be chunked.
     *
     * @return this
     */
    public final Response chunked() {
        return chunked(true);
    }

    /**
     * Set this response not to use HTTP chunked encoding.
     *
     * @return this
     */
    public final Response unchunked() {
        return chunked(false);
    }

}
