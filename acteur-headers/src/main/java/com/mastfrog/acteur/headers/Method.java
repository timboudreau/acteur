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

import com.mastfrog.util.Checks;
import com.mastfrog.util.Strings;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AsciiString;

/**
 * Enum of standard HTTP methods
 *
 * @author Tim Boudreau
 */
public enum Method implements com.mastfrog.acteur.util.HttpMethod {

    GET, PUT, POST, OPTIONS, HEAD, DELETE, TRACE, CONNECT,
    // WEBDAV
    PROPFIND, PROPPATCH, MKCOL, COPY, MOVE, LOCK, UNLOCK,
    UNKNOWN;

    private final AsciiString stringValue;

    Method() {
        stringValue = AsciiString.of(name());
    }

    public static Method get(HttpRequest req) {
        HttpMethod m = req.method();
        return Method.valueOf(m.name().toUpperCase());
    }

    @Override
    public String toString() {
        return name();
    }

    public CharSequence toCharSequence() {
        return stringValue;
    }

    public static Method valueOf(CharSequence seq) {
        Checks.notNull("seq", seq);
        for (Method m : values()) {
            if (Strings.charSequencesEqual(seq, m.stringValue)) {
                return m;
            }
        }
        throw new IllegalArgumentException(seq.toString());
    }
}
