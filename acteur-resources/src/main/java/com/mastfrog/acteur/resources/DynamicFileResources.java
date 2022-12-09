/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.CacheControlTypes;
import com.mastfrog.acteur.headers.BoundedRangeNetty;
import com.mastfrog.acteur.headers.ByteRanges;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.ACCEPT_ENCODING;
import static com.mastfrog.acteur.headers.Headers.ACCEPT_RANGES;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Headers.CONTENT_ENCODING;
import static com.mastfrog.acteur.headers.Headers.CONTENT_LENGTH;
import static com.mastfrog.acteur.headers.Headers.CONTENT_RANGE;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Headers.ETAG;
import static com.mastfrog.acteur.headers.Headers.EXPIRES;
import static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED;
import static com.mastfrog.acteur.headers.Headers.RANGE;
import static com.mastfrog.acteur.headers.Headers.VARY;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.HEAD;
import com.mastfrog.acteur.headers.Range;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.mime.MimeType;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.HashingOutputStream;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.time.TimeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.JZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import static io.netty.handler.codec.http.HttpHeaderValues.IDENTITY;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
import io.netty.util.AsciiString;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import static java.lang.Math.max;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Provider;

/**
 * Version of FileResources that does not cache bytes in-memory, just uses
 * Netty's FileRegion, and inode numbers for etags.
 *
 * @author Tim Boudreau
 */
public class DynamicFileResources implements StaticResources {

    public static final String SETTINGS_KEY_MAX_RANGE_BUFFER_SIZE = "dynresources.range.buffer.size";
    private static final int DEFAULT_RANGE_BUFFER_SIZE = 2048;
    private final File dir;
    private final ExpiresPolicy policy;
    private final MimeTypes types;
    private final ApplicationControl ctrl;
    private final ByteBufAllocator alloc;
    static final HeaderValueType<CharSequence> INTERNAL_COMPRESS_HEADER
            = Headers.header("X-Internal-Compress");
    private static final AsciiString TRUE = new AsciiString("true");
    /**
     * Settings key which, if true, means DynamicFileResources will use the
     * SHA-1 hash of the file's bytes for ETag headers, rather than the inode
     * value. This is useful for consistent etags in a clustered environment, at
     * the price of adding overhead the first time the file is requested and the
     * hash is computed (after which the hash will be cached for some length of
     * time, assuming the file is not modified).
     */
    public static final String SETTINGS_KEY_USE_HASH_ETAG = "dyn.resources.use.hash.etag";
    /**
     * Set the length of time hashed etags are kept for - only relevant if using
     * sha-1 hash etags instead of inodes.
     */
    public static final String SETTINGS_KEY_HASH_ETAG_CACHE_EXPIRY_MINUTES = "dyn.resources.hash.etag.cache.expiry.minutes";
    private final boolean hashEtags;
    private final LoadingCache<File, EtagCacheEntry> etagCache;
    private final boolean neverKeepAlive;
    private final int rangeBufferSize;

    @Inject
    public DynamicFileResources(File dir, MimeTypes types, ExpiresPolicy policy, ApplicationControl ctrl, ByteBufAllocator alloc, Settings settings,
            Provider<Closables> onChannelClose) {
        this.hashEtags = settings.getBoolean(SETTINGS_KEY_USE_HASH_ETAG, false);
        neverKeepAlive = settings.getBoolean("neverKeepAlive", false);
        rangeBufferSize = max(64, settings.getInt(SETTINGS_KEY_MAX_RANGE_BUFFER_SIZE, DEFAULT_RANGE_BUFFER_SIZE));
        this.dir = dir;
        this.policy = policy;
        this.types = types;
        this.ctrl = ctrl;
        this.alloc = alloc;
        if (hashEtags) {
            int etagCacheExpiryMinutes = settings.getInt(SETTINGS_KEY_HASH_ETAG_CACHE_EXPIRY_MINUTES, 8 * 60);
            etagCache = CacheBuilder.newBuilder()
                    .concurrencyLevel(5)
                    .maximumSize(500)
                    .softValues()
                    .expireAfterAccess(etagCacheExpiryMinutes, TimeUnit.MINUTES)
                    .build(new EtagCacheLoader());
        } else {
            etagCache = null;
        }
    }

    @Override
    public Resource get(String path) {
        File f = new File(dir, path);
        if (f.exists() && f.isFile() && f.canRead()) {
            return new DynFileResource(f);
        }
        return null;
    }

    @Override
    public String[] getPatterns() {
        return null;
    }

