/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For cases where the HttpResponseEncoder would alter headers unacceptably - if we are
 * sending a DefaultHttpResponse with a Content-Length sent, which will be followed by
 * chunks or ByteBufs sent by a listener, and HttpResponseEncoder would assume it knew
 * the response length and mis-set headers based on that.
 *
 * @author Tim Boudreau
 */
final class ListenerHackHttpResponse implements FullHttpMessage, HttpMessage, HttpResponse {

    private final HttpHeaders hdrs;
    private final EmptyByteBuf content;

    ListenerHackHttpResponse(HttpVersion ver, Channel ch, HttpHeaders hdrs, HttpResponseStatus status) {
        this.hdrs = new ListenerHackHttpHeaders(hdrs);
        this.hv = ver;
        this.content = new EmptyByteBuf(ch.alloc());
        this.status = status;
    }

    @Override
    public FullHttpMessage copy() {
        return this;
    }

    @Override
    public FullHttpMessage duplicate() {
        return this;
    }

    @Override
    public FullHttpMessage retainedDuplicate() {
        return this;
    }

    @Override
    public FullHttpMessage replace(ByteBuf bb) {
        return this;
    }

    @Override
    public FullHttpMessage retain(int i) {
        content.retain(i);
        return this;
    }

    @Override
    public FullHttpMessage retain() {
        content.retain();
        return this;
    }

    @Override
    public FullHttpMessage touch() {
        return this;
    }

    @Override
    public FullHttpMessage touch(Object o) {
        return this;
    }
    private HttpVersion hv = HttpVersion.HTTP_1_1;

    @Override
    public HttpResponse setProtocolVersion(HttpVersion hv) {
        this.hv = hv;
        return this;
    }
    HttpResponseStatus status;

    @Override
    public HttpResponse setStatus(HttpResponseStatus hrs) {
        this.status = hrs;
        return this;
    }

    @Override
    @SuppressWarnings("deprecation")
    public HttpResponseStatus getStatus() {
        return status;
    }

    @Override
    public HttpResponseStatus status() {
        return status;
    }

    @Override
    @SuppressWarnings("deprecation")
    public HttpVersion getProtocolVersion() {
        return hv;
    }

    @Override
    public HttpVersion protocolVersion() {
        return hv;
    }

    @Override
    public HttpHeaders headers() {
        return hdrs;
    }

    @Override
    @SuppressWarnings("deprecation")
    public DecoderResult getDecoderResult() {
        return DecoderResult.SUCCESS;
    }

    @Override
    public DecoderResult decoderResult() {
        return DecoderResult.SUCCESS;
    }

    @Override
    public void setDecoderResult(DecoderResult dr) {
        // do nothing
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return EmptyHttpHeaders.INSTANCE;
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int i) {
        return content.release(i);
    }

    static final class ListenerHackHttpHeaders extends HttpHeaders {

        private final HttpHeaders hdrs;

        ListenerHackHttpHeaders(HttpHeaders hdrs) {
            this.hdrs = hdrs;
        }

        @Override
        public HttpHeaders copy() {
            return new ListenerHackHttpHeaders(hdrs);
        }

        @Override
        public String toString() {
            return "ListenerHackHttpHeaders{" + hdrs + "}";
        }

        @Override
        public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
            return hdrs.contains(name, value, ignoreCase);
        }

        @Override
        public boolean containsValue(CharSequence name, CharSequence value, boolean ignoreCase) {
            return hdrs.containsValue(name, value, ignoreCase);
        }

        @Override
        public boolean contains(String name, String value, boolean ignoreCase) {
            return hdrs.contains(name, value, ignoreCase);
        }

        @Override
        public HttpHeaders remove(CharSequence name) {
            if (canMutate(name)) {
                hdrs.remove(name);
            }
            return this;
        }

        @Override
        public HttpHeaders setAll(HttpHeaders headers) {
            hdrs.setAll(headers); //XXX
            return this;
        }

        @Override
        public HttpHeaders set(HttpHeaders headers) {
            hdrs.set(headers);
            return this;
        }

        @Override
        public HttpHeaders set(CharSequence name, Iterable<?> values) {
            if (canMutate(name)) {
                hdrs.set(name, values);
            }
            return this;
        }

        @Override
        public HttpHeaders set(CharSequence name, Object value) {
            if (canMutate(name)) {
                hdrs.set(name, value);
            }
            return this;
        }

