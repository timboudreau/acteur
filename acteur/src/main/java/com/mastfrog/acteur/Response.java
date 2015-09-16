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
import org.joda.time.Duration;

/**
 * Abstraction for a response
 *
 * @author Tim Boudreau
 */
public abstract class Response {

    /**
     * Add a header
     * @param <T> The value type for the header
     * @param headerType The header type
     * @param value The header value
     */
    public abstract <T> void add(HeaderValueType<T> headerType, T value);

    /**
     * Set a simple string message
     * 
     * @param message A message
     */
    public abstract void setMessage(Object message);

    /**
     * Set the response code
     * @param status The status
     */
    public abstract void setResponseCode(HttpResponseStatus status);
    /**
     * Set a ChannelFutureListener which will be called after headers are
     * written and flushed to the socket;
     * prefer <code>setResponseWriter()</code> to this method unless
     * you are not using chunked encoding and want to stream your response (in
     * which case, be sure to setChunked(false) or you will have encoding
     * errors).
     *
     * @param listener A ChannelFutureListener which will handle writing
     * the response body after the headers are sent
     */
    public abstract void setBodyWriter(ChannelFutureListener listener);
    /**
     * Set a ResponseWriter which will be called after headers are
     * written and flushed to the socket;  this will cause chunked 
     * encoding to be used;  the ResponseWriter will be called back 
     * repeatedly to stream more of the response until it says that
     * it is done.
     *
     * @param writer A response writer instance which will handle writing
     * the response body after the headers are sent
     */
    public abstract void setBodyWriter(ResponseWriter writer);
    
    /**
     * Get a header which has been previously set
     * @param <T> The type
     * @param header The header definition
     * @return The header value as type T
     */
    protected abstract <T> T get(HeaderValueType<T> header);
    
    /**
     * Set chunked encoding - if set to true, the headers will indicate that
     * chunked encoding will be used and no <code>Content-Length</code> header
     * will be sent.  Chunked encoding will also be defaulted to true if you
     * call <code>setBodyWriter(ResponseWriter)</code>.
     * @param chunked 
     */
    public abstract void setChunked(boolean chunked);
    
    /**
     * Set a delay before the response body should be sent;  this is used
     * for cases such as authentication where failed requests should be 
     * throttled.  This does mean the request will be held in memory until
     * the delay is expired, so it may mean temporary increases in memory
     * requirements.
     * @param delay The delay
     */
    public abstract void setDelay(Duration delay);
}
