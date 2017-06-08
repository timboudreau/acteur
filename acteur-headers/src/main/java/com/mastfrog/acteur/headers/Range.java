/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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

/**
 * A range, used in Range and Content-Range headers.
 *
 * @author Tim Boudreau
 */
public interface Range {

    /**
     * Get the starting value of the range, given the passed total
     * number of bytes that could be served;  returns -1 if the result
     * is an invalid range given that number of bytes.
     * @param max The total bytes available to serve
     * @return The start point
     */
    long start(long max);

    /**
     * Get the ending value of the range, given the passed total
     * number of bytes that could be served;  returns -1 if the result
     * is an invalid range given that number of bytes.
     * @param max The total bytes available to serve
     * @return The start point
     */
    long end(long max);
    
    /**
     * Convert this range to a bounded range suitable for use in a 
     * Content-Range header.
     * @param max The number of bytes that could be served
     * @return A bounded range
     */
    BoundedRange toBoundedRange(long max);
    
    default long length(long max) {
        return end(max) - start(max);
    }
}
