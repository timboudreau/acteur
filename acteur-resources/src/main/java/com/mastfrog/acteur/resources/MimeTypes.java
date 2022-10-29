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

import com.mastfrog.mime.MimeType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import static com.mastfrog.acteur.resources.MimeTypes.CompressionStrategy.COMPRESS;
import static com.mastfrog.acteur.resources.MimeTypes.CompressionStrategy.IDENTITY;
import static com.mastfrog.mime.MimeType.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class MimeTypes {

    private final Map<String, MimeType> m = new HashMap<>();
    private final Map<MimeType, CompressionStrategy> compressionStrategies = new HashMap<>();
    @Inject(optional = true)
    private Charset charset = UTF_8;

    @Inject
    public MimeTypes(Charset defaultCharset) {
        this.charset = defaultCharset == null ? StandardCharsets.UTF_8 : defaultCharset;
        add("js", MimeType.TEXT_JAVASCRIPT_UTF_8.withCharset(charset), true, COMPRESS);
        add("gif", GIF, IDENTITY);
        add("png", PNG, IDENTITY);
        add("jpg", JPEG, IDENTITY);
        add("bmp", BMP, false, COMPRESS);
        add("tif", TIFF, false, COMPRESS);
        add("svg", SVG, false, COMPRESS);
        add("tiff", TIFF, COMPRESS);
        add("ico", ICON, COMPRESS);
        add("xml", MimeType.XML_UTF_8.withCharset(charset), true, COMPRESS);
        add("txt", MimeType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("xhtml", XHTML_UTF_8.withCharset(charset), true, COMPRESS);
        add("jpeg", JPEG, IDENTITY);
        add("json", MimeType.JSON_UTF_8.withCharset(charset), true, COMPRESS);
        add("txt", MimeType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("log", MimeType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("template", MimeType.PLAIN_TEXT_UTF_8.withCharset(charset), true, COMPRESS);
        add("pdf", PDF);
        add("html", MimeType.HTML_UTF_8.withCharset(charset), true, COMPRESS);
        add("css", MimeType.CSS_UTF_8.withCharset(charset), true, COMPRESS);
        add("md", MimeType.create("text", "x-markdown").withCharset(charset), COMPRESS);
        add("bz2", MimeType.create("application", "x-bzip2"), IDENTITY);
        add("gz", MimeType.create("application", "x-gzip"), IDENTITY);
        add("zip", MimeType.create("application", "zip"), IDENTITY);

        add("der", MimeType.create("application", "x-x509-ca-cert"), IDENTITY);
        add("pem", MimeType.create("application", "x-x509-ca-cert"), IDENTITY);
        add("crt", MimeType.create("application", "x-x509-ca-cert"), IDENTITY);
        add("ts", MimeType.create("video", "mp2t"), IDENTITY);
        add("apk", MimeType.create("application", "vnd.android.package-archive"), IDENTITY);
        add("tar", MimeType.create("application", "x-tar"), COMPRESS);
        add("doc", MimeType.create("application", "msword"), false, COMPRESS);
        add("pdf", MimeType.create("application", "pdf"), IDENTITY);
        add("ppt", MimeType.create("application", "powerpoint"), false, COMPRESS);
        add("nbm", MimeType.create("application", "nbm"), false, IDENTITY);
        add("xlsx", MimeType.create("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet"), false, COMPRESS);
        add("pptx", MimeType.create("application", "vnd.openxmlformats-officedocument.presentationml.presentation"), false, COMPRESS);
        add("docx", MimeType.create("application", "vnd.openxmlformats-officedocument.wordprocessingml.document"), false, COMPRESS);
        add("rtf", MimeType.create("application", "rtf"), COMPRESS);
        add("rtx", MimeType.create("text", "richtext"), COMPRESS);
        add("mp4", MimeType.create("video", "mp4"), IDENTITY);
        add("mkv", MimeType.create("video", "x-matroska"), IDENTITY);
        add("mp3", MimeType.create("audio", "mp3"), IDENTITY);
        add("m4a", MimeType.create("audio", "x-m4a"), IDENTITY);
        add("m4v", MimeType.create("video", "mp4"), IDENTITY);
        add("flv", MimeType.create("video", "x-flv"), IDENTITY);
        add("webm", MimeType.create("video", "webm"), IDENTITY);
        add("mov", MimeType.create("video", "quicktype"), IDENTITY);
        add("mid", MimeType.create("audio", "midi"), false, COMPRESS);
        add("aac", MimeType.create("audio", "aac"), IDENTITY);
        add("json", MimeType.JSON_UTF_8, COMPRESS);
        add("mpeg", MimeType.create("video", "mpeg"), IDENTITY);
        add("avi", MimeType.create("video", "x-msvideo"), COMPRESS);
        add("aiff", MimeType.create("audio", "aiff"), COMPRESS);
        add("flac", MimeType.create("audio", "flac"), COMPRESS);
        add("opus", MimeType.create("audio", "opus"), COMPRESS);
        add("wav", MimeType.create("audio", "wav"), COMPRESS);
        add("ogg", MimeType.create("video", "ogg"), IDENTITY);
        add("ogv", MimeType.create("video", "ogg"), IDENTITY);
        add("oga", MimeType.create("audio", "ogg"), IDENTITY);
        add("woff", MimeType.create("application", "x-font-woff"), COMPRESS);
        add("m3u8", MimeType.create("application", "x-mpegurl"), COMPRESS);
        add("m3u", MimeType.create("application", "x-mpegurl"), COMPRESS);
        add("ts", MimeType.create("video", "MP2T"), IDENTITY);
        add("fli", MimeType.create("video", "x-fli"), COMPRESS);
        add("flc", MimeType.create("video", "x-flc"), COMPRESS);
        add("3gp", MimeType.create("video", "3gpp"), IDENTITY);
        add("wmv", MimeType.create("video", "x-ms-wmv"), IDENTITY);
        add("mpeg", MimeType.create("video", "mpeg"), IDENTITY);
        add("mp2", MimeType.create("audio", "mpeg"), IDENTITY);
        add("mts", MimeType.create("video", "avchd"), IDENTITY);
        add("atom", MimeType.create("application", "atom+xml").withCharset(charset), true, COMPRESS);
        add("rss", MimeType.create("application", "rss+xml").withCharset(charset), true, COMPRESS);
        add("jar", MimeType.create("application", "java-archive"), false, IDENTITY);
        add("java", MimeType.create("text", "x-java"), false, IDENTITY);
        add("jar", MimeType.create("application", "java-archive"), false, IDENTITY);
        add("class", MimeType.create("application", "java-vm"), false, IDENTITY);
        add("jng", MimeType.create("image", "x-jng"), false, IDENTITY);
        add("m3u8", MimeType.create("application", "vnd.apple.mpegurl").withCharset(charset), true, IDENTITY);
    }

    public MimeTypes() {
        this(UTF_8);
    }

    public final void add(String ext, MimeType tp) {
        add(ext, tp, false, CompressionStrategy.DEFAULT);
    }

    public final void add(String ext, MimeType tp, CompressionStrategy strategy) {
        add(ext, tp, false, CompressionStrategy.DEFAULT);
    }

    public final void add(String ext, MimeType tp, boolean charset) {
        add(ext, tp, charset, CompressionStrategy.DEFAULT);
    }

    public final void add(String ext, MimeType tp, boolean charset, CompressionStrategy strategy) {
        ext = ext.toLowerCase();

        if (charset) {
            tp = tp.withCharset(this.charset);
        }
        m.put(ext.toLowerCase(), tp);
        compressionStrategies.put(tp, strategy);
    }

    public boolean shouldCompress(MimeType type) {
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
        if ("text".equals(type.primaryType()) || type.isSecondaryType("javascript") || type.isSecondaryType("json")) {
            return true;
        } else {
            return false;
        }
    }

    public MimeType get(String fileName) {
        String ext;
        int off;
        if ((off = fileName.lastIndexOf('.')) >= 0) {
            ext = fileName.substring(off + 1);
        } else {
            ext = fileName;
        }
        MimeType result = m.get(ext.toLowerCase());
        return result;
    }

    public enum CompressionStrategy {
        COMPRESS,
        IDENTITY,
        DEFAULT
    }
}
