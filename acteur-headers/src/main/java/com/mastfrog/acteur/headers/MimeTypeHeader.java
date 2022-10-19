package com.mastfrog.acteur.headers;

import com.mastfrog.mime.MimeType;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Tim Boudreau
 */
final class MimeTypeHeader extends AbstractHeader<MimeType> {

    private static final Map<CharSequence, MimeType> CACHE = new ConcurrentHashMap<>();

    public MimeTypeHeader() {
        super(MimeType.class, HttpHeaderNames.CONTENT_TYPE);
    }

    public CharSequence toCharSequence(MimeType value) {
        return value.toCharSequence();
    }

    public MimeType toValue(CharSequence value) {
        return CACHE.computeIfAbsent(value, MimeType::parse);
    }

}