        @Override
        public HttpHeaders add(HttpHeaders headers) {
            hdrs.add(headers);
            return this;
        }

        @Override
        public HttpHeaders add(CharSequence name, Iterable<?> values) {
            if (canMutate(name)) {
                hdrs.add(name, values);
            }
            return this;
        }

        @Override
        public HttpHeaders add(CharSequence name, Object value) {
            if (canMutate(name)) {
                hdrs.add(name, value);
            }
            return super.add(name, value);
        }

        @Override
        public boolean contains(CharSequence name) {
            return hdrs.contains(name);
        }

        @Override
        public Iterator<? extends CharSequence> valueCharSequenceIterator(CharSequence name) {
            return hdrs.valueCharSequenceIterator(name);
        }

        @Override
        public Iterator<String> valueStringIterator(CharSequence name) {
            return hdrs.valueStringIterator(name);
        }

        @Override
        public String get(String string) {
            return hdrs.get(string);
        }

        @Override
        public Integer getInt(CharSequence cs) {
            return hdrs.getInt(cs);
        }

        @Override
        public int getInt(CharSequence cs, int i) {
            return hdrs.getInt(cs, i);
        }

        @Override
        public Short getShort(CharSequence cs) {
            return hdrs.getShort(cs);
        }

        @Override
        public short getShort(CharSequence cs, short s) {
            return hdrs.getShort(cs, s);
        }

        @Override
        public Long getTimeMillis(CharSequence cs) {
            return hdrs.getTimeMillis(cs);
        }

        @Override
        public long getTimeMillis(CharSequence cs, long l) {
            return hdrs.getTimeMillis(cs, l);
        }

        @Override
        public List<String> getAll(String string) {
            return hdrs.getAll(string);
        }

        @Override
        public List<Map.Entry<String, String>> entries() {
            return hdrs.entries();
        }

        @Override
        public boolean contains(String string) {
            return hdrs.contains(string);
        }

        @Override
        @SuppressWarnings("deprecation")
        public Iterator<Map.Entry<String, String>> iterator() {
            return hdrs.iterator();
        }

        @Override
        public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
            return hdrs.iteratorCharSequence();
        }

        @Override
        public boolean isEmpty() {
            return hdrs.isEmpty();
        }

        @Override
        public int size() {
            return hdrs.size();
        }

        @Override
        public Set<String> names() {
            return hdrs.names();
        }

        private boolean canMutate(CharSequence seq) {
            boolean result = !HttpHeaderNames.CONTENT_LENGTH.contentEquals(seq)
                    && !HttpHeaderNames.TRANSFER_ENCODING.contentEquals(seq);
            return result;
        }

        @Override
        public HttpHeaders add(String string, Object o) {
            if (canMutate(string)) {
                hdrs.add(string, o);
            }
            return this;
        }

        @Override
        public HttpHeaders add(String string, Iterable<?> itrbl) {
            if (canMutate(string)) {
                hdrs.add(string, itrbl);
            }
            return this;
        }

        @Override
        public HttpHeaders addInt(CharSequence cs, int i) {
            if (canMutate(cs)) {
                hdrs.addInt(cs, i);
            }
            return this;
        }

        @Override
        public HttpHeaders addShort(CharSequence cs, short s) {
            if (canMutate(cs)) {
                hdrs.addShort(cs, s);
            }
            return this;
        }

        @Override
        public HttpHeaders set(String string, Object o) {
            if (canMutate(string)) {
                hdrs.set(string, o);
            }
            return this;
        }

        @Override
        public HttpHeaders set(String string, Iterable<?> itrbl) {
            if (canMutate(string)) {
                hdrs.set(string, itrbl);
            }
            return this;
        }

        @Override
        public HttpHeaders setInt(CharSequence cs, int i) {
            if (canMutate(cs)) {
                hdrs.setInt(cs, i);
            }
            return this;
        }

        @Override
        public HttpHeaders setShort(CharSequence cs, short s) {
            if (canMutate(cs)) {
                hdrs.setShort(cs, s);
            }
            return this;
        }

        @Override
        public HttpHeaders remove(String string) {
            if (canMutate(string)) {
                hdrs.remove(string);
            }
            return this;
        }

        @Override
        public HttpHeaders clear() {
            hdrs.clear();
            return this;
        }
    }

}
