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
package com.mastfrog.acteur.headers;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.net.MediaType;
import com.mastfrog.util.preconditions.Checks;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class MediaTypeHeader extends AbstractHeader<MediaType> {

    private static final LoadingCache<MediaType, AsciiString> typeCache
            = CacheBuilder.<MediaType, AsciiString>newBuilder().build(new CacheLoader<MediaType, AsciiString>() {
                @Override
                public AsciiString load(MediaType k) throws Exception {
                    return new AsciiString(k.toString());
                }
            });
    private static final LoadingCache<CharSequence, MediaType> stringCache
            = CacheBuilder.<CharSequence, MediaType>newBuilder().build(new CacheLoader<CharSequence, MediaType>() {
                @Override
                public MediaType load(CharSequence k) throws Exception {
                    return MediaType.parse(k.toString());
                }
            });

    MediaTypeHeader() {
        super(MediaType.class, HttpHeaderNames.CONTENT_TYPE);
    }

    @Override
    public CharSequence toCharSequence(MediaType value) {
        Checks.notNull("value", value);
        try {
            return typeCache.get(value);
        } catch (ExecutionException ex) {
            Logger.getLogger(MediaTypeHeader.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    @Override
    public MediaType toValue(CharSequence value) {
        Checks.notNull("value", value);
        try {
            return stringCache.get(AsciiString.of(value));
        } catch (ExecutionException ex) {
            Logger.getLogger(MediaTypeHeader.class.getName()).log(Level.WARNING, "Bad media type {0}", value);
            return null;
        }
    }

}
