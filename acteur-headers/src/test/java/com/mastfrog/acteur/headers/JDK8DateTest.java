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

package com.mastfrog.acteur.headers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class JDK8DateTest {

    /*
    public static final DateTimeFormatter ISO2822DateFormat
            = new DateTimeFormatterBuilder().appendDayOfWeekShortText()
            .appendLiteral(", ").appendDayOfMonth(2).appendLiteral(" ")
            .appendMonthOfYearShortText().appendLiteral(" ")
            .appendYear(4, 4).appendLiteral(" ").appendHourOfDay(2)
            .appendLiteral(":").appendMinuteOfHour(2).appendLiteral(":")
            .appendSecondOfMinute(2).appendLiteral(" ")
            .appendTimeZoneOffset("GMT", true, 2, 2) //                .appendLiteral(" GMT")
            .toFormatter();

    */

    @Test
    public void test() {
        long now = System.currentTimeMillis();
        org.joda.time.DateTime dt =  new org.joda.time.DateTime(now);
        ZonedDateTime ldt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault());

        System.out.println("A " + dt);
        System.out.println("B " + ldt);
        
        String s = Headers.ISO2822DateFormat.print(dt);
        System.out.println(s);

        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT_STANDALONE).appendLiteral(", ")
                .appendText(ChronoField.DAY_OF_MONTH, TextStyle.FULL).appendLiteral(" ")
                .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT).appendLiteral(" ")
                .appendText(ChronoField.YEAR, TextStyle.FULL).appendLiteral(" ")
                .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(":")
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(":")
                .appendValue(ChronoField.SECOND_OF_MINUTE, 2).appendLiteral(" ")
                .appendOffsetId().toFormatter();

        String jt = fmt.format(ldt);
        System.out.println(jt);

        assertEquals(s, jt);
        
        assertEquals(dt.getMillis(), ldt.toInstant().toEpochMilli());
        
        System.out.println(dt.getMillis() + "\n\n");
//        System.out.println(ldt.toInstant().toEpochMilli());

    }

}
