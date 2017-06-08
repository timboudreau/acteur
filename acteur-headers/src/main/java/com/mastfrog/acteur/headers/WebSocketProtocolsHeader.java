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

import com.mastfrog.util.Strings;
import com.mastfrog.util.strings.AppendableCharSequence;
import io.netty.handler.codec.http.HttpHeaderNames;

/**
 *
 * @author Tim Boudreau
 */
final class WebSocketProtocolsHeader extends AbstractHeader<CharSequence[]> {

    private static final CharSequence comma = Strings.singleChar(',');

    WebSocketProtocolsHeader() {
        super(CharSequence[].class, HttpHeaderNames.WEBSOCKET_PROTOCOL);
    }

    @Override
    public CharSequence toCharSequence(CharSequence[] value) {
        return Strings.join(',', value);
    }

    @Override
    public CharSequence[] toValue(CharSequence value) {
        CharSequence[] result = Strings.split(',', value);
        for (int i = 0; i < result.length; i++) {
            result[i] = Strings.trim(result[i]);
        }
        return result;
    }

}
