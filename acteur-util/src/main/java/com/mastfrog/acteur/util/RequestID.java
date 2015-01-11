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

import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Singleton;
import org.joda.time.Duration;

/**
 * Unique ID for an HTTP request which can be injected
 *
 * @author Tim Boudreau
 */
public final class RequestID {

    public final int index;
    public final long time = System.currentTimeMillis();
    // A trivial unique per run id to uniquify id strings
    private static final String RUN_ID = Long.toString(
            (System.currentTimeMillis() - 1420954494414L) / 60000, 36);

    private RequestID(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public Duration getDuration() {
        return new Duration(System.currentTimeMillis() - time);
    }

    public String stringValue() {
        return RUN_ID + "-" + index;
    }

    @Override
    public String toString() {
        return RUN_ID + "-" + index + "/" + getDuration().getMillis() + "ms";
    }

    @Singleton
    public static final class Factory {

        private final AtomicInteger indexSource = new AtomicInteger();

        public RequestID next() {
            return new RequestID(indexSource.getAndIncrement());
        }
    }
}
