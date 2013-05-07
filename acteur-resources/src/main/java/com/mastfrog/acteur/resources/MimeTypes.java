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
import com.mastfrog.url.Path;
import io.netty.util.CharsetUtil;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class MimeTypes {

    private final Map<String, MediaType> m = new HashMap<>();
    @Inject(optional = true)
    private Charset charset = CharsetUtil.UTF_8;
    private final ExpiresPolicy policy;

    @Inject
    public MimeTypes(Charset defaultCharset, ExpiresPolicy policy) {
        this.charset = defaultCharset;
        this.policy = policy;
        // XXX use Guava's MediaType
        add("js", MediaType.JAVASCRIPT_UTF_8.withCharset(charset), true);
        add("gif", MediaType.GIF);
        add("png", MediaType.PNG);
        add("jpg", MediaType.JPEG);
        add("bmp", MediaType.BMP);
        add("tif", MediaType.TIFF);
        add("tiff", MediaType.TIFF);
        add("ico", MediaType.ICO);
        add("xml", MediaType.XML_UTF_8.withCharset(charset));
        add("xhtml", MediaType.XHTML_UTF_8.withCharset(charset));
        add("jpeg", MediaType.JPEG);
        add("json", MediaType.JSON_UTF_8.withCharset(charset), true);
        add("txt", MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), true);
        add("template", MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), true);
        add("pdf", MediaType.PDF);
        add("html", MediaType.HTML_UTF_8.withCharset(charset), true);
        add("css", MediaType.CSS_UTF_8.withCharset(charset), true);
        add("swf", MediaType.SHOCKWAVE_FLASH);
        add("woff", MediaType.create("application", "x-font-woff"));
    }

    public MimeTypes() {
        this(CharsetUtil.UTF_8, new NullExpiresPolicy());
    }

    private static class NullExpiresPolicy implements ExpiresPolicy {

        public DateTime get(MediaType mimeType, Path path) {
            return null;
        }
    }

    public final void add(String ext, MediaType tp) {
        add(ext, tp, false);
    }

    public final void add(String ext, MediaType tp, boolean charset) {
        ext = ext.toLowerCase();
        
        if (charset) {
            tp=tp.withCharset(this.charset);
        }
        m.put(ext.toLowerCase(), tp);
    }

    public MediaType get(String fileName) {
        String ext;
        int off;
        if ((off = fileName.lastIndexOf('.')) >= 0) {
            ext = fileName.substring(off + 1);
        } else {
            ext = fileName;
        }
        MediaType result = m.get(ext.toLowerCase());
//        System.out.println("Mime type for " + fileName + " is " + result);
        return result;
    }
}
