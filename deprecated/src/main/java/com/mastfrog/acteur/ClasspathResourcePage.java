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

import com.google.inject.Inject;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Headers.CONTENT_ENCODING;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Headers.ETAG;
import static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED;
import static com.mastfrog.acteur.headers.Headers.VARY;
import com.mastfrog.mime.MimeType;
import com.mastfrog.url.Path;
import com.mastfrog.util.streams.HashingInputStream;
import com.mastfrog.util.streams.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;

/**
 * A page which loads resources relative to itself on the classpath. Handles
 * caching headers as follows: ETag is generated (SHA-1) on first read; last
 * modified = server start time.
 * <p/>
 * Use this to embed resources inside application JARs - this is not for serving
 * flat files on disk.
 *
 * @author Tim Boudreau
 * @deprecated This is Acteur 1.0 stuff.  Use the acteur-resources plugin which
 * covers disk and classpath based resources.
 */
@Deprecated
public abstract class ClasspathResourcePage extends Page {

    private static Map<Class<?>, Map<Path, Integer>> sizes = new HashMap<>();
    private static Map<Class<?>, Boolean> overridesProcessContent = new HashMap<>();
    private static Map<Class<?>, Map<Path, String>> etags = new HashMap<>();
    private static final Map<Class<?>, Map<Path, byte[]>> contentForPathForType = new HashMap<>();
    private final Path path;

    protected ClasspathResourcePage(final HttpEvent event, ActeurFactory f, String... patterns) {
        this(null, event, f, patterns);
    }

    @Deprecated
    @SuppressWarnings("LeakingThisInConstructor")
    protected ClasspathResourcePage(final Application app, final HttpEvent event, ActeurFactory f, String... patterns) {
        this.path = event.path();

        add(f.matchMethods(com.mastfrog.acteur.headers.Method.GET, com.mastfrog.acteur.headers.Method.HEAD));
        add(f.matchPath(patterns));
        add(HeadersActeur.class);
        add(HasStreamAction.class);

        add(f.sendNotModifiedIfETagHeaderMatches());
        add(f.sendNotModifiedIfIfModifiedSinceHeaderMatches());
        if (event.method() != com.mastfrog.acteur.headers.Method.HEAD) {
            add(WriteBodyActeur.class);
        } else {
            add(f.responseCode(HttpResponseStatus.OK));
        }
    }

    static class HeadersActeur extends Acteur {

        @Inject
        HeadersActeur(ZonedDateTime serverStartTime) {
            add(LAST_MODIFIED, serverStartTime);
            add(CACHE_CONTROL, CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY);
            add(VARY, new HeaderValueType<?>[]{CONTENT_ENCODING});
        }
    }

    private static class WriteBodyActeur extends Acteur {

        @Inject
        @SuppressWarnings("ArrayIsStoredDirectly")
        WriteBodyActeur(HttpEvent event, Page page) throws IOException {
            byte[] content = ((ClasspathResourcePage) page).getContent(event.path());
            setState(new RespondWith(HttpResponseStatus.OK));
            setResponseWriter(new BodyWriter(content, event.requestsConnectionStayOpen()));
            MimeType type = getContentType(event.path());
            if (type != null) {
                add(CONTENT_TYPE, type);
            }
        }

        protected MimeType getContentType(Path path) {
            String pth = path.toString();
            if (pth.endsWith("svg")) {
                return MimeType.SVG;
            } else if (pth.endsWith("css")) {
                return MimeType.CSS_UTF_8;
            } else if (pth.endsWith("html")) {
                return MimeType.HTML_UTF_8;
            } else if (pth.endsWith("json")) {
                return MimeType.JSON_UTF_8;
            } else if (pth.endsWith("js")) {
                return MimeType.TEXT_JAVASCRIPT_UTF_8;
            } else if (pth.endsWith("gif")) {
                return MimeType.GIF;
            } else if (pth.endsWith("jpg")) {
                return MimeType.JPEG;
            } else if (pth.endsWith("png")) {
                return MimeType.PNG;
            }
            return null;
        }

    }

    private static class HasStreamAction extends Acteur {

        @Inject
        HasStreamAction(Page page, HttpEvent event) {
            boolean hasContent;
            Map<Path, byte[]> m = contentForPathForType.get(page.getClass());
            hasContent = (m != null && m.containsKey(event.path()) || getStream(event.path(), page.getClass()) != null);
            if (hasContent) {
                String cachedEtag = getCachedEtag(page.getClass(), event.path());
                if (cachedEtag != null) {
                    add(ETAG, cachedEtag);
                }
                Long cachedSize = getCachedSize(page.getClass(), event.path());
                if (cachedSize != null) {
                    add(Headers.CONTENT_LENGTH, cachedSize);
                }
                next();
            } else {
                setState(new RespondWith(HttpResponseStatus.NOT_FOUND, "No such page " + event.path()));
            }
        }
    }

    private static Long getCachedSize(Class<?> pageClass, Path path) {
        Map<Path, Integer> sz = sizes.get(pageClass);
        if (sz != null) {
            Integer val = sz.get(path);
            if (val != null) {
                return val.longValue();
            }
        }
        return null;
    }

    private static String getCachedEtag(Class<?> pageClass, Path path) {
        Map<Path, String> tags = etags.get(pageClass);
        if (tags != null) {
            return tags.get(path);
        }
        return null;
    }

