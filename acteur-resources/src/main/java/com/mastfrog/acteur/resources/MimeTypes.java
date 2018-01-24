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
import static com.mastfrog.acteur.resources.MimeTypes.CompressionStrategy.COMPRESS;
import static com.mastfrog.acteur.resources.MimeTypes.CompressionStrategy.IDENTITY;
import io.netty.util.CharsetUtil;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class MimeTypes {

    private final Map<String, MediaType> m = new HashMap<>();
    private final Map<MediaType, CompressionStrategy> compressionStrategies = new HashMap<>();
    @Inject(optional = true)
    private Charset charset = StandardCharsets.UTF_8;

    @Inject
    public MimeTypes(Charset defaultCharset) {
        this.charset = defaultCharset == null ? StandardCharsets.UTF_8 : defaultCharset;
        add("js", MediaType.JAVASCRIPT_UTF_8.withCharset(charset), true, COMPRESS);
        add("gif", MediaType.GIF, IDENTITY);
        add("png", MediaType.PNG, IDENTITY);
        add("jpg", MediaType.JPEG, IDENTITY);
        add("bmp", MediaType.BMP, false, COMPRESS);
        add("tif", MediaType.TIFF,false, COMPRESS);
        add("svg", MediaType.SVG_UTF_8, false, COMPRESS);
        add("tiff", MediaType.TIFF, COMPRESS);
        add("ico", MediaType.ICO, COMPRESS);
        add("xml", MediaType.XML_UTF_8.withCharset(charset), true, COMPRESS);
        add("txt", MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("xhtml", MediaType.XHTML_UTF_8.withCharset(charset), true, COMPRESS);
        add("jpeg", MediaType.JPEG, IDENTITY);
        add("json", MediaType.JSON_UTF_8.withCharset(charset), true, COMPRESS);
        add("txt", MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("log", MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("template", MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("pdf", MediaType.PDF);
        add("html", MediaType.HTML_UTF_8.withCharset(charset), true, COMPRESS);
        add("css", MediaType.CSS_UTF_8.withCharset(charset), true, COMPRESS);
        add("swf", MediaType.SHOCKWAVE_FLASH, COMPRESS);
        add("md", MediaType.parse("text/x-markdown").withCharset(charset), COMPRESS);
        add("bz2", MediaType.parse("application/x-bzip2"), IDENTITY);
        add("gz", MediaType.parse("application/x-gzip"), IDENTITY);
        add("tar", MediaType.parse("application/x-tar"), COMPRESS);
        add("doc", MediaType.parse("application/msword"), false, COMPRESS);
        add("pdf", MediaType.parse("application/pdf"), IDENTITY);
        add("ppt", MediaType.parse("application/powerpoint"), false, COMPRESS);
        add("rtf", MediaType.parse("text/richtext"), COMPRESS);
        add("mp4", MediaType.MP4_VIDEO, IDENTITY);
        add("mp3", MediaType.parse("audio/mp3"), IDENTITY);
        add("m4a", MediaType.MP4_AUDIO, IDENTITY);
        add("flv", MediaType.FLV_VIDEO, IDENTITY);
        add("webm", MediaType.WEBM_VIDEO, IDENTITY);
        add("aac", MediaType.AAC_AUDIO, IDENTITY);
        add("json", MediaType.JSON_UTF_8, COMPRESS);
        add("mpeg", MediaType.MPEG_VIDEO, IDENTITY);
        add("avi", MediaType.parse("video/avi"), COMPRESS);
        add("aiff", MediaType.parse("audio/aiff"), COMPRESS);
        add("wav", MediaType.parse("audio/wav"), COMPRESS);
        add("ogg", MediaType.OGG_VIDEO, IDENTITY);
        add("ogv", MediaType.OGG_VIDEO, IDENTITY);
        add("oga", MediaType.OGG_AUDIO, IDENTITY);
        add("woff", MediaType.create("application", "x-font-woff"), COMPRESS);
        add("m3u8", MediaType.create("application", "x-mpegurl"), COMPRESS);
        add("m3u", MediaType.create("application", "x-mpegurl"), COMPRESS);
        add("ts", MediaType.create("video", "MP2T"), IDENTITY);
        add("m4v", MediaType.MP4_VIDEO, IDENTITY);
        add("fli", MediaType.create("video", "fli"), COMPRESS);
        add("flc", MediaType.create("video", "flc"), COMPRESS);
        add("3gp", MediaType.create("video", "3gpp"), IDENTITY);
        add("wmv", MediaType.WMV, IDENTITY);
        add("mpeg", MediaType.MPEG_VIDEO, IDENTITY);
        add("mp2", MediaType.MPEG_AUDIO, IDENTITY);
        add("mts", MediaType.create("video", "avchd"), IDENTITY);
    }

    public MimeTypes() {
        this(CharsetUtil.UTF_8);
    }

    public final void add(String ext, MediaType tp) {
        add(ext, tp, false, CompressionStrategy.DEFAULT);
    }

    public final void add(String ext, MediaType tp, CompressionStrategy strategy) {
        add(ext, tp, false, CompressionStrategy.DEFAULT);
    }

    public final void add(String ext, MediaType tp, boolean charset) {
        add(ext, tp, charset, CompressionStrategy.DEFAULT);
    }

    public final void add(String ext, MediaType tp, boolean charset, CompressionStrategy strategy) {
        ext = ext.toLowerCase();

        if (charset) {
            tp = tp.withCharset(this.charset);
        }
        m.put(ext.toLowerCase(), tp);
        compressionStrategies.put(tp, strategy);
    }

    public boolean shouldCompress(MediaType type) {
        if (type == null) {
            return true;
        }
        CompressionStrategy strategy = compressionStrategies.get(type);
        if (strategy != null) {
            switch (strategy) {
                case COMPRESS:
                    return true;
                case IDENTITY:
                    return false;
            }
        }
        if ("text".equals(type.type()) || "javascript".equals(type.subtype()) || "json".equals(type.subtype())) {
            return true;
        } else {
            return false;
        }
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
        return result;
    }

    public enum CompressionStrategy {
        COMPRESS,
        IDENTITY,
        DEFAULT
    }
}
