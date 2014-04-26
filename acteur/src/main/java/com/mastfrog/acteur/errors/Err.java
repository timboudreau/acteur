/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur.errors;

import com.google.common.collect.ImmutableMap;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;

/**
 * Convenience implementation of ErrorResponse backed by a map, with
 * constructors for various common scenarios.
 *
 * @author Tim Boudreau
 */
public final class Err implements ErrorResponse {

    public final HttpResponseStatus status;
    public final Map<String, Object> message;

    public Err(Throwable t) {
        this(unwind(t, true));
    }
    
    public static Err of(Throwable t) {
        return new Err(t);
    }
    
    public static Err badRequest(String msg) {
        return new Err(HttpResponseStatus.BAD_REQUEST, msg);
    }
    
    public static Err gone(String msg) {
        return new Err(HttpResponseStatus.GONE, msg);
    }
    
    public static Err conflict(String msg) {
        return new Err(HttpResponseStatus.CONFLICT, msg);
    }

    public static Err forbidden(String msg) {
        return new Err(HttpResponseStatus.FORBIDDEN, msg);
    }
    
    static String unwind(Throwable t, boolean returnClassName) {
        Throwable orig = t;
        String lastMessage = null;
        String lastType;
        do {
            if (t.getMessage() != null) {
                lastMessage = t.getMessage();
            }
            lastType = t.getClass().getName();
            t = t.getCause();
        } while (t != null);
        if (lastMessage == null) {
            Throwable[] others = t.getSuppressed();
            if (others != null && others.length > 0) {
                for (Throwable other : others) {
                    String result = unwind(other, false);
                    if (result != null) {
                        break;
                    }
                }
            }
        }
        if (lastMessage == null && returnClassName) {
            lastMessage = lastType;
        }
        return lastMessage;
    }

    public Err(String message) {
        this(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                new ImmutableMap.Builder<String, Object>().put("error", message).build());
    }

    public Err(HttpResponseStatus code, String message) {
        this(code,
                new ImmutableMap.Builder<String, Object>().put("error", message).build());
    }

    public Err(HttpResponseStatus code, Map<String, Object> message) {
        this.status = code;
        this.message = message;
    }

    public Err put(String key, Object value) {
        Map<String, Object> m = ImmutableMap.<String, Object>builder().putAll(message).put(key, value).build();
        return new Err(status, m);
    }

    public Err withCode(HttpResponseStatus code) {
        return new Err(code, message);
    }

    @Override
    public HttpResponseStatus status() {
        return status;
    }

    @Override
    public Object message() {
        return message;
    }

}
