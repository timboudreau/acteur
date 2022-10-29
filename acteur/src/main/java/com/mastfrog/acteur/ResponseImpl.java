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

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mastfrog.acteur.ResponseWriter.AbstractOutput;
import com.mastfrog.acteur.ResponseWriter.Output;
import com.mastfrog.acteur.ResponseWriter.Status;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.X_INTERNAL_COMPRESS;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.marshallers.netty.NettyContentMarshallers;
import com.mastfrog.mime.MimeType;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.thread.ThreadLocalTransfer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import io.netty.handler.codec.http.HttpHeaderValues;
import static io.netty.handler.codec.http.HttpHeaderValues.IDENTITY;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.HttpVersion;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aggregates the set of headers and a body writer which is used to respond to
 * an HTTP request. Each Acteur has its own which will be merged into the one
 * belonging to the page if it succeeds, so Acteurs that reject a response
 * cannot have side-effects.
 *
 * @author Tim Boudreau
 */
final class ResponseImpl extends Response {

    static boolean DEBUG_BAD_LISTENER_STRINGS = Boolean.getBoolean("acteur.debug.listeners.strings");
    private volatile boolean modified;
    HttpResponseStatus status;
    private final List<Entry<?>> headers = new ArrayList<>(3);
    private Object message;
    ChannelFutureListener listener;
    private boolean chunked;
    private Duration delay;
    private static final boolean debug = Boolean.getBoolean("acteur.debug");

    static final ThreadLocalTransfer<List<ResponseImpl>> shadowResponses = new ThreadLocalTransfer<>();
    List<ResponseImpl> alsoConsult;

    ResponseImpl() {
        // Ensure's an Acteur's call to response().get(Headers.FOO) can see
        // values set earlier in the chain.  Needed to get rid of ResponseHeaders
        // and page.decorateResponse().
        List<ResponseImpl> previousActeursResponses = shadowResponses.get();
        if (previousActeursResponses != null) {
            alsoConsult = CollectionUtils.reversed(previousActeursResponses);
        }
    }

    boolean hasListener() {
        return listener != null;
    }

