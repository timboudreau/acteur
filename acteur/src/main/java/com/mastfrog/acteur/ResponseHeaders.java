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

import com.google.common.net.MediaType;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.HeaderValueType;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

/**
 * Standard HTTP headers.
 *
 * @author Tim Boudreau
 */
public class ResponseHeaders {

    private DateTime lastModified;
    private URI location;
    private String etag;
    private Duration age;
    private Duration maxAge;
    private Locale locale;
    private URI contentLocation;
    private MediaType contentType;
    private CacheControl cacheControl = new CacheControl();
    private List<HeaderValueType<?>> varyHeaders = new LinkedList<>();
    private ContentLengthProvider contentLengthProvider;
    private ETagProvider etagProvider;
    private DateTime expires;
    private String transferEncoding;
    private boolean modified;
    
    private void modify() {
        modified = true;
    }

    public void setExpires(DateTime expires) {
        modify();
        this.expires = expires;
    }

    public void getVaryHeaders(List<? super HeaderValueType<?>> into) {
        into.addAll(varyHeaders);
    }

    public void addVaryHeader(HeaderValueType<?> header) {
        modify();
        varyHeaders.add(header);
    }

    public void addCacheControl(CacheControlTypes type, Duration value) {
        modify();
        cacheControl.add(type, value);
    }

    public void addCacheControl(CacheControlTypes type) {
        modify();
        cacheControl.add(type);
    }

    public void setAge(Duration age) {
        modify();
        this.age = age;
    }

    public void setContentLocation(URI contentLocation) {
        modify();
        this.contentLocation = contentLocation;
    }

    public void setContentType(MediaType contentType) {
        modify();
        this.contentType = contentType;
    }

    public void setEtag(String etag) {
        modify();
        this.etag = etag;
    }

    public void setLastModified(DateTime lastModified) {
        modify();
        this.lastModified = lastModified;
    }

    public void setLocale(Locale locale) {
        modify();
        this.locale = locale;
    }

    public void setLocation(URI location) {
        modify();
        this.location = location;
    }

    public void setMaxAge(Duration maxAge) {
        modify();
        this.maxAge = maxAge;
    }

    protected MediaType getContentType() {
        return contentType;
    }

    public void setContentLength(long len) {
        modify();
        setContentLengthProvider(new TrivialContentLengthProvider(len));
    }

    protected Long getContentLength() {
        return this.contentLengthProvider == null ? null : contentLengthProvider.getContentLength();
    }
    
    private String contentEncoding;
    public String getContentEncoding() {
        return contentEncoding;
    }
    
    public void setContentEncoding(String contentEncoding) {
        modify();
        this.contentEncoding = contentEncoding;
    }

    public void setTransferEncoding(String gzip) {
        modify();
        this.transferEncoding = gzip;
    }
    
    public String getTransferEncoding() {
        return this.transferEncoding;
    }

    private static class TrivialContentLengthProvider implements ContentLengthProvider {

        private final long len;

        public TrivialContentLengthProvider(long len) {
            this.len = len;
        }

        @Override
        public Long getContentLength() {
            return len;
        }
    }

    public void setContentLengthProvider(ContentLengthProvider provider) {
        modify();
        this.contentLengthProvider = provider;
    }

    public void setETag(final String etag) {
        modify();
        setETagProvider(new TrivialETagProvider(etag));
    }

    protected DateTime getExpires() {
        return expires;
    }

    private static final class TrivialETagProvider implements ETagProvider {

        private final String etag;

        public TrivialETagProvider(String etag) {
            this.etag = etag;
        }

        @Override
        public String getETag() {
            return etag;
        }
    }

    public void setETagProvider(ETagProvider provider) {
        modify();
        this.etagProvider = provider;
    }

    public interface ContentLengthProvider {

        public Long getContentLength();
    }

    public interface ETagProvider {

        public String getETag();
    }

    protected DateTime getLastModified() {
        DateTime dt = lastModified;
        if (dt != null) {
            dt = new DateTime(dt.getMillis(), DateTimeZone.UTC).withMillisOfSecond(0);
        }
        return dt;
    }

    protected String getETag() {
        if (etag == null && etagProvider != null) {
            etag = etagProvider.getETag();
        }
        return etag;
    }

    protected Locale getContentLanguage() {
        return locale;
    }

    protected CacheControl getCacheControl() {
        CacheControl result = cacheControl;
        if (result.isEmpty()) {
            Duration ma = getMaxAge();
            if (ma != null) {
                result = new CacheControl().add(CacheControlTypes.max_age, ma).add(CacheControlTypes.must_revalidate);
            }
        }
        return result.isEmpty() ? null : result;
    }

    protected Duration getMaxAge() {
        return maxAge;
    }

    protected Duration getAge() {
        return age;
    }

    protected URI getContentLocation() {
        return contentLocation;
    }

    protected URI getLocation() {
        return location;
    }
    
    boolean modified() {
        return modified;
    }
}
