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

import com.mastfrog.acteur.header.entities.Connection;
import com.mastfrog.acteur.header.entities.ConnectionHeaderData;
import static com.mastfrog.util.preconditions.Checks.notNull;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

/**
 *
 * @author Tim Boudreau
 */
final class ConnectionHeader extends AbstractHeader<ConnectionHeaderData> {

    private static final AsciiString close = AsciiString.of(Connection.close.toString());
    private static final AsciiString keep_alive = AsciiString.of(Connection.keep_alive.toString());

    ConnectionHeader() {
        super(ConnectionHeaderData.class, HttpHeaderNames.CONNECTION);
    }

    @Override
    public CharSequence toCharSequence(ConnectionHeaderData value) {
        if (value instanceof Connection) {
            Connection cv = (Connection) value;
            switch (cv) {
                // Use statically allocated strings where they exist
                case close:
                    return close;
                case keep_alive:
                    return keep_alive;
            }
        }
        return notNull("value", value).toString();
    }

    @Override
    public ConnectionHeaderData toValue(CharSequence value) {
        return ConnectionHeaderData.fromString(notNull("value", value));
    }

}
