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

import com.mastfrog.acteur.util.HeaderValueType;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.util.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import static io.netty.handler.codec.http.HttpHeaders.addHeader;
import static io.netty.handler.codec.http.HttpHeaders.removeHeader;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates the set of headers and a body writer which is used to respond to
 * an HTTP request.
 *
 * @author Tim Boudreau
 */
final class ResponseImpl extends Response {

    private volatile boolean modified;
    HttpResponseStatus status;
    private final List<Entry<?>> headers = Collections.synchronizedList(new ArrayList<Entry<?>>());
    private String message;
    ChannelFutureListener listener;
    private boolean chunked;

    ResponseImpl() {
    }

    boolean isModified() {
        return modified;
    }

    void modify() {
        this.modified = true;
    }

    void merge(ResponseImpl other) {
        this.modified |= other.modified;
        if (other.modified) {
            for (Entry<?> e : other.headers) {
                addEntry(e);
            }
            if (other.status != null) {
                setResponseCode(other.status);
            }
            if (other.message != null) {
                setMessage(other.message);
            }
            if (other.chunked) {
                setChunked(true);
            }
            if (other.listener != null) {
                setBodyWriter(other.listener);
            }
        }
    }

    private <T> void addEntry(Entry<T> e) {
        add(e.decorator, e.value);
    }

    public void setMessage(String message) {
        modify();
        this.message = message;
    }

    public void setResponseCode(HttpResponseStatus status) {
        modify();
        this.status = status;
    }

    public HttpResponseStatus getResponseCode() {
        return status == null ? HttpResponseStatus.OK : status;
    }

    static class HackHttpHeaders extends HttpHeaders {

        private final HttpHeaders orig;

        public HackHttpHeaders(HttpHeaders orig, boolean chunked) {
            this.orig = orig;
            if (chunked) {
                orig.set(Names.TRANSFER_ENCODING, Values.CHUNKED);
                orig.remove(Names.CONTENT_LENGTH);
            } else {
                orig.remove(Names.TRANSFER_ENCODING);
            }
        }

        @Override
        public String get(String name) {
            return orig.get(name);
        }

        @Override
        public List<String> getAll(String name) {
            return orig.getAll(name);
        }

        @Override
        public List<Map.Entry<String, String>> entries() {
            return orig.entries();
        }

        @Override
        public boolean contains(String name) {
            return orig.contains(name);
        }

        @Override
        public boolean isEmpty() {
            return orig.isEmpty();
        }

        @Override
        public Set<String> names() {
            return orig.names();
        }

        @Override
        public HttpHeaders add(String name, Object value) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            return orig.add(name, value);
        }

        @Override
        public HttpHeaders add(String name, Iterable<?> values) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            return orig.add(name, values);
        }

        @Override
        public HttpHeaders set(String name, Object value) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            return orig.set(name, value);
        }

        @Override
        public HttpHeaders set(String name, Iterable<?> values) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            return orig.set(name, values);
        }

        @Override
        public HttpHeaders remove(String name) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            return orig.remove(name);
        }

        @Override
        public HttpHeaders clear() {
            return orig.clear();
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return orig.iterator();
        }
    }

    private static class HackHttpResponse extends DefaultHttpResponse {
        private final HackHttpHeaders hdrs;
        // Workaround for https://github.com/netty/netty/issues/1326

        HackHttpResponse(HttpResponseStatus status, boolean chunked) {
            super(HttpVersion.HTTP_1_1, status);
            hdrs = new HackHttpHeaders(super.headers(), chunked);
        }

        @Override
        public HttpHeaders headers() {
            return hdrs;
        }
    }

    public HttpResponse toResponse(Event evt) {
        if (!canHaveBody(getResponseCode()) && (message != null || listener != null)) {
            System.err.println(evt.getMethod() + " " + evt.getPath()
                    + " attempts to attach a body to " + getResponseCode()
                    + " which cannot have one: " + message
                    + " - " + listener);
//            if (closer != null) {
//                future.addListener(closer);
//            }
//            return;
        }
        String msg = getMessage();
        HttpResponse resp;
        if (msg != null) {
            ByteBuf buf = Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8);
            long size = buf.readableBytes();
            add(Headers.CONTENT_LENGTH, size);
            DefaultFullHttpResponse r = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, getResponseCode(), buf);

            resp = r;
        } else {
            resp = new HackHttpResponse(getResponseCode(), chunked);
        }
        for (Entry<?> e : headers) {
            e.write(resp);
        }