    private class DynFileResource implements Resource {

        final File file;
        final MimeType contentType;

        DynFileResource(File file) {
            this.file = file;
            contentType = types.get(file.getName());
        }

        @Override
        public void decorateResponse(HttpEvent evt, String path, Response response, boolean chunked) {
            String ua = evt.header(HttpHeaderNames.USER_AGENT.toString());
            if (ua != null && !ua.contains("MSIE")) {
                response.add(VARY, new HeaderValueType<?>[]{ACCEPT_ENCODING});
            }
            ZonedDateTime expires = policy.get(types.get(path), evt.path());
            Duration maxAge = expires == null ? Duration.ofHours(2)
                    : Duration.between(ZonedDateTime.now(), expires);

            CacheControl cc = new CacheControl(CacheControlTypes.Public, CacheControlTypes.must_revalidate)
                    .add(CacheControlTypes.max_age, maxAge);
            response.add(CACHE_CONTROL, cc)
                    .add(LAST_MODIFIED, TimeUtil.fromUnixTimestamp(file.lastModified()).withNano(0))
                    .add(ETAG, etag())
                    .add(ACCEPT_RANGES, HttpHeaderValues.BYTES);

            MimeType contentType = getContentType();
            if (contentType != null) {
                response.add(CONTENT_TYPE, contentType);
            }

            if (expires != null) {
                response.add(EXPIRES, expires);
            }
            CharSequence acceptEncoding = evt.header(ACCEPT_ENCODING);
            if (evt.method() == HEAD) {
                return;
            }
            long length = file.length();
            ByteRanges ranges = evt.header(RANGE);
            boolean hasGzip = acceptEncoding != null && Strings.charSequenceContains(acceptEncoding, HttpHeaderValues.GZIP, true);
            boolean hasDeflate = acceptEncoding != null && Strings.charSequenceContains(acceptEncoding, HttpHeaderValues.DEFLATE, true);
            boolean gzipOrDeflate = hasGzip
                    || hasDeflate;
            boolean willCompress = gzipOrDeflate && types.shouldCompress(contentType);
            if (ranges != null) {
                if (!ranges.isValid()) {
                    response.status(BAD_REQUEST);
                    response.content("Invalid range " + ranges);
                    return;
                }
                if (ranges.size() > 1 /* && willCompress*/) { // PENDING: We do it correctly, need multiple range Content-Range header to be written
                    response.status(NOT_IMPLEMENTED);
                    response.content("Multiple ranges not supported in compressed responses, but requested " + ranges);
                    return;
                }
                Range first = ranges.first();
                if (first.toBoundedRange(length).isRangeNotSatisfiable()) {
                    response.status(REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.content("Unsatisfiable range in file of length " + length + ": " + ranges.first());
                    return;
                }
                if (first.end(length) < 0) {
                    response.status(REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.content("Requested past end of file at " + length);
                    return;
                }
                response.add(CONTENT_RANGE, first.toBoundedRange(length));
            }
            if (!willCompress) {
                if (evt.method() != Method.HEAD && !chunked) {
                    response.add(CONTENT_LENGTH, length);
                }
            } else {
                if (evt.method() != Method.HEAD) {
                    response.add(INTERNAL_COMPRESS_HEADER, TRUE).add(CONTENT_ENCODING, hasGzip ? HttpHeaderValues.GZIP : HttpHeaderValues.DEFLATE);
                }
            }
            response.chunked(chunked);
        }

        private String etag() {
            try {
                return hashEtags ? hashEtag() : inodeEtag();
            } catch (ExecutionException ex) {
                return Exceptions.chuck(ex);
            }
        }

        private String hashEtag() throws ExecutionException {
            EtagCacheEntry etg = etagCache.get(file);
            if (!etg.stillValid(file)) {
                etagCache.refresh(file);
                etg = etagCache.get(file);
            }
            return etg.hash;
        }

        private String inodeEtag() {
            try {
                java.nio.file.Path path = file.toPath();
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                Object fileKey = attr.fileKey();
                if (fileKey != null) {
                    String s = fileKey.toString();
                    if (s.contains("ino=")) {
                        String inode = s.substring(s.indexOf("ino=") + 4, s.indexOf(')'));
                        try {
                            long val = Long.parseLong(inode);
                            return Long.toString(val, 36);
                        } catch (NumberFormatException nfe) {
                            ctrl.internalOnError(nfe);
                        }
                        return inode;
                    }
                }
            } catch (IOException ex) {
                ctrl.internalOnError(ex);
            }
            return null;
        }

        @Override
        public void attachBytes(HttpEvent evt, Response response, boolean chunked) throws Exception {
            if (evt.method() == Method.HEAD) {
                return;
            }
            CharSequence acceptEncoding = evt.header(Headers.ACCEPT_ENCODING);
            boolean hasGzip = acceptEncoding != null && Strings.charSequenceContains(acceptEncoding, HttpHeaderValues.GZIP, true);
            boolean hasDeflate = acceptEncoding != null && Strings.charSequenceContains(acceptEncoding, HttpHeaderValues.DEFLATE, true);
            boolean gzipOrDeflate = hasGzip
                    || hasDeflate;
            boolean willCompress = gzipOrDeflate && types.shouldCompress(contentType);

//            System.out.println("ACCEPT ENCODING IS " + acceptEncoding + " CHUNKED " + chunked + " hasGzip "
//                    + hasGzip + " hasDeflate " + hasDeflate + " willCompress " + willCompress + " response " + response);
//
            final ByteRanges ranges = evt.header(Headers.RANGE);
            final long length = file.length();
            if (ranges != null && ranges.size() > 0) {
                response.add(CONTENT_RANGE, ranges.first().toBoundedRange(length));
            } else if (!willCompress) {
                if (!chunked) {
                    response.add(CONTENT_LENGTH, length);
                }
            }
            if (!willCompress) {
                response.add(CONTENT_ENCODING, IDENTITY);

                response.contentWriter(new ChannelFutureListener() {

                    ReplaceRangeIterator it = ranges == null ? null : new ReplaceRangeIterator(ranges, length);
                    boolean done = false;

                    SeekableByteChannel channel;

                    private ByteBuf readBytes(Range range) throws IOException {
                        // XXX for streaming large audio video files, this fails with
//OutOfMemoryError: Cannot reserve 38496007 bytes of direct buffer
// memory (allocated: 4375169, limit: 25165824                        
                        int len = (int) (range == null ? length : range.length(length));
                        ByteBuffer buffer = ByteBuffer.allocateDirect(len);
                        if (range != null) {
                            channel.position(range.start(length));
                        }
                        int read = 0;
                        while (read < len) {
                            read += channel.read(buffer);
                        }
                        ((Buffer) buffer).flip();
                        return Unpooled.wrappedBuffer(buffer);
                    }

                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        if (f.cause() != null) {
                            ctrl.internalOnError(f.cause());
                            f.channel().close();
                            return;
                        }
                        if (done) {
//                            System.out.println("SEND LAST CHUNK FOR COMPRESSED " + ((HttpEvent) evt).path());
                            if (chunked) {
                                f = f.channel().writeAndFlush(new DefaultLastHttpContent());
                            }
                            String conn = evt.header(HttpHeaderNames.CONNECTION);
                            if (neverKeepAlive || (conn != null && HttpHeaderValues.CLOSE.contentEquals(conn))) {
                                f.addListener(CLOSE);
                            }
                            return;
                        }
                        if (channel == null) {
                            channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ);
                            f.channel().closeFuture().addListener((ChannelFutureListener) (ChannelFuture f1) -> {
                                channel.close();
                            });
                        }
                        ByteBuf buf = null;
                        try {
                            if (it == null) {
                                buf = readBytes(null);
//                            FileRegion reg = new DefaultFileRegion(file, 0, length);
                                done = true;
                                channel.close();
                            } else {
                                if (it.hasNext()) {
                                    Range range = it.take(rangeBufferSize);
//                                long count = range.length(length);
//                                FileRegion reg = new DefaultFileRegion(file, range.start(length), count);
//                                f = f.channel().writeAndFlush(reg);
                                    buf = readBytes(range);
                                    done = !it.hasNext();
                                } else {
                                    // initially empty iterator
                                    channel.close();
                                    done = true;
                                }
                            }
                        } catch (ClosedByInterruptException ex) {
                            ctrl.internalOnError(ex);
                            f.channel().close();
                            return;
                        }
                        if (buf != null) {
                            if (chunked) {
                                f = f.channel().writeAndFlush(new DefaultHttpContent(buf));
                            } else {
                                f = f.channel().writeAndFlush(buf);
                            }
                        }
                        f.addListener(this);
                    }
                });

//                response.contentWriter(new ResponseWriter() {
//                    @Override
//                    public ResponseWriter.Status write(Event<?> evt, ResponseWriter.Output out, int iteration) throws Exception {
//                        if (ranges == null) {
//                            FileRegion reg = new DefaultFileRegion(file, 0, length);
//                            out.write(reg);
//                            return Status.DONE;
//                        } else {
//                            for (Range range : ranges) {
//                                long count = range.length(length);
//                                FileRegion reg = new DefaultFileRegion(file, range.start(length), count);
//                                out.write(reg);
//                            }
//                            return Status.DONE;
//                        }
//                    }
//                });
                return;
            } else {
                ByteBuf buf = Unpooled.buffer();
                response.add(Headers.CONTENT_ENCODING, hasGzip ? "gzip" : "deflate");
                // XXX should not do the entire file in one hunk - could be very large
                try {
                    if (ranges == null) {
                        Enc enc = new Enc(hasGzip);
                        enc.encode(evt.ctx(), Unpooled.wrappedBuffer(Files.readAllBytes(file.toPath())), buf);
                    } else {
                        Enc enc = new Enc(hasGzip);
                        try (SeekableByteChannel channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
                            for (Range r : ranges) {
                                int rangeLength = (int) r.length(length);
                                if (rangeLength > 0) {
                                    ByteBuffer buffer = ByteBuffer.allocate(rangeLength);
                                    channel.position(r.start(length));
                                    channel.read(buffer);
                                    // Need to cast to Buffer to avoid
                                    // java.lang.NoSuchMethodError: java.nio.ByteBuffer.flip()Ljava/nio/ByteBuffer
                                    // because > jdk-8 returns ByteBuffer
                                    ((Buffer) buffer).flip();
                                    enc.encode(evt.ctx(), Unpooled.wrappedBuffer(buffer), buf);
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    buf.release();
                    evt.channel().close();
                    throw ex;
                }
                if (!chunked) {
                    response.add(Headers.CONTENT_LENGTH, (long) buf.readableBytes());
                }

                response.contentWriter(new ChannelFutureListener() {
                    boolean first = true;

                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        if (f.cause() != null) {
                            ctrl.internalOnError(f.cause());
                            f.channel().close();
                        }
                        if (!first) {
//                            System.out.println("SEND LAST CHUNK FOR COMPRESSED " + ((HttpEvent) evt).path());
                            if (chunked) {
                                f = f.channel().writeAndFlush(new DefaultLastHttpContent());
                            }
                            String conn = evt.header(HttpHeaderNames.CONNECTION);
                            if (neverKeepAlive || (conn != null && HttpHeaderValues.CLOSE.contentEquals(conn))) {
                                f.addListener(CLOSE);
                            }
                            return;
                        }
//                        System.out.println("SEND COMPRESSED CHUNK OF " + buf.readableBytes() + " " + ((HttpEvent) evt).path());
                        if (chunked) {
                            f = f.channel().writeAndFlush(new DefaultHttpContent(buf));
                        } else {
                            f = f.channel().writeAndFlush(buf);
                        }
                        first = false;
                        f.addListener(this);
                    }
                });
            }
        }

        @Override
        public MimeType getContentType() {
            return contentType;
        }
    }

    static final class Enc extends JZlibEncoder {

        Enc(boolean gzip) {
            super(gzip ? ZlibWrapper.GZIP : ZlibWrapper.ZLIB, 8);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
            super.encode(ctx, in, out);
        }
    }

    /*
    static final class ChunkedGzipContentWriter extends ResponseWriter {

        private final File file;
        private final int length;
        private SeekableByteChannel fileChannel;
        private final Iterator<Range> rangeIterator;
        private final Closables onChannelClose;
        private final Enc enc = new Enc();
        private final int chunkSize;
        private final ByteBuffer readBuffer;
        ChunkedGzipContentWriter(File file, int length, ByteRanges ranges, Closables onChannelClose, int chunkSize) {
            readBuffer = ByteBuffer.allocateDirect((int) Math.min(file.length(), length));
            this.file = file;
            this.length = length;
            this.onChannelClose = onChannelClose;
            this.rangeIterator = ranges == null ? CollectionUtils.singletonIterator(new FullRange(length)) : ranges.iterator();
            // Make a set of new ranges that reflect the chnunk size and use those
            this.chunkSize = chunkSize;
        }

        @Override
        public Status write(Event<?> evt, Output out, int iteration) throws Exception {
            switch(iteration) {
                case 0 :
                    fileChannel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ);
                    onChannelClose.add(fileChannel);
                    break;
            }
            Range range = rangeIterator.hasNext() ? rangeIterator.next() : null;
            if (range == null) {
                out.write(LastHttpContent.EMPTY_LAST_CONTENT);
                return Status.DONE;
            } else {
                ByteBuffer readInto = readBuffer;
                readBuffer.reset();
                fileChannel.position(range.start(length));

                boolean isDone = fileChannel.position() == fileChannel.size();
                int byteCount = fileChannel.read(readBuffer);
                ByteBuf outbuffer = evt.channel().alloc().ioBuffer(chunkSize);
                this.enc.encode(evt.ctx(), Unpooled.wrappedBuffer(readBuffer), outbuffer);

                return Status.NOT_DONE;
            }
        }

        static final class Enc extends JZlibEncoder {

            @Override
            protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
                super.encode(ctx, in, out);
            }


        }

    }
    static final class FullRange implements Range {

        final long length;

        public FullRange(long length) {
            this.length = length;
        }

        @Override
        public long start(long max) {
            return 0;
        }

        @Override
        public long end(long max) {
            return length - 1;
        }

        @Override
        public BoundedRange toBoundedRange(long max) {
            throw new UnsupportedOperationException("Not supported");
        }
    }
     */
    static final class EtagCacheLoader extends CacheLoader<File, EtagCacheEntry> {

        @Override
        public EtagCacheEntry load(File k) throws Exception {
            if (!k.exists()) {
                return new EtagCacheEntry("<deleted>", 0);
            }
            HashingOutputStream hashOut = HashingOutputStream.sha1(Streams.nullOutputStream());;
            try (FileInputStream in = new FileInputStream(k)) {
                Streams.copy(in, hashOut, 1024);
            } finally {
                hashOut.close();
            }
            byte[] digest = hashOut.getDigest();
            return new EtagCacheEntry(Strings.toBase64(digest), k.lastModified());
        }
    }

    private static final class EtagCacheEntry {

        private final String hash;
        private final long lastModified;

        public EtagCacheEntry(String hash, long lastModified) {
            this.hash = hash;
            this.lastModified = lastModified;
        }

        public boolean stillValid(File f) {
            return f.exists() && f.lastModified() == lastModified;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + Objects.hashCode(this.hash);
            hash = 67 * hash + (int) (this.lastModified ^ (this.lastModified >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final EtagCacheEntry other = (EtagCacheEntry) obj;
            if (this.lastModified != other.lastModified) {
                return false;
            }
            if (!Objects.equals(this.hash, other.hash)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return hash + ":" + TimeUtil.toIsoFormat(new Date(lastModified));
        }
    }
    
    static class ReplaceRangeIterator {
        private final List<Range> ranges = new ArrayList<>();
        private int cursor = 0;
        private final long totalBytes;
        ReplaceRangeIterator(ByteRanges ranges, long totalBytes) {
            for (Range r : ranges) {
                this.ranges.add(r);
            }
            this.totalBytes = totalBytes;
        }
        
        public boolean hasNext() {
            return cursor < ranges.size();
        }
        
        public Range take(int maxLength) {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Range r = ranges.get(cursor);
            long len = r.length(totalBytes);
            if (len >= maxLength) {
                long st = r.start(totalBytes);
                long en = r.end(totalBytes);
//                System.out.println("HAVE OUT OF RANGE " + st + " - " + en + " for max " + maxLength);
                FakeRange result = new FakeRange(st, st + len);
//                System.out.println("  RETURN A " + result);
                if (st + len < en) {
                    FakeRange substitute = new FakeRange(st + len, en);
//                    System.out.println("  PUT BACK A " + substitute);
                    ranges.set(cursor, substitute);
                } else {
//                    System.out.println("  done, increment cursor");
                    cursor++;
                }
                return result;
            } else {
//                System.out.println("  normal done, increment cursor");
                cursor++;
            }
            return r;
        }
        
        static class FakeRange implements Range {
            
            private final long start;
            private final long end;
            FakeRange(long start, long end) {
                this.start = start;
                this.end = end;
            }
            
            @Override
            public long start(long max) {
                return start;
            }

            @Override
            public long end(long max) {
                return end;
            }

            @Override
            public long length(long max) {
                return end - start;
            }
            
            @Override
            public BoundedRangeNetty toBoundedRange(long max) {
                return new BoundedRangeNetty(start(max), end(max), max);
            }
            
            public String toString() {
                return "FakeRange(" + start + ":" + end  + ")";
            }
            
        }
        
    }
}
