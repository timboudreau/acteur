/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.acteur.output;

import com.google.inject.ImplementedBy;
import com.mastfrog.util.preconditions.Checks;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Provides array formatting for stream contents when using
 * ObjectStreamActeur to write a stream to the response.
 */
@ImplementedBy(value = JsonArrayFormatting.class)
public interface ArrayFormatting {

    static final ArrayFormatting JSON = new JsonArrayFormatting();

    String openingDelimiter();

    String closingDelimiter();

    String interItemDelimiter();

    default int batchBytes() {
        return 512;
    }

    default HttpResponseStatus successStatus() {
        return HttpResponseStatus.OK;
    }

    default ArrayFormatting withOpeningDelimiter(String delim) {
        String old = openingDelimiter();
        if (old.equals(Checks.notNull("delim", delim))) {
            return this;
        }
        return new GeneralArrayFormatting(delim, closingDelimiter(), interItemDelimiter(), batchBytes(), successStatus());
    }

    default ArrayFormatting withClosingDelimiter(String delim) {
        String old = closingDelimiter();
        if (old.equals(Checks.notNull("delim", delim))) {
            return this;
        }
        return new GeneralArrayFormatting(openingDelimiter(), delim, interItemDelimiter(), batchBytes(), successStatus());
    }

    default ArrayFormatting withInterItemDelimiter(String delim) {
        String old = interItemDelimiter();
        if (old.equals(Checks.notNull("delim", delim))) {
            return this;
        }
        return new GeneralArrayFormatting(openingDelimiter(), closingDelimiter(), delim, batchBytes(), successStatus());
    }

    default ArrayFormatting withBatchBytes(int batchBytes) {
        int old = batchBytes();
        if (old == Checks.greaterThanZero("batchBytes", batchBytes)) {
            return this;
        }
        return new GeneralArrayFormatting(openingDelimiter(), closingDelimiter(), interItemDelimiter(), batchBytes, successStatus());
    }

    default ArrayFormatting withSuccessStatus(HttpResponseStatus status) {
        HttpResponseStatus old = successStatus();
        if (old.equals(Checks.notNull("status", status))) {
            return this;
        }
        return new GeneralArrayFormatting(openingDelimiter(), closingDelimiter(), interItemDelimiter(), batchBytes(), status);
    }

}
