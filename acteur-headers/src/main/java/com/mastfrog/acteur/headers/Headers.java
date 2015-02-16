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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

/**
 * A collection of standard HTTP headers and objects that convert between them
 * and actual header strings and vice versa. Typical usage would be something
 * like:
 * <pre>
 * response.setHeader(Headers.LAST_MODIFIED, new DateTime());
 * </pre> This handles conversion between header strings and Java objects
 * without creating a class which dicatates what headers should be or what they
 * should look like.
 *
 * @author Tim Boudreau
 */
public final class Headers {

    private Headers() {
    }
    public static final HeaderValueType<DateTime> DATE = new DateTimeHeader(HttpHeaders.Names.DATE.toString());
    public static final HeaderValueType<DateTime> LAST_MODIFIED = new DateTimeHeader(HttpHeaders.Names.LAST_MODIFIED.toString());
    public static final HeaderValueType<DateTime> EXPIRES = new DateTimeHeader(HttpHeaders.Names.EXPIRES.toString());
    public static final HeaderValueType<DateTime> IF_MODIFIED_SINCE = new DateTimeHeader(HttpHeaders.Names.IF_MODIFIED_SINCE.toString());
    public static final HeaderValueType<DateTime> IF_UNMODIFIED_SINCE = new DateTimeHeader(HttpHeaders.Names.IF_UNMODIFIED_SINCE.toString());
    public static final HeaderValueType<DateTime> RETRY_AFTER_DATE = new DateTimeHeader(HttpHeaders.Names.RETRY_AFTER.toString());
    public static final HeaderValueType<Duration> RETRY_AFTER_DURATION = new DurationHeader(HttpHeaders.Names.RETRY_AFTER.toString());
//    public static final HeaderValueType<Host> HOST = new HostHeader(HttpHeaders.Names.HOST);
    public static final HeaderValueType<String> HOST = new HostHeader(HttpHeaders.Names.HOST.toString());

    public static final HeaderValueType<MediaType> CONTENT_TYPE = new MediaTypeHeader();
    public static final HeaderValueType<String> SERVER = new StringHeader(HttpHeaders.Names.SERVER.toString());
    public static final HeaderValueType<HeaderValueType[]> VARY = new VaryHeader();
    public static final HeaderValueType<ByteRanges> RANGE = new ByteRangeHeader(HttpHeaders.Names.RANGE.toString());
    public static final HeaderValueType<BoundedRange> CONTENT_RANGE = new ContentRangeHeader(HttpHeaders.Names.CONTENT_RANGE.toString());
    public static final HeaderValueType<String> ACCEPT = new StringHeader(HttpHeaders.Names.ACCEPT.toString());
    public static final HeaderValueType<String> ACCEPT_ENCODING = new StringHeader(HttpHeaders.Names.ACCEPT_ENCODING.toString());
    public static final HeaderValueType<String> ACCEPT_RANGES = new StringHeader(HttpHeaders.Names.ACCEPT_RANGES.toString());
    public static final HeaderValueType<String> CONTENT_ENCODING = new StringHeader(HttpHeaders.Names.CONTENT_ENCODING.toString());
    public static final HeaderValueType<String> USER_AGENT = new StringHeader(HttpHeaders.Names.USER_AGENT.toString());
    public static final HeaderValueType<Connection> CONNECTION = new ConnectionHeader();
    public static final HeaderValueType<Long> CONTENT_LENGTH = new LongHeader(HttpHeaders.Names.CONTENT_LENGTH.toString());
    public static final HeaderValueType<URI> CONTENT_LOCATION = new UriHeader(HttpHeaders.Names.CONTENT_LOCATION.toString());
    public static final HeaderValueType<URI> LOCATION = new UriHeader(HttpHeaders.Names.LOCATION.toString());
    public static final HeaderValueType<Charset> ACCEPT_CHARSET = new CharsetHeader(HttpHeaders.Names.ACCEPT_CHARSET.toString());
    public static final HeaderValueType<Locale> CONTENT_LANGUAGE = new LocaleHeader(HttpHeaders.Names.CONTENT_LANGUAGE.toString());
    public static final HeaderValueType<String> ETAG = new ETagHeader(HttpHeaders.Names.ETAG.toString());
    public static final HeaderValueType<String> IF_NONE_MATCH = new ETagHeader(HttpHeaders.Names.IF_NONE_MATCH.toString());
    public static final HeaderValueType<Duration> AGE = new DurationHeader(HttpHeaders.Names.AGE.toString());
    public static final HeaderValueType<Duration> RETRY_AFTER = new DurationHeader(HttpHeaders.Names.RETRY_AFTER.toString());
    public static final HeaderValueType<BasicCredentials> AUTHORIZATION = new BasicCredentialsHeader();
    public static final HeaderValueType<CacheControl> CACHE_CONTROL = new CacheControlHeader();
    public static final HeaderValueType<Realm> WWW_AUTHENTICATE = new AuthHeader();
    public static final HeaderValueType<Method[]> ALLOW = new AllowHeader(false);
    public static final HeaderValueType<Method[]> ACCESS_CONTROL_ALLOW = new AllowHeader(true);
    public static final HeaderValueType<Cookie> SET_COOKIE = new SetCookieHeader();
    public static final HeaderValueType<Cookie[]> COOKIE = new CookieHeader();
    public static final HeaderValueType<String[]> WEBSOCKET_PROTOCOLS = new WebSocketProtocolsHeader();
    public static final HeaderValueType<String> WEBSOCKET_PROTOCOL = new StringHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL);
