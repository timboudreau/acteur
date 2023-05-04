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

import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
class GeneralArrayFormatting implements ArrayFormatting {

    final String openingDelimiter;
    final String closingDelimiter;
    final String interItemDelimiter;
    final int batchBytes;
    final HttpResponseStatus successStatus;

    public GeneralArrayFormatting(String openingDelimiter, String closingDelimiter, String interItemDelimiter, int batchBytes, HttpResponseStatus successStatus) {
        this.openingDelimiter = openingDelimiter;
        this.closingDelimiter = closingDelimiter;
        this.interItemDelimiter = interItemDelimiter;
        this.batchBytes = batchBytes;
        this.successStatus = successStatus;
    }

    @Override
    public String openingDelimiter() {
        return openingDelimiter;
    }

    @Override
    public String closingDelimiter() {
        return closingDelimiter;
    }

    @Override
    public String interItemDelimiter() {
        return interItemDelimiter;
    }

    @Override
    public int batchBytes() {
        return batchBytes;
    }

    @Override
    public HttpResponseStatus successStatus() {
        return successStatus;
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof ArrayFormatting)) {
            return false;
        }
        return equal(this, (ArrayFormatting) o);
    }

    public int hashCode() {
        return hash(this);
    }

    public String toString() {
        return stringify(this);
    }

    static boolean equal(ArrayFormatting a, ArrayFormatting b) {
        if (a == b) {
            return true;
        } else if ((a == null) != (b == null)) {
            return false;
        }
        return a.batchBytes() == b.batchBytes() && a.successStatus().equals(b.successStatus()) && a.openingDelimiter().equals(b.openingDelimiter()) && a.closingDelimiter().equals(b.closingDelimiter()) && a.interItemDelimiter().equals(b.interItemDelimiter());
    }

    static int hash(ArrayFormatting s) {
        return Objects.hash(s.successStatus(), s.openingDelimiter(), s.closingDelimiter(), s.interItemDelimiter()) + (71 * s.batchBytes());
    }

    private static String stringify(ArrayFormatting s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.batchBytes()).append(':').append(s.successStatus()).append(s.openingDelimiter()).append(s.interItemDelimiter()).append(s.closingDelimiter());
        return sb.toString();
    }

}
