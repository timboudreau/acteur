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

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.ResponseHeaders;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.GZIPOutputStream;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Version of FileResources that does not cache bytes in-memory, just uses
 * Netty's FileRegion, and inode numbers for etags.
 *
 * @author Tim Boudreau
 */
public class DynamicFileResources implements StaticResources {

    private final File dir;
    private final ExpiresPolicy policy;
    private final MimeTypes types;
    private final ApplicationControl ctrl;
    private final ByteBufAllocator alloc;

    @Inject
    DynamicFileResources(File dir, MimeTypes types, ExpiresPolicy policy, ApplicationControl ctrl, ByteBufAllocator alloc) {
        this.dir = dir;
        this.policy = policy;
        this.types = types;
        this.ctrl = ctrl;
        this.alloc = alloc;
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

        private final File file;

        public DynFileResource(File file) {
            this.file = file;
        }

        @Override
        public void decoratePage(Page page, HttpEvent evt, String path, Response response, boolean chunked) {
            ResponseHeaders h = page.getResponseHeaders();
            String ua = evt.getHeader(HttpHeaderNames.USER_AGENT.toString());
            if (ua != null && !ua.contains("MSIE")) {
                page.getResponseHeaders().addVaryHeader(Headers.ACCEPT_ENCODING);
            }
            DateTime expires = policy.get(types.get(path), evt.getPath());
            Duration maxAge = expires == null ? Duration.standardHours(2)
                    : new Duration(DateTime.now(), expires);
            h.addCacheControl(CacheControlTypes.Public);
            h.addCacheControl(CacheControlTypes.must_revalidate);
            h.addCacheControl(CacheControlTypes.max_age, maxAge);
            h.setMaxAge(maxAge);
            h.setExpires(expires);
            h.setAge(Duration.ZERO);
            h.setEtag(getEtag());
            h.setLastModified(new DateTime(file.lastModified()));
            String acceptEncoding = evt.getHeader(Headers.ACCEPT_ENCODING);
            boolean willCompress = acceptEncoding != null && acceptEncoding.toLowerCase().contains("gzip");
            if (!willCompress) {
                h.setContentLength(file.length());
            } else {
                response.add(Headers.stringHeader("X-Internal-Compress"), "true");
                response.add(Headers.CONTENT_ENCODING, "gzip");
            }
            response.setChunked(false);
        }

        @Override
        public String getEtag() {
            try {
                java.nio.file.Path path = file.toPath();
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);

                Object fileKey = attr.fileKey();
                if (fileKey != null) {
                    String s = fileKey.toString();
                    if (s.contains("ino=")) {
                        String inode = s.substring(s.indexOf("ino=") + 4, s.indexOf(")"));
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
        public DateTime lastModified() {
            return new DateTime(file.lastModified());
        }

        @Override
        public MediaType getContentType() {
            return types.get(file.getName());
        }

        @Override
        public long getLength() {
            return file.length();
        }

        @Override
        public void attachBytes(HttpEvent evt, Response response, boolean chunked) {
            String acceptEncoding = evt.getHeader(Headers.ACCEPT_ENCODING);
            boolean willCompress = acceptEncoding != null && acceptEncoding.toLowerCase().contains("gzip");
            if (!willCompress) {
                response.setBodyWriter(new ResponseWriter() {
                    @Override
                    public ResponseWriter.Status write(Event<?> evt, ResponseWriter.Output out, int iteration) throws Exception {
                        FileRegion reg = new DefaultFileRegion(file, 0, file.length());
                        out.write(reg);
                        return Status.DONE;
                    }
                });
            } else {
                ByteBuf buf = alloc.directBuffer();
                try (FileInputStream in = new FileInputStream(file)) {
                    try (ByteBufOutputStream bufOut = new ByteBufOutputStream(buf)) {
                        try (GZIPOutputStream gz = new GZIPOutputStream(bufOut, (int) (file.length() / 2))) {
                            Streams.copy(in, gz);
                        }
                    }
                } catch (Exception ex) {
                    buf.release();
                    Exceptions.chuck(ex);
                }
                response.add(Headers.CONTENT_LENGTH, (long) buf.readableBytes());
                response.setBodyWriter(new ResponseWriter() {
                    boolean first = true;

                    @Override
                    public ResponseWriter.Status write(Event<?> evt, ResponseWriter.Output out, int iteration) throws Exception {
                        if (!first) {
                            out.write(new DefaultLastHttpContent());
                            return Status.DONE;
                        }
                        out.write(buf);
                        boolean wasFirst = first;
                        first = false;
                        return wasFirst ? Status.NOT_DONE : Status.DONE;
                    }
                });
            }
        }
    }
}