//    public static final HeaderValueType<URL> WEBSOCKET_LOCATION = new WebSocketLocationHeader();
    public static final HeaderValueType<String> UPGRADE = stringHeader(HttpHeaders.Names.UPGRADE.toString());
    public static final HeaderValueType<String> REFERRER = stringHeader(HttpHeaders.Names.REFERER.toString());
    public static final HeaderValueType<String> TRANSFER_ENCODING = stringHeader(HttpHeaders.Names.TRANSFER_ENCODING.toString());
    public static final HeaderValueType<String> ACCESS_CONTROL_ALLOW_ORIGIN = stringHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN.toString());
    public static final HeaderValueType<String> ACCESS_CONTROL_ALLOW_METHODS = stringHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS.toString());
    public static final HeaderValueType<HeaderValueType<?>[]> ACCESS_CONTROL_ALLOW_HEADERS = new HeaderNamesHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS);
    public static final HeaderValueType<Duration> ACCESS_CONTROL_MAX_AGE = new DurationHeader(HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE.toString());

    public static HeaderValueType<String> stringHeader(String key) {
        return new StringHeader(key);
    }

    public static <T> T read(HeaderValueType<T> type, HttpMessage msg) {
        String val = msg.headers().get(type.name());
        return val == null ? null : type.toValue(val);
    }

    public static <T> String writeIfNotNull(HeaderValueType<T> type, T value, HttpMessage msg) {
        if (value != null) {
            return write(type, value, msg);
        }
        return null;
    }

    public static <T> String write(HeaderValueType<T> type, T value, HttpMessage msg) {
        Checks.notNull("type", type);
        Checks.notNull("msg", msg);
        Checks.notNull("value " + type, value);
        String val = type.toString(value);
        msg.headers().add(type.name(), val);
        return val;
    }

    public static final DateTimeFormatter ISO2822DateFormat
            = new DateTimeFormatterBuilder().appendDayOfWeekShortText()
            .appendLiteral(", ").appendDayOfMonth(2).appendLiteral(" ")
            .appendMonthOfYearShortText().appendLiteral(" ")
            .appendYear(4, 4).appendLiteral(" ").appendHourOfDay(2)
            .appendLiteral(":").appendMinuteOfHour(2).appendLiteral(":")
            .appendSecondOfMinute(2).appendLiteral(" ")
            .appendTimeZoneOffset("GMT", true, 2, 2) //                .appendLiteral(" GMT")
            .toFormatter();

    public static String toISO2822Date(DateTime dt) {
        dt = new DateTime(dt.getMillis(), DateTimeZone.UTC);
        return dt.toDateTime(DateTimeZone.UTC).toDateTimeISO().toString(
                ISO2822DateFormat);
    }

    public static HeaderValueType<String> custom(String name) {
        return new StringHeader(name);
    }
}
