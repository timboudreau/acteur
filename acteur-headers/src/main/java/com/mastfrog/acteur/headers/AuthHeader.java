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

import com.mastfrog.acteur.util.Realm;
import com.mastfrog.util.preconditions.Checks;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
final class AuthHeader extends AbstractHeader<Realm> {

    private static final Pattern BASIC_PATTERN = Pattern.compile("realm=\"?(.*?)\"?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_PATTERN = Pattern.compile("bearer\\s+(.*)\\s*$", Pattern.CASE_INSENSITIVE);

    AuthHeader() {
        super(Realm.class, HttpHeaderNames.WWW_AUTHENTICATE);
    }

    @Override
    public CharSequence toCharSequence(Realm value) {
        Checks.notNull("value", value);
        return AsciiString.of("Basic realm=\"" + value.toString() + "\"");
    }

    @Override
    public Realm toValue(CharSequence value) {
        Checks.notNull("value", value);
        Matcher m = BASIC_PATTERN.matcher(value);
        if (m.find()) {
            return Realm.createSimple(m.group(1));
        } else {
            m = BEARER_PATTERN.matcher(value);
            if (m.find()) {
                return Realm.createSimple(m.group(1));
            }
        }
        return Realm.createSimple(value);
    }

}