    InputStream getStream(Path path) {
        return getStream(path, getClass());
    }

    protected static InputStream getStream(Path path, Class<?> type) {
        try {
            String name = URLDecoder.decode(path.getLastElement().toString(), "UTF-8");
            InputStream in = type.getResourceAsStream(name);
            return in;
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError(ex); //won't happen
        }
    }

    static class BodyWriter extends ResponseWriter {

        private final byte[] bytes;
        private volatile int offset = 0;
        private int chunksize = 256;
        private final boolean keepAlive;

        @SuppressWarnings("ArrayIsStoredDirectly")
        BodyWriter(byte[] content, boolean keepAlive) {
            bytes = content;
            this.keepAlive = keepAlive;
        }

        @Override
        public Status write(Event<?> evt, Output out, int iteration) throws Exception {
            int old = offset;
            int remaining = Math.min(chunksize, bytes.length - offset);
            offset += remaining;
            ByteBuf buf = Unpooled.wrappedBuffer(bytes, old, remaining);
            out.write(buf);
            return offset < bytes.length ? Status.NOT_DONE : Status.DONE;
        }
    }

    public Long getContentLength() {
        long result = -1;
        if (!isDynamicContent()) {
            Map<Path, Integer> m = sizes.get(getClass());
            if (m == null) {
                sizes.put(getClass(), m = new HashMap<>());
            }
            getETag();
            Integer res = m.get(path);
            if (res != null) {
                result = res;
            }
        }
        return result == -1 ? null : result;
    }

    private boolean shouldCache(Path path) {
        return !isDynamicContent();
    }

    protected byte[] getContent(Path path) throws IOException {
        boolean cache = shouldCache(path);
        Map<Path, byte[]> cacheMap = contentForPathForType.get(getClass());
        if (cache && cacheMap != null) {
            byte[] res = cacheMap.get(path);
            if (res != null) {
                return res;
            }
        } else if (cache) {
            cacheMap = new HashMap<>();
            contentForPathForType.put(getClass(), cacheMap);
        }

        InputStream in = getStream(path);
        if (in == null) {
            return null;
        }
        byte[] result;
        if (!isDynamicContent()) {
            Map<Path, String> m = etags.get(getClass());
            String etag = null;
            if (m == null) {
                m = new HashMap<>();
                etags.put(getClass(), m);
            } else {
                etag = m.get(path);
            }
            if (etag == null) {
                HashingInputStream hin = HashingInputStream.sha1(in);
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                int byteCount = Streams.copy(hin, o);
                hin.close();
                m.put(path, getHashString(hin));
                Map<Path, Integer> sz = sizes.get(getClass());
                if (sz == null) {
                    sz = new HashMap<>();
                    sizes.put(getClass(), sz);
                }
                sz.put(path, byteCount);
                result = o.toByteArray();
            } else {
                try {
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    Streams.copy(in, o);
                    result = o.toByteArray();
                } finally {
                    in.close();
                }
            }
        } else {
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                Streams.copy(in, o);
                result = o.toByteArray();
            } finally {
                in.close();
            }
        }
        if (cache) {
            cacheMap.put(path, result);
        }
        return result;
    }

    protected byte[] processContent(byte[] content) {
        return content;
    }

    private static Method findMethod(Class<?> on, String name, Class<?>... params) throws SecurityException {
        Class<?> curr = on;
        //NOTE:  Does not check interfaces
        while (curr != Object.class) {
            try {
                return curr.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ex) {
//                Logger.getLogger(ClasspathResourcePage.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                curr = curr.getSuperclass();
            }
        }
        return null;
    }

    protected boolean isDynamicContent() {
        Boolean dynContent = overridesProcessContent.get(getClass());
        if (dynContent == null) {
            try {
                Method m = findMethod(getClass(), "processContent", String.class);
                dynContent = m == null ? false : m.getDeclaringClass() == ClasspathResourcePage.class;
                overridesProcessContent.put(getClass(), dynContent);
            } catch (SecurityException ex) {
                Logger.getLogger(ClasspathResourcePage.class.getName()).log(Level.SEVERE, null, ex);
                dynContent = true;
            }
        }
        return dynContent;
    }

    public String getETag() {
        if (isDynamicContent()) {
            return null;
        }
        Map<Path, String> m = etags.get(getClass());
        if (m == null) {
            m = new HashMap<>();
            etags.put(getClass(), m);
        }
        String etag = m.get(path);
        if (etag == null) {
            InputStream in = getStream(path);
            if (in == null) {
                return null;
            }
            HashingInputStream hin = HashingInputStream.sha1(in);
            try {
                int byteCount = Streams.copy(hin, Streams.nullOutputStream());
                etag = getHashString(hin);
                Map<Path, Integer> sz = sizes.get(getClass());
                if (sz == null) {
                    sz = new HashMap<>();
                    sizes.put(getClass(), sz);
                }
                sz.put(path, byteCount);
                m.put(path, etag);
            } catch (IOException ex) {
                Logger.getLogger(ClasspathResourcePage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return etag;
    }

    String getHashString(HashingInputStream hin) throws IOException {
        hin.close();
        byte[] bytes = hin.getDigest();
        byte[] base64 = Base64.encodeBase64(bytes);
        return new String(base64, CharsetUtil.US_ASCII);
    }
}
