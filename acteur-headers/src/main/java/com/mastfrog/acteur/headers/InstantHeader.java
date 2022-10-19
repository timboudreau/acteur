/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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

import static com.mastfrog.acteur.headers.DateTimeHeader.GMT;
import static com.mastfrog.acteur.headers.Headers.ISO2822DateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 *
 * @author Tim Boudreau
 */
final class InstantHeader implements TimestampHeader<Instant> {

    private final DateTimeHeader delegate;

    InstantHeader(DateTimeHeader delegate) {
        this.delegate = delegate;
    }

    @Override
    public Class<Instant> type() {
        return Instant.class;
    }

    @Override
    public CharSequence name() {
        return delegate.name();
    }

    @Override
    public Instant toValue(CharSequence value) {
        return delegate.toValue(value).toInstant();
    }

    @Override
    @SuppressWarnings("deprecation")
    public String toString(Instant value) {
        return delegate.toString(ZonedDateTime.ofInstant(value, GMT));
    }

    @Override
    public CharSequence toCharSequence(Instant value) {
        return delegate.toCharSequence(ZonedDateTime.ofInstant(value, GMT));
    }

    @Override
    public TimestampHeader<Instant> toInstantHeader() {
        return this;
    }

}
