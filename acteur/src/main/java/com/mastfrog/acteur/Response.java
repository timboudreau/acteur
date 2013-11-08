package com.mastfrog.acteur;

import com.mastfrog.acteur.util.HeaderValueType;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.joda.time.Duration;

/**
 * Abstraction for a response
 *
 * @author Tim Boudreau
 */
public abstract class Response {

    public abstract <T> void add(HeaderValueType<T> decorator, T value);

    public abstract void setMessage(String message);

    public abstract void setResponseCode(HttpResponseStatus status);

    public abstract void setBodyWriter(ChannelFutureListener listener);

    public abstract void setBodyWriter(ResponseWriter writer);
    
    protected abstract <T> T get(HeaderValueType<T> header);
    
    public abstract void setChunked(boolean chunked);
    
    public abstract void setDelay(Duration delay);
}
