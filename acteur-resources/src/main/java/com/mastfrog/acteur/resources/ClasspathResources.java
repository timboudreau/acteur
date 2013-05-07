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
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.ResponseHeaders;
import com.mastfrog.acteur.ResponseHeaders.ContentLengthProvider;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.util.Streams;
import com.mastfrog.util.streams.HashingOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public final class ClasspathResources implements StaticResources {

    private final Provider<MimeTypes> types;
    private final Class<?> relativeTo;
    private final Map<String, Resource> names = new HashMap<>();
    private static final DateTime startTime = DateTime.now();
    private final String[] patterns;

    @Inject
    public ClasspathResources(Provider<MimeTypes> types, ClasspathResourceInfo info) throws IOException {
        this.types = types;
        this.relativeTo = info.relativeTo();
        List<String> l = new ArrayList<>();
        for (String nm : info.names()) {
            this.names.put(nm, new ClasspathResource(nm));
//            String pat = "^" + nm + "$";
            String pat = nm;
//            pat = pat.replace(".", "\\.");
            l.add(pat);
        }
//        System.out.println("PATTERNS: "+ l);
        patterns = l.toArray(new String[0]);
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

    private class ClasspathResource implements Resource, ContentLengthProvider {

        private final byte[] bytes;
        private final String hash;
        private final String name;

        ClasspathResource(String name) throws IOException {
            this.name = name;
            InputStream in = relativeTo.getResourceAsStream(name);
            if (in == null) {
                throw new FileNotFoundException(name);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            HashingOutputStream ho = new HashingOutputStream("SHA-1", bo);
            Streams.copy(in, ho);
            hash = ho.getHashAsString();
            bytes = bo.toByteArray();
        }

        public void decoratePage(Page page, Event evt) {
            ResponseHeaders h = page.getReponseHeaders();
            page.getReponseHeaders().addVaryHeader(Headers.CONTENT_ENCODING);
            page.getReponseHeaders().addCacheControl(CacheControlTypes.Public);
            page.getReponseHeaders().addCacheControl(CacheControlTypes.max_age, Duration.standardHours(2));
            page.getReponseHeaders().addCacheControl(CacheControlTypes.must_revalidate);
            if (evt.getMethod() != Method.HEAD) {
                page.getReponseHeaders().setContentLengthProvider(this);
            }
            h.setLastModified(startTime);
            h.setEtag(hash);
            MediaType type = getContentType();
            if (type != null) {
                h.setContentType(type);
            }
        }

        @Override
        public ResponseWriter sender(Event evt) {
            return new BytesSender(evt, bytes);
        }

        public String getEtag() {
            return hash;
        }

        public DateTime lastModified() {
            return startTime;
        }

        public MediaType getContentType() {
            MediaType mt = types.get().get(name);
            return mt;
        }

        public long getLength() {
            return bytes.length;
        }

        public Long getContentLength() {
            return getLength();
        }
    }

    static final class BytesSender extends ResponseWriter {

        private final Event evt;
        private final byte[] bytes;

        public BytesSender(Event evt, byte[] bytes) {
            this.evt = evt;
            this.bytes = bytes;
        }

        @Override
        public Status write(Event evt, Output out) throws Exception {
            out.write(bytes);
            return Status.DONE;
        }
    }
}
