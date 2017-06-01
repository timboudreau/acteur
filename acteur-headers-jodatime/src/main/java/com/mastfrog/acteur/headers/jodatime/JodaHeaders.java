/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.acteur.headers.jodatime;

import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.util.CacheControl;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

/**
 * Joda Time headers originally part of acteur-headers.
 *
 * @author Tim Boudreau
 */
public class JodaHeaders {

    private JodaHeaders() {
    }
    public static final HeaderValueType<DateTime> DATE = new JodaDateTimeHeader(HttpHeaderNames.DATE);
    public static final HeaderValueType<DateTime> LAST_MODIFIED = new JodaDateTimeHeader(HttpHeaderNames.LAST_MODIFIED);
    public static final HeaderValueType<DateTime> EXPIRES = new JodaDateTimeHeader(HttpHeaderNames.EXPIRES);
    public static final HeaderValueType<DateTime> IF_MODIFIED_SINCE = new JodaDateTimeHeader(HttpHeaderNames.IF_MODIFIED_SINCE);
    public static final HeaderValueType<DateTime> IF_UNMODIFIED_SINCE = new JodaDateTimeHeader(HttpHeaderNames.IF_UNMODIFIED_SINCE);
    public static final HeaderValueType<Duration> KEEP_ALIVE = new JodaKeepAliveHeader();
    public static final HeaderValueType<DateTime> RETRY_AFTER_DATE = new JodaDateTimeHeader(HttpHeaderNames.RETRY_AFTER);
    public static final HeaderValueType<Duration> AGE = new JodaDurationHeader(HttpHeaderNames.AGE);
    public static final HeaderValueType<Duration> RETRY_AFTER = new JodaDurationHeader(HttpHeaderNames.RETRY_AFTER);
    public static final HeaderValueType<Duration> RETRY_AFTER_DURATION = new JodaDurationHeader(HttpHeaderNames.RETRY_AFTER);
    public static final HeaderValueType<Duration> ACCESS_CONTROL_MAX_AGE = new JodaDurationHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE);
    public static final HeaderValueType<CacheControl> CACHE_CONTROL = new JodaCacheControlHeader();

    public static final DateTimeFormatter ISO2822DateFormat
            = new DateTimeFormatterBuilder().appendDayOfWeekShortText()
                    .appendLiteral(", ").appendDayOfMonth(2).appendLiteral(" ")
                    .appendMonthOfYearShortText().appendLiteral(" ")
                    .appendYear(4, 4).appendLiteral(" ").appendHourOfDay(2)
                    .appendLiteral(":").appendMinuteOfHour(2).appendLiteral(":")
                    .appendSecondOfMinute(2).appendLiteral(" ")
                    .appendTimeZoneOffset("GMT", true, 2, 2) //                .appendLiteral(" GMT")
                    .toFormatter();
}
