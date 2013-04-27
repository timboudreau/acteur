package com.mastfrog.acteur;

import com.mastfrog.acteur.util.HeaderValueType;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;

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
    
    protected abstract <T> T get(HeaderValueType<T> header);
    
    abstract void setChunked(boolean chunked);
}