    Object message() {
        return message;
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
                status(other.status);
            }
            if (other.message != null) {
                content(other.message);
            }
            if (other.chunked) {
                chunked(true);
            }
            if (other.listener != null) {
                contentWriter(other.listener);
            }
            if (other.delay != null) {
                this.delay = other.delay;
            }
        }
    }

    private <T> void addEntry(Entry<T> e) {
        add(e.decorator, e.value);
    }

    @Override
    public Response content(Object message) {
        modify();
        this.message = message;
        return this;
    }

    @Override
    public Response delayedBy(Duration delay) {
        modify();
        this.delay = delay;
        return this;
    }

    @Override
    public Response status(HttpResponseStatus status) {
        modify();
        this.status = status;
        return this;
    }

    HttpResponseStatus rawStatus() {
        return status;
    }

    public HttpResponseStatus getResponseCode() {
        if (status == null && alsoConsult != null) {
            for (ResponseImpl previous : alsoConsult) {
                HttpResponseStatus raw = previous.rawStatus();
                if (raw != null) {
                    return raw;
                }
            }
        }
        return status == null ? HttpResponseStatus.OK : status;
    }

    @Override
    public Response contentWriter(ResponseWriter writer) {
        Page p = Page.get();
        Application app = p.getApplication();
        Dependencies deps = app.getDependencies();
        HttpEvent evt = deps.getInstance(HttpEvent.class);
        Charset charset = deps.getInstance(Charset.class);
        ByteBufAllocator allocator = deps.getInstance(ByteBufAllocator.class);
        Codec mapper = deps.getInstance(Codec.class);
        Key<ExecutorService> key = Key.get(ExecutorService.class,
                Names.named(ServerModule.WORKER_THREAD_POOL_NAME));
        ExecutorService svc = deps.getInstance(key);
        setWriter(writer, charset, allocator, mapper, evt, svc, app.control());
        return this;
    }

    Duration getDelay() {
        return delay;
    }

    @SuppressWarnings("deprecation")
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
        return Objects.equals(cookieName(old), cookieName(nue));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Response add(HeaderValueType<T> decorator, T value) {
        List<Entry<?>> old = new LinkedList<>();
        // XXX set cookie!
        for (Iterator<Entry<?>> it = headers.iterator(); it.hasNext();) {
            Entry<?> e = it.next();
            // Do prune setting the same cookie twice
            if (decorator.is(HttpHeaderNames.SET_COOKIE)
                    && e.decorator.is(HttpHeaderNames.SET_COOKIE)) {
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
        if (!old.isEmpty() && decorator.is(HttpHeaderNames.ALLOW)) {
            old.add(e);
            Set<Method> all = EnumSet.noneOf(Method.class);
            for (Entry<?> en : old) {
                Method[] m = (Method[]) en.value;
                all.addAll(Arrays.asList(m));
            }
            value = (T) all.toArray(new Method[all.size()]);
            e = new Entry<>(decorator, value);
        }
        headers.add(e);
        modify();
        return this;
    }

    @Override
    public <T> T get(HeaderValueType<T> decorator) {
        T result = internalGet(decorator);
        if (result == null && alsoConsult != null) {
            for (ResponseImpl preceding : alsoConsult) {
                result = preceding.internalGet(decorator);
                if (result != null) {
                    break;
                }
            }
        }
        return result;
    }

    <T> T internalGet(HeaderValueType<T> headerType) {
        for (Entry<?> e : headers) {
            HeaderValueType<T> d = e.match(headerType);
            if (d != null) {
                return d.type().cast(e.value);
            }
        }
        return null;
    }

    @Override
    public Response chunked(boolean chunked) {
        if (chunked != this.chunked) {
            this.chunked = chunked;
            modify();
        }
        return this;
    }

    <T extends ResponseWriter> void setWriter(T w, Dependencies deps, HttpEvent evt) {
        Charset charset = deps.getInstance(Charset.class);
        ByteBufAllocator allocator = deps.getInstance(ByteBufAllocator.class);
        Codec mapper = deps.getInstance(Codec.class);
        Key<ExecutorService> key = Key.get(ExecutorService.class, Names.named(ServerModule.WORKER_THREAD_POOL_NAME));
        ExecutorService svc = deps.getInstance(key);
        ApplicationControl ctrl = deps.getInstance(ApplicationControl.class);
        setWriter(w, charset, allocator, mapper, evt, svc, ctrl);
    }

    <T extends ResponseWriter> void setWriter(Class<T> w, Dependencies deps, HttpEvent evt) {
        Charset charset = deps.getInstance(Charset.class);
        ByteBufAllocator allocator = deps.getInstance(ByteBufAllocator.class);
        Key<ExecutorService> key = Key.get(ExecutorService.class, Names.named(ServerModule.WORKER_THREAD_POOL_NAME));
        ExecutorService svc = deps.getInstance(key);
        Codec mapper = deps.getInstance(Codec.class);
        setWriter(new DynResponseWriter(w, deps), charset, allocator, mapper, evt, svc,
                deps.getInstance(ApplicationControl.class));
    }

    static class DynResponseWriter extends ResponseWriter {

        private final AtomicReference<ResponseWriter> actual = new AtomicReference<>();
        private final Callable<ResponseWriter> resp;

        DynResponseWriter(final Class<? extends ResponseWriter> type, final Dependencies deps) {
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

                public String toString() {
                    return type.toString();
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

        public String toString() {
            return "DynResponseWriter for " + resp.toString();
        }
    }

    private boolean hasTransferEncodingChunked() {
        for (Entry<?> entry : headers) {
            if (Headers.TRANSFER_ENCODING.is(entry.decorator.name())) {
                return Strings.charSequencesEqual(HttpHeaderValues.CHUNKED, entry.stringValue(), true);
            }
        }
        return false;
    }

    private boolean hasContentLength() {
        for (Entry<?> entry : headers) {
            if (Headers.CONTENT_LENGTH.equals(entry.decorator)) {
                return Strings.charSequencesEqual(HttpHeaderValues.CHUNKED, entry.stringValue(), true);
            }
        }
        return false;

    }

    private boolean isHttp10StyleResponse() {
        // If no content length is set, but a listener is present, and transfer encoding
        // is not set, then we are going to fire raw ByteBufs down the pipe and we need
        // to close the connection to indicate that the response is finished
        return listener != null && !chunked && !hasTransferEncodingChunked() && !hasContentLength();
    }

    boolean isKeepAlive(Event<?> evt) {
        boolean result = evt instanceof HttpEvent ? ((HttpEvent) evt).requestsConnectionStayOpen() : false;
        if (result) {
            result = !isHttp10StyleResponse();
        }
        return result;
    }

    void setWriter(ResponseWriter w, Charset charset, ByteBufAllocator allocator,
            Codec mapper, Event<?> evt, ExecutorService svc, ApplicationControl ctrl) {
        contentWriter(new ResponseWriterListener(evt, w, charset, allocator,
                mapper, chunked, !isKeepAlive(evt), svc, ctrl));
    }

    String listenerString() {
        if (listener != null) {
            if (listener instanceof ResponseWriterListener) {
                return checkValidCharacters(((ResponseWriterListener) listener).writer.getClass().getName());
            }
            if (listener instanceof Acteur.ScopeWrapper || listener instanceof Acteur.IWrapper) {
                return checkValidCharacters(listener.toString());
            }
            return checkValidCharacters(listener.getClass().getName());
        }
        return "no-listener";
    }

    static String checkValidCharacters(String ls) {
         // Comment this if we have acteurs returning garbage strings from
        // toString() which contain 0x0 - these will cause an exception from
        // netty
        if (!DEBUG_BAD_LISTENER_STRINGS) {
            return ls;
        }
        for (int i = 0; i < ls.length(); i++) {
            char c = ls.charAt(i);
            if (c == 0) {
                new IllegalArgumentException("Bad string '" + ls + "'").printStackTrace();
                return "bad-listener-string";
            }
        }
        return ls;
    }

    private static final class ResponseWriterListener extends AbstractOutput implements ChannelFutureListener {

        private volatile ChannelFuture future;
        private volatile int callCount = 0;
        private final boolean chunked;
        final ResponseWriter writer;
        private final boolean shouldClose;
        private final Event<?> evt;
        private final ExecutorService svc;
        private final ApplicationControl ctrl;

        ResponseWriterListener(Event<?> evt, ResponseWriter writer, Charset charset,
                ByteBufAllocator allocator, Codec mapper, boolean chunked,
                boolean shouldClose, ExecutorService svc, ApplicationControl ctrl) {
            super(charset, allocator, mapper);
            this.chunked = chunked;
            this.writer = writer;
            this.shouldClose = shouldClose;
            this.evt = evt;
            this.svc = svc;
            this.ctrl = ctrl;
        }

        @Override
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
            if (future.cause() != null) {
                ctrl.internalOnError(future.cause());
                if (future.channel() != null && future.channel().isOpen()) {
                    future.channel().close();
                }
                return;
            }
            try {
                // See https://github.com/netty/netty/issues/2415 for why this is needed
                if (entryCount > 0) {
                    svc.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                operationComplete(future);
                            } catch (Exception ex) {
                                ctrl.internalOnError(ex);
                                if (future != null && future.channel().isOpen()) {
                                    future.channel().close();
                                }
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
                        } catch (Exception ex) {
                            ctrl.internalOnError(ex);
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

        @Override
        public Output write(FileRegion region) throws IOException {
            ResponseWriterListener.this.future = ResponseWriterListener.this.future.channel().writeAndFlush(region);
            if (shouldClose) {
                future.addListener(CLOSE);
            }
            return this;
        }
    }

    /**
     * Set a ChannelFutureListener which will be called after headers are
     * written and flushed to the socket; prefer
     * <code>setResponseWriter()</code> to this method unless you are not using
     * chunked encoding and want to stream your response (in which case, be sure
     * to chunked(false) or you will have encoding errors).
     *
     * @param listener
     */
    @Override
    public Response contentWriter(ChannelFutureListener listener) {
        if (this.listener != null) {
            throw new IllegalStateException("Listener already set to " + this.listener);
        }
        this.listener = listener;
        return this;
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

    private ByteBuf writeMessage(Event<?> evt, Charset charset) throws Exception {
        if (message == null) {
            return null;
        }
        if (message instanceof ByteBuf) {
            return (ByteBuf) message;
        }
        Page p = Page.get();
        if (p == null) {
            throw new IllegalStateException("Call to write message with Page.set() not called (outside request scope?)");
        }
        NettyContentMarshallers marshallers = p.getApplication().getDependencies().getInstance(NettyContentMarshallers.class);
        ByteBuf buf = evt.channel().alloc().ioBuffer();
        buf.touch("response-impl-write-message");
        marshallers.write(message, buf, charset);
        return buf;
    }

    HttpResponseStatus internalStatus() {
        return status == null ? OK : status;
    }

    private boolean has(CharSequence headerName) {
        for (Entry<?> e : this.headers) {
            if (e.is(headerName)) {
                return true;
            }
        }
        return false;
    }

    boolean hasNoPayload() {
        HttpResponseStatus status = internalStatus();
        if (status == NOT_MODIFIED || status == NO_CONTENT) {
            return true;
        }
        if (listener == null && (message == null || (message instanceof String && ((String) message).isEmpty()))) {
            return true;
        }
        return false;
    }

    private static final AsciiString ZERO = AsciiString.of("0");

    public HttpResponse toResponse(Event<?> evt, Charset defaultCharset) throws Exception {
        HttpResponseStatus status = internalStatus();
        // Log cases where a payload is attached to a status code that cannot have a payload
        // according to the HTTP spec
        if (!canHaveBody(status) && (message != null || listener != null)) {
            if (debug && listener != ChannelFutureListener.CLOSE && listener != SEND_EMPTY_LAST_CHUNK) {
                System.err.println(evt
                        + " attempts to attach a body to " + status
                        + " which cannot have one: " + message
                        + " - " + listener);
            }
        }
        // Ensure we pass the correct character set based on the MIME type and failing
        // over to the character set the application was configured with (default UTF-8):
        MimeType mimeType = get(CONTENT_TYPE);
        if (mimeType != null && mimeType.charset().isPresent()) {
            defaultCharset = mimeType.charset().get();
        }
        // Convert the message payload, if any, into a ByteBuf
        ByteBuf buf = writeMessage(evt, defaultCharset);
        // If this happens, the application is telling the framework to do two contradictory things -
        // you can either send a payload by attaching it to the response, or by attaching a listener
        // which will be notified when the headers have been written (or flushed) to the socket
        // buffer - but not both
        if (buf != null && listener != null) {
            throw new IllegalStateException("Both outbound buffer, and listener for header flush are present;"
                    + " either one can write the response body, but not both");
        }
        // Start constructing the actual HTTP response
        DefaultHttpHeaders hdrs = new DefaultHttpHeaders();
        // Figure out if this response cannot possibly be anything more than headers
        boolean noBody = (listener == null && buf == null) || status == NO_CONTENT || status == NOT_MODIFIED;
        boolean hasInternalCompress = has(X_INTERNAL_COMPRESS);
        boolean hasContentEncoding = has(CONTENT_ENCODING);
        boolean hasTransferEncoding = has(TRANSFER_ENCODING);
        boolean hasContentLength = false;

        // To ensure the compressor doesn't screw with things it shouldn't - we have a buffer with
        // the complete response.
        boolean addIdentityContentEncoding = buf != null && !hasContentEncoding && !hasInternalCompress;
        // Iterate and filter the headers to create a usable response
        for (Entry<?> e : headers) {
            if (noBody) {
                // Ensure a response with no body is always a zero content-length response; discard content-length
                // as it doesn't belong in a chunked response;  content encoding is irrelevant with no
                // content, and same with transfer-encoding
                if (e.is(CONTENT_LENGTH)) {
                    continue;
                } else if (e.is(CONTENT_ENCODING)) {
                    hasContentEncoding = false;
                    continue;
                } else if (e.is(TRANSFER_ENCODING)) {
                    hasTransferEncoding = false;
                    continue;
                }
            }
            // Avoid double content-encoding headers
            if (addIdentityContentEncoding && e.is(CONTENT_ENCODING)) {
                continue;
            }
            hasContentLength |= e.is(CONTENT_LENGTH);
            e.write(hdrs);
        }
        // Some debug logging
        if (debug && evt instanceof HttpEvent) {
            System.out.println("\n\n********************\n"
                    + ((HttpEvent) evt).path() + " " + status + " hasInternalCompress "
                    + hasInternalCompress + " hasContentLength " + hasContentLength
                    + " hasTransferEncoding " + hasTransferEncoding + " hasContentEncoding " + hasContentEncoding
                    + " chunked " + chunked + " noBody " + noBody);
        }
        if (addIdentityContentEncoding) {
            hdrs.add(CONTENT_ENCODING, IDENTITY);
        }
        if (noBody) {
            // Return the buffer's memory to the pool if present
            if (buf != null) {
                buf.release();
            }
            // Using DefaultFullHttpResponse, especially with 0-byte responses,
            // leaves the encoder in a bad state, where it will throw an exception
            // if the connection is reused for another response.  So instead, we
            // manually flush our own last content

//            hdrs.set(TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
//            listener = SEND_EMPTY_LAST_CHUNK;
//            chunked = true;
            if (debug) {
                System.out.println("noBody - set content-length: 0, chunked false");
            }
            hdrs.set(CONTENT_LENGTH, ZERO);
            chunked = false;
            DefaultFullHttpResponse result = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER, hdrs, EmptyHttpHeaders.INSTANCE);
            result.touch("response-impl-a");
            return result;
        } else {
            if (chunked) {
                if (!hasTransferEncoding) {
//                    System.out.println("chunked and to xfer - set transfer encoding to chunked");
                    hdrs.set(TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
            } else if (buf != null) {
                hdrs.set(CONTENT_LENGTH, buf.readableBytes());
//                System.out.println("set content-length to " + buf.readableBytes());
            }
        }
        HttpVersion version = HTTP_1_1;
        if (!chunked && buf == null && listener != null && !hasContentEncoding) {
            if (!hasContentLength && listener != CLOSE && listener != SEND_EMPTY_LAST_CHUNK) {
                // Unless an HTTP 1.0 style response is really desired, this usually
                // indicates a bug, and the connection to the browser will hang
                // indefinitely at the end of the request
                warn(evt);
                version = HTTP_1_0;
            }
            hdrs.set(X_INTERNAL_COMPRESS, true);
        }
        if (debug) {
            System.out.println(" final headers " + headersString(hdrs));
        }
        DefaultHttpResponse result;
        if (buf != null) {
//            return new DefaultFullHttpResponse(HTTP_1_1, status, buf, hdrs, EmptyHttpHeaders.INSTANCE);
            if (debug) {
                System.out.println("  has a buffer, send it as a chunk");
            }
            listener = new SendOneBuffer(buf);
            result = new DefaultHttpResponse(version, status, hdrs);
        } else {
            result = new DefaultHttpResponse(version, status, hdrs);
        }
        return result;
    }

    static Set<String> WARNED = ConcurrentHashMap.newKeySet();

    static void warn(Event<?> evt) {
        String pth = evt instanceof HttpEvent ? ((HttpEvent) evt).path().toString() : "";
        String mth = evt instanceof HttpEvent ? ((HttpEvent) evt).method().toString() : "";
        if (WARNED.add(mth + pth)) {
            System.err.println("Response to " + mth + " " + pth
                    + " is non-chunked, has no content-length header but "
                    + "will send response chunks using a listener.  The only way to avoid hanging "
                    + "the client is to close the connection, HTTP 1.0 style, once all data is sent.");
        }
    }

    private String headersString(HttpHeaders resp) {
        StringBuilder sb = new StringBuilder();
        if (resp != null) {
            for (Map.Entry<String, String> e : resp.entries()) {
                sb.append('\n').append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        return sb.toString();
    }

    static final class SendOneBuffer implements ChannelFutureListener {

        private final ByteBuf buf;

        public SendOneBuffer(ByteBuf buf) {
            this.buf = buf;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isDone() || future.isSuccess()) {
                future.channel().writeAndFlush(new DefaultLastHttpContent(buf));
            }
        }

    }
    private static final AsciiString TRUE = AsciiString.of("1");

    static final SendEmptyLastChunk SEND_EMPTY_LAST_CHUNK = new SendEmptyLastChunk();

    static final class SendEmptyLastChunk implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isDone() || future.isSuccess()) {
                future.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            } else {
                if (future.cause() != null) {
                    future.cause().printStackTrace();
                }
            }
        }

    }

    ChannelFuture sendMessage(Event<?> evt, ChannelFuture future, HttpMessage resp, boolean trigger) throws Exception {
        if (listener != null) {
            if (trigger) {
                listener.operationComplete(future);
            } else {
                future = future.addListener(listener);
            }
            return future;
        } else if (!isKeepAlive(evt)) {
            future = future.addListener(ChannelFutureListener.CLOSE);
        }
        return future;
    }

    @Override
    public String toString() {
        return "Response{" + "modified=" + modified + ", status="
                + status + ", headers=" + headers + ", message=" + message
                + ", listener=" + listener + ", chunked=" + chunked
                + '}';
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

        public boolean is(CharSequence name) {
            return decorator.is(name);
        }

        public void write(HttpMessage msg) {
            Headers.write(decorator, value, msg);
        }

        void write(HttpHeaders headers) {
            Headers.write(decorator, value, headers);
        }

        public CharSequence stringValue() {
            return decorator.toCharSequence(value);
        }

        @Override
        public String toString() {
            return decorator.name() + ": " + decorator.toCharSequence(value);
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
            if (this.decorator.equals(decorator)) { // Equality test is case-insensitive name match
                if (this.decorator.type() != decorator.type()) {
                    if (debug) {
                        System.err.println("Requesting header " + decorator + " of type " + decorator.type().getName()
                                + " but returning header of type " + this.decorator.type().getName() + " - if set, this"
                                + " will probably throw a ClassCastException.");
                    }
                }
                return (HeaderValueType<R>) this.decorator;
            }
            if (this.decorator.name().equals(decorator.name())
                    && this.decorator.type().equals(decorator.type())) {
                return decorator;
            } else if (Strings.charSequencesEqual(this.decorator.name(), decorator.name(), true)) {

            }
            return null;
        }
    }
}
