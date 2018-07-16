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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class DurationHeader extends AbstractHeader<Duration> {

    DurationHeader(CharSequence name) {
        super(Duration.class, name);
    }

    @Override
    public CharSequence toCharSequence(Duration value) {
        Checks.notNull("value", value);
        return Long.toString(value.getSeconds());
    }

    @Override
    public Duration toValue(CharSequence value) {
        Checks.notNull("value", value);
        try {
            return Duration.of(Strings.parseLong(value), ChronoUnit.SECONDS);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(DurationHeader.class.getName()).log(Level.INFO, "Bad duration header '" + value + "'", nfe);
            return Duration.ZERO;
        }
    }

}