//        if (chunked) {
//            HttpHeaders.setTransferEncodingChunked(resp);
//        } else {
//            HttpHeaders.removeTransferEncodingChunked(resp);
//        }
        return resp;
    }

    public <T> void add(HeaderValueType<T> decorator, T value) {
        List<Entry<?>> old = new LinkedList<>();
        // XXX set cookie!
        for (Iterator<Entry<?>> it = headers.iterator(); it.hasNext();) {
            Entry<?> e = it.next();
            if (e.decorator.equals(Headers.SET_COOKIE)) {
                continue;
            }
            if (e.match(decorator) != null) {
                old.add(e);
                it.remove();
            }
        }
        Entry<?> e = new Entry<>(decorator, value);
        // For now, special handling for Allow:
        // Longer term, should HeaderValueType.isArray() and a way to 
        // coalesce
        if (!old.isEmpty() && decorator == Headers.ALLOW) {
            old.add(e);
            Set<Method> all = new HashSet<>();
            for (Entry<?> en : old) {
                Method[] m = (Method[]) en.value;
                all.addAll(Arrays.asList(m));
            }
            value = (T) all.toArray(new Method[0]);
            e = new Entry<>(decorator, value);
        }
        headers.add(e);
        modify();
    }

    public <T> T get(HeaderValueType<T> decorator) {
        for (Entry<?> e : headers) {
            HeaderValueType<T> d = e.match(decorator);
            if (d != null) {
                return d.type().cast(e.value);
            }
        }
        return null;
    }

    void setChunked(boolean chunked) {
        this.chunked = chunked;
        modify();
    }

    public void setBodyWriter(ChannelFutureListener listener) {
//        modify();
        if (this.listener != null) {
            throw new IllegalStateException("Listener already set to " + this.listener);
        }
        this.listener = listener;
    }

    public String getMessage() {
        return message;
    }

    public boolean canHaveBody(HttpResponseStatus status) {
        switch (status.code()) {
            case 204:
            case 205:
            case 304:
                return false;
            default:
                return true;
        }
    }

    void sendMessage(Event evt, ChannelFuture future, HttpMessage resp, final ChannelFutureListener closer) {
        for (Map.Entry<String, String> e : resp.headers().entries()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }

        if (!future.channel().isOpen()) {
//            return;
        }

        if (listener != null) {
            future.addListener(listener);
            return;
        }
        if (getMessage() == null) {
            if (closer != null) {
                future.addListener(closer);
            }
        }
    }

    @Override
    public String toString() {
        return "Response{" + "modified=" + modified + ", status=" + status + ", headers=" + headers + ", message=" + message + ", listener=" + listener + ", chunked=" + chunked + " has listener " + (this.listener != null) + '}';
    }

    private static final class Entry<T> {

        private final HeaderValueType<T> decorator;
        private final T value;

        Entry(HeaderValueType<T> decorator, T value) {
            Checks.notNull("decorator", decorator);
            Checks.notNull(decorator.name(), value);
//            assert value == null || decorator.type().isInstance(value) :
//                    value + " of type " + value.getClass() + " is not a " + decorator.type();
            this.decorator = decorator;
            this.value = value;
        }

        public void decorate(HttpMessage msg) {
            msg.headers().set(decorator.name(), value);
        }

        public void write(HttpMessage msg) {
            Headers.write(decorator, value, msg);
        }

        @Override
        public String toString() {
            return decorator.name() + ": " + decorator.toString(value);
        }

        @Override
        public int hashCode() {
            return decorator.name().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Entry<?> && ((Entry<?>) o).decorator.name().equals(decorator.name());
        }

        @SuppressWarnings({"unchecked"})
        public <R> HeaderValueType<R> match(HeaderValueType<R> decorator) {
            if (decorator == this.decorator) {
                return (HeaderValueType<R>) this.decorator;
            }
            if (this.decorator.name().equals(decorator.name())
                    && this.decorator.type().equals(decorator.type())) {
                return decorator;
            }
            return null;
        }
    }
}
