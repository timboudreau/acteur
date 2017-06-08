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
package com.mastfrog.acteur.headers.jodatime;

import com.mastfrog.acteur.headers.AbstractHeader;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 *
 * @author Tim Boudreau
 */
class JodaDateTimeHeader extends AbstractHeader<DateTime> {

    JodaDateTimeHeader(CharSequence name) {
        super(DateTime.class, name);
    }

    @Override
    public String toString(DateTime value) {
        return JodaHeaders.ISO2822DateFormat.print(value.withMillisOfSecond(0));
    }

    @Override
    @SuppressWarnings("deprecation")
    public DateTime toValue(CharSequence value) {
        String string = value.toString();
        long val = 0;
        if (val == 0) {
            try {
                val = JodaHeaders.ISO2822DateFormat.parseDateTime(string).getMillis();
            } catch (IllegalArgumentException e) {
                try {
                    //Sigh...use java.util.date to handle "GMT", "PST", "EST"
                    val = Date.parse(string);
                } catch (IllegalArgumentException ex) {
                    new IllegalArgumentException(string, ex).printStackTrace();
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
