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

import com.mastfrog.acteur.util.Connection;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.strings.Strings;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

/**
 *
 * @author Tim Boudreau
 */
final class ConnectionHeader extends AbstractHeader<Connection> {

    private static final AsciiString close = AsciiString.of(Connection.close.toString());
    private static final AsciiString keep_alive = AsciiString.of(Connection.keep_alive.toString());

    ConnectionHeader() {
        super(Connection.class, HttpHeaderNames.CONNECTION);
    }

    @Override
    public CharSequence toCharSequence(Connection value) {
        Checks.notNull("value", value);
        switch (value) {
            case close:
                return ConnectionHeader.close;
            case keep_alive:
                return ConnectionHeader.keep_alive;
        }
        return value.toString();
    }

    @Override
    public Connection toValue(CharSequence value) {
        Checks.notNull("value", value);
        if (Strings.charSequencesEqual(close, value, true)) {
            return Connection.close;
        } else if (Strings.charSequencesEqual(keep_alive, value, true)) {
            return Connection.keep_alive;
        }
        return null;
    }

}
