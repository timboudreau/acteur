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
package com.mastfrog.acteur;

import com.google.common.base.Objects;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mastfrog.acteur.ResponseWriter.AbstractOutput;
import com.mastfrog.acteur.ResponseWriter.Output;
import com.mastfrog.acteur.ResponseWriter.Status;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Codec;
import com.mastfrog.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.joda.time.Duration;
import io.netty.handler.ssl.SslContext;
/**
 * Aggregates the set of headers and a body writer which is used to respond to
 * an HTTP request. Each Acteur has its own which will be merged into the one
 * belonging to the page if it succeeds, so Acteurs that reject a response
 * cannot have side-effects.
 *
 * @author Tim Boudreau
 */
final class ResponseImpl extends Response {

    private volatile boolean modified;
    HttpResponseStatus status;
    private final List<Entry<?>> headers = new ArrayList<Entry<?>>(2);
    private Object message;
    ChannelFutureListener listener;
    private boolean chunked;
    private Duration delay;

    ResponseImpl() {
    }

    boolean isModified() {
        return modified;
    }

    void modify() {
        this.modified = true;
    }

    void merge(ResponseImpl other) {
        Checks.notNull("other", other);
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
            if (other.delay != null) {
                this.delay = other.delay;
            }
        }
    }

    private <T> void addEntry(Entry<T> e) {
        add(e.decorator, e.value);
    }

    public void setMessage(Object message) {
        modify();
        this.message = message;
    }

    public void setDelay(Duration delay) {
        modify();
        this.delay = delay;
    }

    public void setResponseCode(HttpResponseStatus status) {
        modify();
        this.status = status;
    }

    public HttpResponseStatus getResponseCode() {
        return status == null ? HttpResponseStatus.OK : status;
    }

    @Override
    public void setBodyWriter(ResponseWriter writer) {
        Page p = Page.get();
        Application app = p.getApplication();
        Dependencies deps = app.getDependencies();
        HttpEvent evt = deps.getInstance(HttpEvent.class);
        Charset charset = deps.getInstance(Charset.class);
        ByteBufAllocator allocator = deps.getInstance(ByteBufAllocator.class);
        Codec mapper = deps.getInstance(Codec.class);
        Key<ExecutorService> key = Key.get(ExecutorService.class,
                Names.named(ServerModule.BACKGROUND_THREAD_POOL_NAME));
        ExecutorService svc = deps.getInstance(key);
        setWriter(writer, charset, allocator, mapper, evt, svc);
    }

    Duration getDelay() {
        return delay;
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
            if (Names.CONTENT_LENGTH.equals(name)) {
                return this;
            }
            return orig.add(name, values);
        }

        @Override
        public HttpHeaders set(String name, Object value) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            if (Names.CONTENT_LENGTH.equals(name)) {
                return this;
            }
