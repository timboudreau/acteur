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
package com.mastfrog.acteur.resources;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.CacheControlTypes;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.ACCEPT_ENCODING;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Headers.ETAG;
import static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED;
import static com.mastfrog.acteur.headers.Headers.VARY;
import static com.mastfrog.acteur.resources.DynamicFileResources.INTERNAL_COMPRESS_HEADER;
import static com.mastfrog.acteur.resources.FileResources.RESOURCES_BASE_PATH;
import com.mastfrog.mime.MimeType;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.DeploymentMode;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.HashingOutputStream;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.JZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public final class ClasspathResources implements StaticResources {

    private final MimeTypes types;
    private final Class<?> relativeTo;
    private final Map<String, Resource> names = new HashMap<>();
    private static final ZonedDateTime startTime = ZonedDateTime.now().with(ChronoField.MILLI_OF_SECOND, 0);
    private final String[] patterns;
    private final DeploymentMode mode;
    private final ByteBufAllocator allocator;
    private final boolean internalGzip;

    @Inject
    public ClasspathResources(MimeTypes types, ClasspathResourceInfo info, DeploymentMode mode, ByteBufAllocator allocator, Settings settings) throws Exception {
        Checks.notNull("allocator", allocator);
        Checks.notNull("types", types);
        Checks.notNull("info", info);
        Checks.notNull("mode", mode);
        this.allocator = allocator;
        internalGzip = settings.getBoolean("internal.gzip", false);
        this.types = types;
        this.mode = mode;
        this.relativeTo = info.relativeTo();
        List<String> l = new ArrayList<>();
        String resourcesBasePath = settings.getString(RESOURCES_BASE_PATH, "");

        for (String nm : info.names()) {
            this.names.put(nm, new ClasspathResource(nm));
            String pat = Strings.joinPath(resourcesBasePath, nm);
            l.add(pat);
        }
        patterns = l.toArray(new String[l.size()]);
    }

    boolean productionMode() {
        return mode.isProduction();
    }

    @Override
    public Resource get(String path) {
        if (path.indexOf('%') >= 0) {
            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                return Exceptions.chuck(ex);
            }
        }
        return names.get(path);
    }

    @Override
    public String[] getPatterns() {
        return patterns;
    }

    static void gzip(ByteBuf in, ByteBuf out) throws IOException {
        try (GZIPOutputStream outStream = new GZIPOutputStream(new ByteBufOutputStream(out), 9)) {
            try (ByteBufInputStream inStream = new ByteBufInputStream(in)) {
                Streams.copy(inStream, outStream, 512);
            }
        }
    }

    private static class Y extends JZlibDecoder {

        Y() {
            super(ZlibWrapper.GZIP);
            super.setSingleDecode(true);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            while (in.readableBytes() > 0) {
                super.decode(ctx, in, out);
            }
        }
    }

    private class ClasspathResource implements Resource {

        final ByteBuf bytes;
        final ByteBuf compressed;
        private final String hash;
        final String name;
        private final int length;

        ClasspathResource(String name) throws Exception {
            Checks.notNull("name", name);
            this.name = name;
            ByteBuf bytes = allocator.directBuffer();
            try (InputStream in = relativeTo.getResourceAsStream(name)) {
                if (in == null) {
                    throw new FileNotFoundException(name);
                }
                try (ByteBufOutputStream out = new ByteBufOutputStream(bytes)) {
                    try (HashingOutputStream hashOut = HashingOutputStream.sha1(out)) {
                        Streams.copy(in, hashOut, 512);
                        hash = hashOut.getHashAsString();
                    }
                }
            }
            bytes.retain();
            this.bytes = Unpooled.unreleasableBuffer(bytes);
            if (internalGzip) {
                int sizeEstimate = (int) Math.ceil(bytes.readableBytes() * 1.001) + 12;
                ByteBuf compressed = allocator.directBuffer(sizeEstimate);
                gzip(bytes, compressed);
                bytes.resetReaderIndex();
                this.compressed = Unpooled.unreleasableBuffer(compressed);
                assert check();

            } else {
                compressed = null;
            }
            bytes.resetReaderIndex();
            length = bytes.readableBytes();
        }

        private boolean check() throws Exception {
            Y y = new Y();
            ByteBuf test = allocator.buffer(bytes.readableBytes());
            try {
                y.decode(null, compressed, Collections.<Object>singletonList(test));
                compressed.resetReaderIndex();
                byte[] a = new byte[bytes.readableBytes()];
                bytes.readBytes(a);
                byte[] b = new byte[test.readableBytes()];
                test.readBytes(b);
                if (!Arrays.equals(a, b)) {
                    throw new IllegalStateException("Compressed data differs. Orig length " + a.length
                            + " result length " + b.length + "\n.  ORIG:\n" + new String(a) + "\n\nNEW:\n" + new String(b));
                }
                bytes.resetReaderIndex();
            } finally {
                test.release();
            }
            return true;
        }

        @Override
        public void decorateResponse(HttpEvent evt, String path, Response response, boolean chunked) {
            String ua = evt.header("User-Agent");
            if (ua != null && !ua.contains("MSIE")) {
                response.add(VARY, new HeaderValueType<?>[]{ACCEPT_ENCODING});
            }
            if (productionMode()) {
                response.add(CACHE_CONTROL, CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY);
            } else {
                response.add(CACHE_CONTROL, new CacheControl(CacheControlTypes.Private, CacheControlTypes.no_cache, CacheControlTypes.no_store));
            }
            response.add(LAST_MODIFIED, startTime)
                    .add(ETAG, hash);
            MimeType type = getContentType();
            if (type != null) {
                response.add(CONTENT_TYPE, type);
            }
            if (internalGzip) {
                // Flag it so the standard compressor ignores us
                response.add(INTERNAL_COMPRESS_HEADER, "true");
            }
            if (chunked) {
                response.add(Headers.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            if (isGzip(evt)) {
                response.add(Headers.CONTENT_ENCODING, HttpHeaderValues.GZIP.toString());
                if (!chunked) {
                    response.add(Headers.CONTENT_LENGTH, (long) compressed.readableBytes());
                }
            } else {
                if (!chunked) {
                    response.add(Headers.CONTENT_LENGTH, (long) bytes.readableBytes());
                }
            }
            response.chunked(chunked);
        }

        @Override
        public void attachBytes(HttpEvent evt, Response response, boolean chunked) {
            if (isGzip(evt)) {
                CompressedBytesSender sender = new CompressedBytesSender(compressed, !evt.requestsConnectionStayOpen(), chunked);
                response.contentWriter(sender);
            } else {
                CompressedBytesSender c = new CompressedBytesSender(bytes, !evt.requestsConnectionStayOpen(), chunked);
                response.contentWriter(c);
            }
        }

        @Override
        public MimeType getContentType() {
            MimeType mt = types.get(name);
            return mt;
        }
    }

    boolean isGzip(HttpEvent evt) {
        if (!internalGzip) {
            return false;
        }
        CharSequence acceptEncoding = evt.header(Headers.ACCEPT_ENCODING);
        return acceptEncoding != null && 
                Strings.charSequenceContains(acceptEncoding, "gzip", true);
    }

    static final class BytesSender extends ResponseWriter {

        private final ByteBuf bytes;

        BytesSender(ByteBuf bytes) {
            this.bytes = Unpooled.wrappedBuffer(bytes);
        }

        @Override
        public Status write(Event<?> evt, Output out) throws Exception {
            out.write(bytes);
//            out.future().addListener(ChannelFutureListener.CLOSE);
            return Status.DONE;
        }
    }

    static final class CompressedBytesSender implements ChannelFutureListener {

        private final ByteBuf bytes;
        private final boolean close;
        private final boolean chunked;

        CompressedBytesSender(ByteBuf bytes, boolean close, boolean chunked) {
            this.bytes = Unpooled.wrappedBuffer(bytes);
            this.close = close;
            this.chunked = chunked;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!chunked) {
                future = future.channel().writeAndFlush(bytes);
            } else {
                future = future.channel().write(new DefaultHttpContent(bytes)).channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            }
            if (close) {
                future.addListener(CLOSE);
            }
        }
    }
}
