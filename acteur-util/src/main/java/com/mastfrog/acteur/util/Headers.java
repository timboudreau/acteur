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
package com.mastfrog.acteur.util;

import com.google.common.net.MediaType;
import com.mastfrog.url.Host;
import com.mastfrog.url.URL;
import com.mastfrog.util.Checks;
import io.netty.handler.codec.http.ClientCookieEncoder;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.ServerCookieEncoder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
/**
 * A collection of standard HTTP headers and objects that convert between
 * them and actual header strings and vice versa.
 * Typical usage would be something like:
 * <pre>
 * response.setHeader(Headers.LAST_MODIFIED, new DateTime());
 * </pre>
 * This handles conversion between header strings and Java objects without
 * creating a class which dicatates what headers should be or what they should
 * look like.
 *
 * @author Tim Boudreau
 */
public final class Headers {

    private Headers() {
    }
    public static final HeaderValueType<DateTime> DATE = new DateTimeHeader(HttpHeaders.Names.DATE);
    public static final HeaderValueType<DateTime> LAST_MODIFIED = new DateTimeHeader(HttpHeaders.Names.LAST_MODIFIED);
    public static final HeaderValueType<DateTime> EXPIRES = new DateTimeHeader(HttpHeaders.Names.EXPIRES);
    public static final HeaderValueType<DateTime> IF_MODIFIED_SINCE = new DateTimeHeader(HttpHeaders.Names.IF_MODIFIED_SINCE);
    public static final HeaderValueType<DateTime> IF_UNMODIFIED_SINCE = new DateTimeHeader(HttpHeaders.Names.IF_UNMODIFIED_SINCE);
    public static final HeaderValueType<DateTime> RETRY_AFTER_DATE = new DateTimeHeader(HttpHeaders.Names.RETRY_AFTER);
    public static final HeaderValueType<Duration> RETRY_AFTER_DURATION = new DurationHeader(HttpHeaders.Names.RETRY_AFTER);
    public static final HeaderValueType<Host> HOST = new HostHeader(HttpHeaders.Names.HOST);