//            if (Names.CONTENT_ENCODING.equals(name) && !"true".equals(orig.get("X-Internal-Compress"))) {
//                return this;
//            }
            return orig.set(name, value);
        }

        @Override
        public HttpHeaders set(String name, Iterable<?> values) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            if (Names.CONTENT_LENGTH.equals(name)) {
                return this;
            }
            if (Names.CONTENT_ENCODING.equals(name)) {
                return this;
            }
            return orig.set(name, values);
        }

        @Override
        public HttpHeaders remove(String name) {
            if (Names.TRANSFER_ENCODING.equals(name)) {
                return this;
            }
            if (Names.CONTENT_LENGTH.equals(name)) {
                return this;
            }
            if (Names.CONTENT_ENCODING.equals(name)) {
                return this;
            }
            return orig.remove(name);
        }

        @Override
        public HttpHeaders addInt(CharSequence name, int i) {
            if (Names.CONTENT_LENGTH.contentEquals(name)) {
                return this;
            }
            orig.addInt(name, i);
            return this;
        }

        @Override
        public HttpHeaders addShort(CharSequence name, short s) {
            if (Names.CONTENT_LENGTH.contentEquals(name)) {
                return this;
            }
            orig.addShort(name, s);
            return this;
        }

        @Override
        public HttpHeaders setInt(CharSequence name, int s) {
            if (Names.CONTENT_LENGTH.contentEquals(name)) {
                return this;
            }
            orig.setInt(name, s);
            return this;
        }

        @Override
        public HttpHeaders setShort(CharSequence name, short s) {
            if (Names.CONTENT_LENGTH.contentEquals(name)) {
                return this;
            }
            orig.setShort(name, s);
            return this;
        }

        @Override
        public HttpHeaders clear() {
            return orig.clear();
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return orig.iterator();
        }

        @Override
        public Integer getInt(CharSequence cs) {
            return orig.getInt(cs);
        }

        @Override
        public int getInt(CharSequence cs, int i) {
            return orig.getInt(cs);
        }

        @Override
        public Short getShort(CharSequence cs) {
            return orig.getShort(cs);
        }

        @Override
        public short getShort(CharSequence cs, short s) {
            return orig.getShort(cs, s);
        }

        @Override
        public Long getTimeMillis(CharSequence cs) {
            return orig.getTimeMillis(cs);
        }

        @Override
        public long getTimeMillis(CharSequence cs, long l) {
            return orig.getTimeMillis(cs, l);
        }

        @Override
        public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
            return orig.iteratorCharSequence();
        }

        @Override
        public int size() {
            return orig.size();
        }
    }

    private static class HackHttpResponse extends DefaultHttpResponse {

        private final HttpHeaders hdrs;
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

    private String cookieName(Object o) {
        if (o instanceof Cookie) {
            return ((Cookie) o).name();
        } else if (o instanceof io.netty.handler.codec.http.Cookie) {
            return ((io.netty.handler.codec.http.Cookie) o).name();
        } else {
            return null;
        }
    }

    private boolean compareCookies(Object old, Object nue) {
        return Objects.equal(cookieName(old), cookieName(nue));
    }

    @SuppressWarnings("unchecked")
    public <T> void add(HeaderValueType<T> decorator, T value) {
        List<Entry<?>> old = new LinkedList<>();
        // XXX set cookie!
        for (Iterator<Entry<?>> it = headers.iterator(); it.hasNext();) {
            Entry<?> e = it.next();
            // Do prune setting the same cookie twice
            if (decorator.name().equals(HttpHeaders.Names.SET_COOKIE) && e.decorator.name().equals(HttpHeaders.Names.SET_COOKIE)) {
                if (compareCookies(e.value, value)) {
                    it.remove();
                    continue;
                } else {
                    continue;
                }
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

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
        modify();
    }

    <T extends ResponseWriter> void setWriter(T w, Dependencies deps, HttpEvent evt) {
//        setChunked(true);
        Charset charset = deps.getInstance(Charset.class);
        ByteBufAllocator allocator = deps.getInstance(ByteBufAllocator.class);
        Codec mapper = deps.getInstance(Codec.class);
        Key<ExecutorService> key = Key.get(ExecutorService.class, Names.named(ServerModule.BACKGROUND_THREAD_POOL_NAME));
        ExecutorService svc = deps.getInstance(key);
        setWriter(w, charset, allocator, mapper, evt, svc);
    }

    <T extends ResponseWriter> void setWriter(Class<T> w, Dependencies deps, HttpEvent evt) {
//        setChunked(true);
        Charset charset = deps.getInstance(Charset.class);
        ByteBufAllocator allocator = deps.getInstance(ByteBufAllocator.class);
        Key<ExecutorService> key = Key.get(ExecutorService.class, Names.named(ServerModule.BACKGROUND_THREAD_POOL_NAME));
        ExecutorService svc = deps.getInstance(key);
        Codec mapper = deps.getInstance(Codec.class);
        setWriter(new DynResponseWriter(w, deps), charset, allocator, mapper, evt, svc);
    }

    static class DynResponseWriter extends ResponseWriter {

        private final AtomicReference<ResponseWriter> actual = new AtomicReference<>();
        private final Callable<ResponseWriter> resp;

        public DynResponseWriter(final Class<? extends ResponseWriter> type, final Dependencies deps) {
            ReentrantScope scope = deps.getInstance(ReentrantScope.class);
            assert scope.inScope();
            resp = scope.wrap(new Callable<ResponseWriter>() {

                @Override
                public ResponseWriter call() throws Exception {
                    ResponseWriter w = actual.get();
                    if (w == null) {
                        actual.set(w = deps.getInstance(type));
                    }
                    return w;
                }
            });
        }

        @Override
        public ResponseWriter.Status write(Event<?> evt, Output out) throws Exception {
            ResponseWriter actual = resp.call();
            return actual.write(evt, out);
        }

        @Override
        public Status write(Event<?> evt, Output out, int iteration) throws Exception {
            ResponseWriter actual = resp.call();
            return actual.write(evt, out, iteration);
        }
    }

    static boolean isKeepAlive(Event<?> evt) {
        return evt instanceof HttpEvent ? ((HttpEvent) evt).isKeepAlive() : false;
    }

    void setWriter(ResponseWriter w, Charset charset, ByteBufAllocator allocator, Codec mapper, Event<?> evt, ExecutorService svc) {
        setBodyWriter(new ResponseWriterListener(evt, w, charset, allocator,
                mapper, chunked, !isKeepAlive(evt), svc));
    }

    private static final class ResponseWriterListener extends AbstractOutput implements ChannelFutureListener {

        private volatile ChannelFuture future;
        private volatile int callCount = 0;
        private final boolean chunked;
        private final ResponseWriter writer;
        private final boolean shouldClose;
        private final Event<?> evt;
        private final ExecutorService svc;

        public ResponseWriterListener(Event<?> evt, ResponseWriter writer, Charset charset,
                ByteBufAllocator allocator, Codec mapper, boolean chunked,
                boolean shouldClose, ExecutorService svc) {
            super(charset, allocator, mapper);
            this.chunked = chunked;
            this.writer = writer;
            this.shouldClose = shouldClose;
            this.evt = evt;
            this.svc = svc;
        }

        public Channel channel() {
            if (future == null) {
                throw new IllegalStateException("No future -> no channel");
            }
            return future.channel();
        }

        @Override
        public Output write(ByteBuf buf) throws IOException {
            assert future != null;
            if (chunked) {
                future = future.channel().writeAndFlush(new DefaultHttpContent(buf));
            } else {
                future = future.channel().writeAndFlush(buf);
            }
            return this;
        }

        volatile boolean inOperationComplete;
        volatile int entryCount = 0;

        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
            try {
                // See https://github.com/netty/netty/issues/2415 for why this is needed
                if (entryCount > 0) {
                    svc.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                operationComplete(future);
                            } catch (Exception ex) {
                                Exceptions.chuck(ex);
                            }
                        }
                    });
                    return;
                }
                entryCount++;
                Callable<Void> c = new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        inOperationComplete = true;
                        try {
                            ResponseWriterListener.this.future = future;
                            ResponseWriter.Status status = writer.write(evt, ResponseWriterListener.this, callCount++);
                            if (status.isCallback()) {
                                ResponseWriterListener.this.future = ResponseWriterListener.this.future.addListener(ResponseWriterListener.this);
                            } else if (status == Status.DONE) {
                                if (chunked) {
                                    ResponseWriterListener.this.future = ResponseWriterListener.this.future.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                                }
                                if (shouldClose) {
                                    ResponseWriterListener.this.future = ResponseWriterListener.this.future.addListener(CLOSE);
                                }
                            }
                        } finally {
                            inOperationComplete = false;
                        }
                        return null;
                    }
                };
                if (!inOperationComplete) {
                    c.call();
                } else {
                    svc.submit(c);
                }
            } finally {
                entryCount--;
            }
        }

        @Override
        public ChannelFuture future() {
            return future;
        }

        @Override
        public Output write(HttpContent chunk) throws IOException {
            if (!chunked) {
                ResponseWriterListener.this.future = ResponseWriterListener.this.future.channel().writeAndFlush(chunk.content());
            } else {
                ResponseWriterListener.this.future = ResponseWriterListener.this.future.channel().writeAndFlush(chunk);
            }
            return this;
        }
    }

    /**
     * Set a ChannelFutureListener which will be called after headers are
     * written and flushed to the socket; prefer
     * <code>setResponseWriter()</code> to this method unless you are not using
     * chunked encoding and want to stream your response (in which case, be sure
     * to setChunked(false) or you will have encoding errors).
     *
     * @param listener
     */
    public void setBodyWriter(ChannelFutureListener listener) {
        if (this.listener != null) {
            throw new IllegalStateException("Listener already set to " + this.listener);
        }
        this.listener = listener;
    }

    public Object getMessage() {
        return message;
    }

    final boolean canHaveBody(HttpResponseStatus status) {
        switch (status.code()) {
            case 204:
            case 205:
            case 304:
                return false;
            default:
                return true;
        }
    }

    private ByteBuf writeMessage(Event<?> evt, Charset charset) {
        Object message = getMessage();
        if (message == null) {
            return null;
        }
        if (message instanceof ByteBuf) {
            return (ByteBuf) message;
        }
        if (message instanceof byte[]) {
            return Unpooled.wrappedBuffer((byte[]) message);
        }
        if (message instanceof ByteBuffer) {
            return Unpooled.wrappedBuffer(((ByteBuffer) message));
        }
        if (message instanceof CharSequence) {
            return evt.getChannel().alloc().buffer().writeBytes(message.toString().getBytes(charset));
        }
        Page p = Page.get();
        if (p == null) {
            throw new IllegalStateException("Call to write message outside request scope");
        }
        Application app = p.getApplication();
        Dependencies deps = app.getDependencies();
        Codec codec = deps.getInstance(Codec.class);
        ByteBuf result = evt.getChannel().alloc().buffer();
        try (OutputStream out = new ByteBufOutputStream(result)) {
            codec.writeValue(message, out);
            return result;
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public HttpResponse toResponse(Event<?> evt, Charset charset) {
        if (!canHaveBody(getResponseCode()) && (message != null || listener != null)) {
            if (listener != ChannelFutureListener.CLOSE) {
                System.err.println(evt
                        + " attempts to attach a body to " + getResponseCode()
                        + " which cannot have one: " + message
                        + " - " + listener);
            }
        }
        ByteBuf buf = writeMessage(evt, charset);
        HttpResponse resp;
        if (buf != null) {
            long size = buf.readableBytes();
            add(Headers.CONTENT_LENGTH, size);
            DefaultFullHttpResponse r = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, getResponseCode(), buf);
            resp = r;
        } else {
            resp = new HackHttpResponse(getResponseCode(), this.status == NOT_MODIFIED ? false : chunked);
        }
        for (Entry<?> e : headers) {
            // Remove things which cause problems for non-modified responses -
            // browsers will hold the connection open regardless
            if (this.status == NOT_MODIFIED) {
                if (e.decorator == Headers.CONTENT_LENGTH) {
                    continue;
                } else if (HttpHeaders.Names.CONTENT_ENCODING.equals(e.decorator.name())) {
                    continue;
                } else if (Headers.TRANSFER_ENCODING.name().equals(e.decorator.name())) {
                    continue;
                }
            }
            e.write(resp);
        }
        // Ensure a 0 content length is present for items with no content
        if (message == null && listener == null && resp.headers() instanceof HackHttpHeaders) {
            ((HackHttpHeaders) resp.headers()).orig.set(Headers.CONTENT_LENGTH.name(), 0);
            ((HackHttpHeaders) resp.headers()).remove(Headers.TRANSFER_ENCODING.name());
            ((HackHttpHeaders) resp.headers()).remove(Headers.CONTENT_ENCODING.name());
        }
        return resp;
    }

    ChannelFuture sendMessage(Event<?> evt, ChannelFuture future, HttpMessage resp) {
        if (listener != null) {
            future = future.addListener(listener);
            return future;
        } else if (!isKeepAlive(evt)) {
            future = future.addListener(ChannelFutureListener.CLOSE);
        }
        return future;
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
            Checks.notNull(decorator.name().toString(), value);
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
