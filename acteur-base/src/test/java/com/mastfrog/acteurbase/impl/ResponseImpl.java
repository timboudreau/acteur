/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteurbase.impl;

import com.mastfrog.acteur.headers.HeaderValueType;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class ResponseImpl implements Response {

    private final List<Entry<?>> entries = new LinkedList<>();
    private final ResponseImpl parent;
    private boolean modified;

    ResponseImpl() {
        this.parent = null;
    }

    ResponseImpl(ResponseImpl parent) {
        this.parent = parent;
    }

    private HttpResponseStatus status;

    public HttpResponseStatus status() {
        return status;
    }

    public void setStatus(HttpResponseStatus status) {
        modified = true;
        this.status = status;
    }

    @Override
    public <T> Response add(HeaderValueType<T> header, T value) {
        modified = true;
        entries.add(new Entry<T>(header, value));
        return this;
    }

    boolean modified() {
        return modified;
    }

    @Override
    public void setResponseBodyWriter(ChannelFutureListener l) {
        modified = true;
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (status != null) {
            sb.append(status).append(": ");
        }
        for (Entry<?> e : entries) {
            sb.append(e);
            sb.append(';');
        }
        return sb.toString();
    }

    private Object message;

    @Override
    public void setMessage(Object message) {
        modified = true;
        this.message = message;
    }

    private static final class Entry<T> {

        private final HeaderValueType<T> header;
        private final T value;

        public Entry(HeaderValueType<T> header, T value) {
            this.header = header;
            this.value = value;
        }

        public String toString() {
            return header.name() + "=" + value;
        }
    }
}
