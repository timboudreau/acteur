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

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.ResponseHeaders;
import com.mastfrog.acteur.ResponseHeaders.ContentLengthProvider;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.giulius.DeploymentMode;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Streams;
import com.mastfrog.util.streams.HashingOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MessageList;
import io.netty.handler.codec.compression.JZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public final class ClasspathResources implements StaticResources {

    private final MimeTypes types;
    private final Class<?> relativeTo;
    private final Map<String, Resource> names = new HashMap<>();
    private static final DateTime startTime = DateTime.now();
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
        for (String nm : info.names()) {
            this.names.put(nm, new ClasspathResource(nm));
            String pat = nm;
            l.add(pat);
        }
        patterns = l.toArray(new String[0]);
    }

    boolean productionMode() {
        return mode.isProduction();
    }

    public Resource get(String path) {
        if (path.indexOf('%') >= 0) {
            path = URLDecoder.decode(path);
        }
        return names.get(path);
    }

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

    static class Y extends JZlibDecoder {

        Y() {
            super(ZlibWrapper.GZIP);
            super.setSingleDecode(true);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, MessageList<Object> out) throws Exception {
            while (in.readableBytes() > 0) {
                super.decode(ctx, in, out); 
            }
        }
    }

    private class ClasspathResource implements Resource, ContentLengthProvider {

        private final ByteBuf bytes;
        private final ByteBuf compressed;
        private final String hash;
        private final String name;
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
                y.decode(null, compressed, MessageList.<Object>newInstance(test));
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
        public void decoratePage(Page page, Event evt, String path, Response response) {
            ResponseHeaders h = page.getReponseHeaders();
            String ua = evt.getHeader("User-Agent");
            if (ua != null && !ua.contains("MSIE")) {
                page.getReponseHeaders().addVaryHeader(Headers.ACCEPT_ENCODING);
            }
            if (productionMode()) {
                page.getReponseHeaders().addCacheControl(CacheControlTypes.Public);
                page.getReponseHeaders().addCacheControl(CacheControlTypes.max_age, Duration.standardHours(2));
                page.getReponseHeaders().addCacheControl(CacheControlTypes.must_revalidate);
            } else {
                page.getReponseHeaders().addCacheControl(CacheControlTypes.Private);
                page.getReponseHeaders().addCacheControl(CacheControlTypes.no_cache);
                page.getReponseHeaders().addCacheControl(CacheControlTypes.no_store);
            }
//            if (evt.getMethod() != Method.HEAD) {
//                page.getReponseHeaders().setContentLengthProvider(this);
//            }
            h.setLastModified(startTime);
            h.setEtag(hash);
//            page.getReponseHeaders().setContentLength(getLength());
            MediaType type = getContentType();
            if (type == null) {
                new NullPointerException("Null content type for " + name).printStackTrace();
            }
            if (type != null) {
                h.setContentType(type);
            }
            if (internalGzip) {
                // Flag it so the standard compressor ignores us
                response.add(Headers.stringHeader("X-Internal-Compress"), "true");
            }
//            response.add(Headers.stringHeader("Transfer-Encoding"), "chunked");
            if (isGzip(evt)) {
                page.getReponseHeaders().setContentEncoding("gzip");
//                response.add(Headers.CONTENT_LENGTH, (long) compressed.readableBytes());
            } else {
//                response.add(Headers.CONTENT_LENGTH, (long) bytes.readableBytes());
            }
            response.setChunked(true);
//            response.setChunked(false);
        }

        public void attachBytes(Event evt, Response response) {
            if (isGzip(evt)) {
                CompressedBytesSender sender = new CompressedBytesSender(compressed, !evt.isKeepAlive(), true);
                response.setBodyWriter(sender);
//                BytesSender sender = new BytesSender(compressed);
//                response.setBodyWriter(sender);
            } else {
//                BytesSender sender = new BytesSender(bytes);
//                response.setBodyWriter(sender);
                CompressedBytesSender c = new CompressedBytesSender(bytes, !evt.isKeepAlive(), true);
                response.setBodyWriter(c);
            }
        }

        public String getEtag() {
            return hash;
        }

        public DateTime lastModified() {
            return startTime;
        }

        public MediaType getContentType() {
            MediaType mt = types.get(name);
            return mt;
        }

        public long getLength() {
            return length;
        }

        public Long getContentLength() {
//            return internalGzip ? null : (long) length;
            return null;
        }
    }

    boolean isGzip(Event evt) {
        if (!internalGzip) {
            return false;
        }
        String hdr = evt.getHeader(HttpHeaders.Names.ACCEPT_ENCODING);
        return hdr != null && hdr.toLowerCase().contains("gzip");
    }

    static final class BytesSender extends ResponseWriter {

        private final ByteBuf bytes;

        public BytesSender(ByteBuf bytes) {
            this.bytes = Unpooled.wrappedBuffer(bytes);
        }

        @Override
        public Status write(Event evt, Output out) throws Exception {
            out.write(bytes);
//            out.future().addListener(ChannelFutureListener.CLOSE);
            return Status.DONE;
        }
    }

    static final class CompressedBytesSender implements ChannelFutureListener {

        private final ByteBuf bytes;
        private final boolean close;
        private final boolean chunked;

        public CompressedBytesSender(ByteBuf bytes, boolean close, boolean chunked) {
            this.bytes = Unpooled.wrappedBuffer(bytes);
            this.close = close;
            this.chunked = chunked;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!chunked) {
                future = future.channel().write(bytes);
            } else {
                future = future.channel().write(new DefaultHttpContent(bytes)).channel().write(LastHttpContent.EMPTY_LAST_CONTENT);
            }
            if (close) {
                future.addListener(CLOSE);
            }
        }
    }
}
