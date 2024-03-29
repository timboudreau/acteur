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

import com.mastfrog.util.preconditions.Checks;
import static com.mastfrog.util.preconditions.Checks.notNull;
import static io.netty.util.CharsetUtil.UTF_8;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class CharsetHeader extends AbstractHeader<Charset> {

    CharsetHeader(CharSequence name) {
        super(Charset.class, name);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String toString(Charset value) {
        return value.name().toLowerCase();
    }

    @Override
    public Charset toValue(CharSequence value) {
        try {
            return Charset.forName(notNull("value", value).toString());
        } catch (Exception ex) {
            Logger.getLogger(CharsetHeader.class.getName()).log(Level.INFO,
                    "invalid charset header '" + value + "' for " + name(), ex);
            return UTF_8;
        }
    }
}
