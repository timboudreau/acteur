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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.ResponseHeaders.ContentLengthProvider;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import com.mastfrog.util.streams.HashingOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class FileResources implements StaticResources {

    public static final String SETTINGS_KEY_MAX_FILE_LENGTH = "max.file.length";

    private final File dir;
    private final MimeTypes types;
    private final LoadingCache<String, FileResource> cache;

    @Inject
    FileResources(File dir, MimeTypes types, Settings settings) {
        this.dir = dir;
        this.types = types;
        long maxFileLength = settings.getLong(SETTINGS_KEY_MAX_FILE_LENGTH, 1024 * 1024 * 12);
        long expiry = settings.getLong("file.resources.expire.minutes", 2);
        Loader loader = new Loader();
        cache = CacheBuilder.newBuilder()
                .weigher(loader)
                .maximumWeight(maxFileLength)
                .softValues()
                .expireAfterAccess(expiry, TimeUnit.MINUTES)
                .concurrencyLevel(3)
                .initialCapacity(20)
                .build(loader);
    }

    private class Loader extends CacheLoader<String, FileResource> implements Weigher<String, FileResource> {

        @Override
        public int weigh(String key, FileResource value) {
            return value.bytes.length;
        }

        @Override
        public FileResource load(String arg0) throws Exception {
            return _get(arg0);
        }
    }

    public Resource get(String path) {
        try {
            return cache.get(path);
        } catch (ExecutionException ex) {
            return (Resource) (ex.getCause() != null ? Exceptions.chuck(ex.getCause())
                    : Exceptions.chuck(ex));
        }
    }

    private FileResource _get(String path) {
        try {
            return new FileResource(new File(dir, path));
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    @Override
    public String[] getPatterns() {
        List<String> result = new ArrayList<>();
        scan(dir, "", result);
        return result.toArray(new String[0]);
    }

    private void scan(File dir, String path, List<String> result) {
        for (File f : dir.listFiles()) {
            if (f.isFile() && f.canRead()) {
//                String nm = "^" + f.getName() + "$";
//                nm = nm.replace("\\.", "\\\\.");
//                result.add(nm);
                result.add(path + (path.isEmpty() ? "" : "/") + f.getName());
            } else if (f.isDirectory()) {
                scan(f, path + (path.isEmpty() ? "" : "/") + f.getName(), result);
            }
        }

    }

    private class FileResource implements Resource, ContentLengthProvider {

        private final File file;
        private final byte[] bytes;
        private final String etag;

        private FileResource(File file) throws FileNotFoundException, IOException {
            this.file = file;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HashingOutputStream o = new HashingOutputStream("SHA-1", baos);
            try (FileInputStream fi = new FileInputStream(file)) {
                Streams.copy(fi, o);
            }
            etag = o.getHashAsString();
            bytes = baos.toByteArray();
        }

        @Override
        public void decoratePage(Page page, Event evt, String path) {
            page.getReponseHeaders().addVaryHeader(Headers.ACCEPT_ENCODING);
            page.getReponseHeaders().addCacheControl(CacheControlTypes.Public);
            page.getReponseHeaders().addCacheControl(CacheControlTypes.max_age, Duration.standardHours(2));
            page.getReponseHeaders().addCacheControl(CacheControlTypes.must_revalidate);
            page.getReponseHeaders().setLastModified(lastModified());
            page.getReponseHeaders().setEtag(etag);
            if (evt.getMethod() != Method.HEAD) {
                page.getReponseHeaders().setContentLengthProvider(this);
            }
            MediaType contentType = types.get(file.getName());
            if (contentType != null) {
                page.getReponseHeaders().setContentType(contentType);
            }
        }

        public ResponseWriter sender(Event evt) {
            return new ClasspathResources.BytesSender(bytes);
        }

        public String getEtag() {
            return etag;
        }

        public DateTime lastModified() {
            return new DateTime(file.lastModified());
        }

        public MediaType getContentType() {
            return types.get(file.getName());
        }

        public long getLength() {
            return file.length();
        }

        public Long getContentLength() {
            return getLength();
        }
    }
}
