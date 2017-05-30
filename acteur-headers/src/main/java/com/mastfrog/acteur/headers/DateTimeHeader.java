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

import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 *
 * @author Tim Boudreau
 */
class DateTimeHeader extends AbstractHeader<DateTime> {

    DateTimeHeader(CharSequence name) {
        super(DateTime.class, name);
    }

    @Override
    public String toString(DateTime value) {
        return Headers.toISO2822Date(value.withMillisOfSecond(0));
    }

    @Override
    @SuppressWarnings("deprecation")
    public DateTime toValue(String value) {
        long val = 0;
        if (val == 0) {
            try {
                val = Headers.ISO2822DateFormat.parseDateTime(value).getMillis();
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
                result = result.withYear(2_000 - (100 - result.getYear())).withDayOfYear(result.getDayOfYear() - 1); //don't ask
            } else {
                result = result.withYear(2_000 + result.getYear());
            }
        }
        return result;
    }

}
