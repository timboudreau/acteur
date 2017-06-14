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

import com.google.common.net.MediaType;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.util.Checks;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.util.AsciiString;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * A collection of standard HTTP headers and objects that convert between them
 * and actual header strings and vice versa. Typical usage would be something
 * like:
 * <pre>
 * response.setHeader(Headers.LAST_MODIFIED, new ZonedDateTime());
 * </pre> This handles conversion between header strings and Java objects
 * without creating a class which dicatates what headers should be or what they
 * should look like.
 *
 * @author Tim Boudreau
 */
public final class Headers {

    private Headers() {
    }
    public static final HeaderValueType<CharSequence> EXPECT = header(HttpHeaderNames.EXPECT);
    public static final HeaderValueType<ZonedDateTime> DATE = new DateTimeHeader(HttpHeaderNames.DATE);
    public static final HeaderValueType<ZonedDateTime> LAST_MODIFIED = new DateTimeHeader(HttpHeaderNames.LAST_MODIFIED);
    public static final HeaderValueType<ZonedDateTime> EXPIRES = new DateTimeHeader(HttpHeaderNames.EXPIRES);
    public static final HeaderValueType<ZonedDateTime> IF_MODIFIED_SINCE = new DateTimeHeader(HttpHeaderNames.IF_MODIFIED_SINCE);
    public static final HeaderValueType<ZonedDateTime> IF_UNMODIFIED_SINCE = new DateTimeHeader(HttpHeaderNames.IF_UNMODIFIED_SINCE);
    public static final HeaderValueType<ZonedDateTime> RETRY_AFTER_DATE = new DateTimeHeader(HttpHeaderNames.RETRY_AFTER);
    public static final HeaderValueType<Duration> RETRY_AFTER_DURATION = new DurationHeader(HttpHeaderNames.RETRY_AFTER);
    public static final HeaderValueType<CharSequence> HOST = header(HttpHeaderNames.HOST);
    public static final HeaderValueType<MediaType> CONTENT_TYPE = new MediaTypeHeader();
    public static final HeaderValueType<CharSequence> SERVER = header(HttpHeaderNames.SERVER);
    public static final HeaderValueType<HeaderValueType[]> VARY = new VaryHeader();
    public static final HeaderValueType<ByteRanges> RANGE = new ByteRangeHeader(HttpHeaderNames.RANGE);
    public static final HeaderValueType<BoundedRange> CONTENT_RANGE = new ContentRangeHeader(HttpHeaderNames.CONTENT_RANGE);
    public static final HeaderValueType<CharSequence> ACCEPT = header(HttpHeaderNames.ACCEPT);
    public static final HeaderValueType<CharSequence> ACCEPT_ENCODING = header(HttpHeaderNames.ACCEPT_ENCODING);
    public static final HeaderValueType<CharSequence> ACCEPT_RANGES = header(HttpHeaderNames.ACCEPT_RANGES);
    public static final HeaderValueType<CharSequence> CONTENT_ENCODING = header(HttpHeaderNames.CONTENT_ENCODING);
    public static final HeaderValueType<CharSequence> USER_AGENT = header(HttpHeaderNames.USER_AGENT);
    public static final HeaderValueType<Connection> CONNECTION = new ConnectionHeader();
    public static final HeaderValueType<Number> CONTENT_LENGTH = new NumberHeader(HttpHeaderNames.CONTENT_LENGTH);
    public static final HeaderValueType<URI> CONTENT_LOCATION = new UriHeader(HttpHeaderNames.CONTENT_LOCATION);
    public static final HeaderValueType<URI> LOCATION = new UriHeader(HttpHeaderNames.LOCATION);
    public static final HeaderValueType<Charset> ACCEPT_CHARSET = new CharsetHeader(HttpHeaderNames.ACCEPT_CHARSET);
    public static final HeaderValueType<Locale> CONTENT_LANGUAGE = new LocaleHeader(HttpHeaderNames.CONTENT_LANGUAGE);
    public static final HeaderValueType<CharSequence> ETAG = new ETagHeader(HttpHeaderNames.ETAG);
    public static final HeaderValueType<CharSequence> IF_NONE_MATCH = new ETagHeader(HttpHeaderNames.IF_NONE_MATCH);
    public static final HeaderValueType<Duration> AGE = new DurationHeader(HttpHeaderNames.AGE);
    public static final HeaderValueType<Duration> RETRY_AFTER = new DurationHeader(HttpHeaderNames.RETRY_AFTER);
    public static final HeaderValueType<BasicCredentials> AUTHORIZATION = new BasicCredentialsHeader();
    public static final HeaderValueType<CacheControl> CACHE_CONTROL = new CacheControlHeader();
    public static final HeaderValueType<Realm> WWW_AUTHENTICATE = new AuthHeader();
    public static final HeaderValueType<Method[]> ALLOW = new AllowHeader(false);
    public static final HeaderValueType<Method[]> ACCESS_CONTROL_ALLOW = new AllowHeader(true);
    public static final HeaderValueType<String[]> ACCESS_CONTROL_ALLOW_ORIGIN = new StringArrayHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN);
    public static final HeaderValueType<Number> ACCESS_CONTROL_ALLOW_MAX_AGE = new NumberHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE);
    public static final HeaderValueType<HeaderValueType<?>[]> ACCESS_CONTROL_ALLOW_HEADERS = new HeaderNamesHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS);
    public static final HeaderValueType<HeaderValueType<?>[]> ACCESS_CONTROL_EXPOSE_HEADERS = new HeaderNamesHeader(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS);
    public static final HeaderValueType<Boolean> ACCESS_CONTROL_ALLOW_CREDENTIALS = new BooleanHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS);
    public static final HeaderValueType<CharSequence> X_REQUESTED_WITH = header(new AsciiString("x-requested-with"));
    public static final HeaderValueType<CharSequence> X_FORWARDED_PROTO = header(new AsciiString("x-forwarded-proto"));
    @Deprecated
    @SuppressWarnings("deprecation")
    public static final HeaderValueType<Cookie> SET_COOKIE = new SetCookieHeader();
    public static final HeaderValueType<io.netty.handler.codec.http.cookie.Cookie> SET_COOKIE_B = new SetCookieHeaderNetty428(HttpHeaderNames.SET_COOKIE, false);
    public static final HeaderValueType<io.netty.handler.codec.http.cookie.Cookie> SET_COOKIE_B_STRICT = new SetCookieHeaderNetty428(HttpHeaderNames.SET_COOKIE, false);
    @Deprecated
    @SuppressWarnings("deprecation")
    public static final HeaderValueType<Cookie[]> COOKIE = new CookieHeader();
    public static final HeaderValueType<io.netty.handler.codec.http.cookie.Cookie[]> COOKIE_B = new CookieHeaderNetty428(false);
    public static final HeaderValueType<io.netty.handler.codec.http.cookie.Cookie[]> COOKIE_B_STRICT = new CookieHeaderNetty428(true);
    public static final HeaderValueType<CharSequence[]> WEBSOCKET_PROTOCOLS = new WebSocketProtocolsHeader();
    public static final HeaderValueType<String> WEBSOCKET_PROTOCOL = new StringHeader(HttpHeaderNames.WEBSOCKET_PROTOCOL);
