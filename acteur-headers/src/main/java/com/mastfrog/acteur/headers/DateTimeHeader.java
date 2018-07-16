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

import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.time.TimeUtil;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Date;

/**
 *
 * @author Tim Boudreau
 */
class DateTimeHeader extends AbstractHeader<ZonedDateTime> {

    DateTimeHeader(CharSequence name) {
        super(ZonedDateTime.class, name);
    }

    @Override
    public String toString(ZonedDateTime value) {
        Checks.notNull("value", value);
        value = value.withZoneSameInstant(ZoneId.systemDefault());
        return Headers.toISO2822Date(value);
    }

    private ZonedDateTime mungeYear(ZonedDateTime dt) {
        int yr = dt.get(ChronoField.YEAR);
        if (yr < 100 && yr >= 0) {
            if (yr >= 50) {
                yr += 1900;
            } else {
                yr += 2000;
            }
            dt = dt.withYear(yr);
        }
        return dt;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ZonedDateTime toValue(CharSequence value) {
        Checks.notNull("value", value);
        // Be permissive in what you accept, as they say
        long val;
        ZonedDateTime result;
        try {
            ZonedDateTime top = ZonedDateTime.parse(value, Headers.ISO2822DateFormat);
            result = mungeYear(top);
        } catch (DateTimeParseException e) {
            try {
                ZonedDateTime rfs = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                result = mungeYear(rfs);
            } catch (DateTimeParseException e1) {
                e.addSuppressed(e1);
                try {
                    CharSequence munged = value;
                    int space = Strings.indexOf(' ', munged);
                    if (space != -1) {
                        munged = value.subSequence(space + 1, value.length());
                    }
                    ZonedDateTime dt = ZonedDateTime.parse(munged, Headers.TWO_DIGIT_YEAR);
                    result = mungeYear(dt);
                } catch (DateTimeParseException ex2) {
                    e.addSuppressed(ex2);
                    try {
                        //Sigh...use java.util.date to handle "GMT", "PST", "EST"
                        val = Date.parse(value.toString());
                        result = TimeUtil.fromUnixTimestamp(val);
                    } catch (IllegalArgumentException e3) {
                        e.addSuppressed(e3);
                        new IllegalArgumentException(value.toString(), e).printStackTrace(System.err);
                        return null;
                    }
                }
            }
        }
//        result = result.withZoneSameInstant(ZoneId.of("America/New_York"));
//        ZonedDateTime result = TimeUtil.fromUnixTimestamp(val).withZoneSameInstant(Headers.UTC);
        //to be truly compliant, accept 2-digit dates
//        if (result.getYear() < 100 && result.getYear() > 0) {
//            if (result.getYear() >= 50) {
//                result = result.withYear(2_000 - (100 - result.getYear())).withDayOfYear(result.getDayOfYear() - 1); //don't ask
//            } else {
//                result = result.withYear(2_000 + result.getYear());
//            }
//        }
        return result.withZoneSameInstant(ZoneId.systemDefault());
    }

}