    public static final HeaderValueType<MediaType> CONTENT_TYPE = new MediaTypeHeader();
    public static final HeaderValueType<String> SERVER = new StringHeader(HttpHeaders.Names.SERVER);
    public static final HeaderValueType<HeaderValueType[]> VARY = new VaryHeader();
    public static final HeaderValueType<String> ACCEPT_ENCODING = new StringHeader(HttpHeaders.Names.ACCEPT_ENCODING);
    public static final HeaderValueType<String> CONTENT_ENCODING = new StringHeader(HttpHeaders.Names.CONTENT_ENCODING);
    public static final HeaderValueType<String> USER_AGENT = new StringHeader(HttpHeaders.Names.USER_AGENT);
    public static final HeaderValueType<Connection> CONNECTION = new ConnectionHeader();
    public static final HeaderValueType<Long> CONTENT_LENGTH = new LongHeader(HttpHeaders.Names.CONTENT_LENGTH);
    public static final HeaderValueType<URI> CONTENT_LOCATION = new UriHeader(HttpHeaders.Names.CONTENT_LOCATION);
    public static final HeaderValueType<URI> LOCATION = new UriHeader(HttpHeaders.Names.LOCATION);
    public static final HeaderValueType<Charset> ACCEPT_CHARSET = new CharsetHeader(HttpHeaders.Names.ACCEPT_CHARSET);
    public static final HeaderValueType<Locale> CONTENT_LANGUAGE = new LocaleHeader(HttpHeaders.Names.CONTENT_LANGUAGE);
    public static final HeaderValueType<String> ETAG = new ETagHeader(HttpHeaders.Names.ETAG);
    public static final HeaderValueType<String> IF_NONE_MATCH = new ETagHeader(HttpHeaders.Names.IF_NONE_MATCH);
    public static final HeaderValueType<Duration> AGE = new DurationHeader(HttpHeaders.Names.AGE);
    public static final HeaderValueType<Duration> RETRY_AFTER = new DurationHeader(HttpHeaders.Names.RETRY_AFTER);
    public static final HeaderValueType<BasicCredentials> AUTHORIZATION = new BasicCredentialsHeader();
    public static final HeaderValueType<CacheControl> CACHE_CONTROL = new CacheControlHeader();
    public static final HeaderValueType<Realm> WWW_AUTHENTICATE = new AuthHeader();
    public static final HeaderValueType<Method[]> ALLOW = new AllowHeader(false);
    public static final HeaderValueType<Method[]> ACCESS_CONTROL_ALLOW = new AllowHeader(true);
    public static final HeaderValueType<Cookie> SET_COOKIE = new SetCookieHeader();
    public static final HeaderValueType<Cookie[]> COOKIE = new CookieHeader();
    public static final HeaderValueType<String[]> WEBSOCKET_PROTOCOLS = new WebSocketProtocolsHeader();
    public static final HeaderValueType<String> WEBSOCKET_PROTOCOL = new StringHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL);
    public static final HeaderValueType<URL> WEBSOCKET_LOCATION = new WebSocketLocationHeader();
    public static final HeaderValueType<String> UPGRADE = stringHeader(HttpHeaders.Names.UPGRADE);

    public static HeaderValueType<String> stringHeader(String key) {
        return new StringHeader(key);
    }

    private static final class WebSocketLocationHeader extends AbstractHeader<URL> {

        public WebSocketLocationHeader() {
            super(URL.class, HttpHeaders.Names.WEBSOCKET_LOCATION);
        }

        @Override
        public String toString(URL value) {
            return value.toUnescapedForm();
        }

        @Override
        public URL toValue(String value) {
            return URL.parse(value);
        }
    }

    private static final class WebSocketProtocolsHeader extends AbstractHeader<String[]> {

        WebSocketProtocolsHeader() {
            super(String[].class, HttpHeaders.Names.WEBSOCKET_PROTOCOL);
        }

        @Override
        public String toString(String[] value) {
            StringBuilder sb = new StringBuilder();
            for (String s : value) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(s);
            }
            return sb.toString();
        }

        @Override
        public String[] toValue(String value) {
            String[] result = value.split(",");
            for (int i=0; i < result.length; i++) {
                result[i] = result[i].trim();
            }
            return result;
        }
    }

    private static final class CookieHeader extends AbstractHeader<Cookie[]> {
        CookieHeader() {
            super(Cookie[].class, HttpHeaders.Names.COOKIE);
        }

        @Override
        public String toString(Cookie[] value) {
            return ClientCookieEncoder.encode(value);
        }

        @Override
        public Cookie[] toValue(String value) {
            return CookieDecoder.decode(value).toArray(new Cookie[0]);
        }
    }
    private static final class SetCookieHeader extends AbstractHeader<Cookie> {
        SetCookieHeader() {
            super(Cookie.class, HttpHeaders.Names.SET_COOKIE);
        }

        @Override
        public String toString(Cookie value) {
            return ServerCookieEncoder.encode(value);
        }

        @Override
        public Cookie toValue(String value) {
            return CookieDecoder.decode(value).iterator().next();
        }
    }

    private static final class MediaTypeHeader extends AbstractHeader<MediaType> {
        MediaTypeHeader() {
            super(MediaType.class, HttpHeaders.Names.CONTENT_TYPE);
        }

        @Override
        public String toString(MediaType value) {
            return value.toString();
        }

        @Override
        public MediaType toValue(String value) {
            try {
                return MediaType.parse(value);
            } catch (IllegalArgumentException e) {
                Logger.getLogger(MediaTypeHeader.class.getName()).log(Level.WARNING,
                        "Bad media type {0}", value);
                return null;
            }
        }
    }
    private static final class AllowHeader extends AbstractHeader<Method[]> {

        AllowHeader(boolean isAllowOrigin) {
            super(Method[].class, isAllowOrigin ? "Access-Control-Allow-Methods" : HttpHeaders.Names.ALLOW);
        }

        @Override
        public String toString(Method[] value) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.length; i++) {
                sb.append(value[i].name());
                if (i != value.length - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }

        @Override
        public Method[] toValue(String value) {
            String[] s = value.split(",");
            Method[] result = new Method[s.length];
            for (int i = 0; i < s.length; i++) {
                try {
                    result[i] = Method.valueOf(s[i]);
                } catch (Exception e) {
                    Logger.getLogger(AllowHeader.class.getName()).log(Level.INFO, "Bad methods in allow header '" + value + "'", e);
                    return null;
                }
            }
            return result;
        }
    }

    private static final class VaryHeader extends AbstractHeader<HeaderValueType[]> {

        VaryHeader() {
            super(HeaderValueType[].class, HttpHeaders.Names.VARY);
        }

        @Override
        public String toString(HeaderValueType[] value) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.length; i++) {
                sb.append(value[i].name());
                if (i != value.length - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }

        @Override
        public HeaderValueType<?>[] toValue(String value) {
            String[] s = value.split(",");
            HeaderValueType<?>[] result = new HeaderValueType<?>[s.length];
            for (int i = 0; i < s.length; i++) {
                result[i] = new StringHeader(s[i].trim());
            }
            return result;
        }
    }

    private static class UriHeader extends AbstractHeader<URI> {

        UriHeader(String name) {
            super(URI.class, name);
        }

        @Override
        public String toString(URI value) {
            return value.toString();
        }

        @Override
        public URI toValue(String value) {
            try {
                return new URI(value);
            } catch (URISyntaxException ex) {
                Logger.getLogger(Headers.class.getName()).log(Level.SEVERE,
                        "Bad URI in " + name() + " - " + value, ex);
                return null;
            }
        }
    }

    private static final class AuthHeader extends AbstractHeader<Realm> {

        AuthHeader() {
            super(Realm.class, HttpHeaders.Names.WWW_AUTHENTICATE);
        }

        @Override
        public String toString(Realm value) {
            return "Basic realm=\"" + value.toString() + "\"";
        }

        @Override
        public Realm toValue(String value) {
            return Realm.createSimple(value);
        }
    }

    public static <T> T read(HeaderValueType<T> type, HttpMessage msg) {
        String val = msg.headers().get(type.name());
        return val == null ? null : type.toValue(val);
    }

    public static <T> String write(HeaderValueType<T> type, T value, HttpMessage msg) {
        Checks.notNull("type", type);
        Checks.notNull("msg", msg);
        Checks.notNull("value " + type, value);
        String val = type.toString(value);
        msg.headers().add(type.name(), val);
        return val;
    }

    private static final class ConnectionHeader extends AbstractHeader<Connection> {

        ConnectionHeader() {
            super(Connection.class, HttpHeaders.Names.CONNECTION);
        }

        @Override
        public String toString(Connection value) {
            return value.toString();
        }

        @Override
        public Connection toValue(String value) {
            for (Connection c : Connection.values()) {
                if (value.toLowerCase().equals(c.toString())) {
                    return c;
                }
            }
            return null;
        }
    }

    private static final class DurationHeader extends AbstractHeader<Duration> {

        DurationHeader(String name) {
            super(Duration.class, name);
        }

        @Override
        public String toString(Duration value) {
            return value.getStandardSeconds() + "";
        }

        @Override
        public Duration toValue(String value) {
            try {
                return new Duration(Long.parseLong(value));
            } catch (NumberFormatException nfe) {
                Logger.getLogger(DurationHeader.class.getName()).log(Level.INFO,
                        "Bad duration header '" + value + "'", nfe);
                return null;
            }
        }
    }
    public static final DateTimeFormatter ISO2822DateFormat =
            new DateTimeFormatterBuilder().appendDayOfWeekShortText()
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

    private static class DateTimeHeader extends AbstractHeader<DateTime> {

        DateTimeHeader(String name) {
            super(DateTime.class, name);
        }

        @Override
        public String toString(DateTime value) {
            return toISO2822Date(value);
        }

        @Override
        public DateTime toValue(String value) {
            long val = 0;
            if (val == 0) {
                try {
                    val = ISO2822DateFormat.parseDateTime(value).getMillis();
                } catch (IllegalArgumentException e) {
                    try {
                        //Sigh...use java.util.date to handle "GMT", "PST", "EST"
                        val = Date.parse(value);
                    } catch (IllegalArgumentException ex) {
                        new IllegalArgumentException(value, ex).printStackTrace();
                        return null;
                    }
                }
            }
            DateTime result = new DateTime(val, DateTimeZone.UTC);
            //to be truly compliant, accept 2-digit dates
            if (result.getYear() < 100 && result.getYear() > 0) {
                if (result.getYear() >= 50) {
                    result = result.withYear(2000 - (100 - result.getYear())).withDayOfYear(result.getDayOfYear() - 1); //don't ask
                } else {
                    result = result.withYear(2000 + result.getYear());
                }
            }
            return result;
        }
    }

    static class StringHeader extends AbstractHeader<String> {

        StringHeader(String name) {
            super(String.class, name);
        }

        @Override
        public String toString(String value) {
            return value;
        }

        @Override
        public String toValue(String value) {
            return value;
        }
    }

    static class LongHeader extends AbstractHeader<Long> {

        LongHeader(String name) {
            super(Long.TYPE, name);
        }

        @Override
        public String toString(Long value) {
            return value.toString();
        }

        @Override
        public Long toValue(String value) {
            return Long.parseLong(value);
        }
    }

    static class IntHeader extends AbstractHeader<Integer> {

        IntHeader(String name) {
            super(Integer.TYPE, name);
        }

        @Override
        public String toString(Integer value) {
            return value.toString();
        }

        @Override
        public Integer toValue(String value) {
            return Integer.parseInt(value);
        }
    }

    static class ETagHeader extends AbstractHeader<String> {

        ETagHeader(String name) {
            super(String.class, name);
        }

        @Override
        public String toString(String value) {
            return '"' + value + '"';
        }

        @Override
        public String toValue(String value) {
            if (value.length() > 1) {
                if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                    return value.substring(1, value.length() - 1);
                }
            }
            return value;
        }
    }

    static class HostHeader extends AbstractHeader<Host> {

        HostHeader(String name) {
            super(Host.class, name);
        }

        @Override
        public String toString(Host value) {
            return value.toString();
        }

        @Override
        public Host toValue(String value) {
            return Host.parse(value);
        }
    }

    static class CharsetHeader extends AbstractHeader<Charset> {

        CharsetHeader(String name) {
            super(Charset.class, name);
        }

        @Override
        public String toString(Charset value) {
            return value.name();
        }

        @Override
        public Charset toValue(String value) {
            return Charset.forName(value);
        }
    }

    static class LocaleHeader extends AbstractHeader<Locale> {

        LocaleHeader(String name) {
            super(Locale.class, name);
        }

        @Override
        public String toString(Locale value) {
            return value.toLanguageTag();
        }

        @Override
        public Locale toValue(String value) {
            return Locale.forLanguageTag(value);
        }
    }

    static class BasicCredentialsHeader extends AbstractHeader<BasicCredentials> {

        BasicCredentialsHeader() {
            super(BasicCredentials.class, HttpHeaders.Names.AUTHORIZATION);
        }

        @Override
        public String toString(BasicCredentials value) {
            return value.toString();
        }

        @Override
        public BasicCredentials toValue(String value) {
            return BasicCredentials.parse(value);
        }
    }

    static final class CacheControlHeader extends AbstractHeader<CacheControl> {

        CacheControlHeader() {
            super(CacheControl.class, HttpHeaders.Names.CACHE_CONTROL);
        }

        @Override
        public String toString(CacheControl value) {
            return value.toString();
        }

        @Override
        public CacheControl toValue(String value) {
            return CacheControl.fromString(value);
        }
    }
}