//    public static final HeaderValueType<URL> WEBSOCKET_LOCATION = new WebSocketLocationHeader();
    public static final HeaderValueType<CharSequence> UPGRADE = header(HttpHeaderNames.UPGRADE);
    public static final HeaderValueType<CharSequence> REFERRER = header(HttpHeaderNames.REFERER);
    public static final HeaderValueType<CharSequence> TRANSFER_ENCODING = header(HttpHeaderNames.TRANSFER_ENCODING);
    public static final HeaderValueType<CharSequence> ACCESS_CONTROL_ALLOW_METHODS = header(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS);
    public static final HeaderValueType<Duration> ACCESS_CONTROL_MAX_AGE = new DurationHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE);
    public static final HeaderValueType<Boolean> X_ACCEL_BUFFERING = new AccelBufferingHeader();
    public static final HeaderValueType<Duration> KEEP_ALIVE = new KeepAliveHeader();
    public static final HeaderValueType<CharSequence> CONTENT_DISPOSITION
            = header(HttpHeaderNames.CONTENT_DISPOSITION);

    public static HeaderValueType<String> stringHeader(CharSequence key) {
        return new StringHeader(key);
    }

    public static HeaderValueType<CharSequence> header(CharSequence key) {
        return new CharSequenceHeader(key);
    }

    public static <T> T read(HeaderValueType<T> type, HttpMessage msg) {
        String val = msg.headers().get(type.name());
        return val == null ? null : type.toValue(val);
    }

    public static <T> CharSequence writeIfNotNull(HeaderValueType<T> type, T value, HttpMessage msg) {
        if (value != null) {
            return write(type, value, msg);
        }
        return null;
    }

    public static <T> CharSequence write(HeaderValueType<T> type, T value, HttpMessage msg) {
        Checks.notNull("type", type);
        Checks.notNull("msg", msg);
        Checks.notNull("value " + type, value);
        CharSequence val = type.toCharSequence(value);
        msg.headers().add(type.name(), val);
        return val;
    }

    public static final DateTimeFormatter ISO2822DateFormat
            = new DateTimeFormatterBuilder()
                    .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT_STANDALONE).appendLiteral(", ")
                    .appendText(ChronoField.DAY_OF_MONTH, TextStyle.FULL).appendLiteral(" ")
                    .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT).appendLiteral(" ")
                    .appendText(ChronoField.YEAR, TextStyle.FULL).appendLiteral(" ")
                    .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(":")
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(":")
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2).appendLiteral(" ")
                    .appendOffsetId().toFormatter();

    static final DateTimeFormatter TWO_DIGIT_YEAR
            = new DateTimeFormatterBuilder()
//                    .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT_STANDALONE).appendLiteral(", ")
                    .appendText(ChronoField.DAY_OF_MONTH, TextStyle.FULL).appendLiteral(" ")
                    .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT).appendLiteral(" ")
                    .appendValue(ChronoField.YEAR, 2, 4, SignStyle.NEVER).appendLiteral(" ")
                    .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(":")
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(":")
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2).appendLiteral(" ")
                    .appendZoneOrOffsetId().toFormatter();

    static final ZoneId UTC = ZoneId.of("GMT");
    public static String toISO2822Date(ZonedDateTime dt) {
//        dt = dt.withZoneSameInstant(UTC);
        return dt.format(ISO2822DateFormat);
//        return dt.toDateTime(DateTimeZone.UTC).toDateTimeISO().toString(
//                ISO2822DateFormat);
    }
}
