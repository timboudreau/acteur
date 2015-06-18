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

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

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

    public static com.mastfrog.acteur.util.HttpMethod get(HttpRequest req) {
        io.netty.handler.codec.http.HttpMethod m = req.method();
        AsciiString methodName = m.name().toUpperCase();
        for (Method mm : values()) {
            if (methodName.contentEquals(mm.name())) {
                return mm;
            }
        }
        return new UnknownMethodImpl(methodName);
    }
    
    public static Method valueFor(AsciiString string) {
        string = string.toUpperCase();
        for (Method mm : values()) {
            if (string.contentEquals(mm.name())) {
                return mm;
            }
        }
        return null;
    }

    static class UnknownMethodImpl implements com.mastfrog.acteur.util.HttpMethod {

        private final AsciiString name;

        public UnknownMethodImpl(AsciiString name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name.toString();
        }

    }
}
