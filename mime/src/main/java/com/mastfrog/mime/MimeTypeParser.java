/*
 * The MIT License
 *
 * Copyright 2022 Tim Boudreau.
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
package com.mastfrog.mime;

import static java.lang.Character.isWhitespace;

/**
 *
 * @author Tim Boudreau
 */
final class MimeTypeParser {

    static ParsedMimeType parse(CharSequence seq) {
        int start = 0;
        int slashLocation = -1;
        int plusLocation = -1;
        int firstSemiLocation = -1;
        int len = seq.length();
        boolean nonWhitespaceFound = false;
        loop:
        for (int i = 0; i < len; i++) {
            char c = seq.charAt(i);
            if (!nonWhitespaceFound && !isWhitespace(c)) {
                nonWhitespaceFound = true;
                start = i;
            }
            switch (c) {
                case '/':
                    if (slashLocation == -1) {
                        slashLocation = i - start;
                    }
                    break;
                case ';':
                    firstSemiLocation = i - start;
                    break loop;
                case '+':
                    if (plusLocation == -1) {
                        plusLocation = i - start;
                    }
                    break;
            }
        }
        if (!nonWhitespaceFound) {
            return null;
        }
        int end = len;
        while (end >= 0 && isWhitespace(seq.charAt(end - 1))) {
            end--;
        }
        if (start > 0 || end < len) {
            seq = seq.subSequence(start, end);
        }
        return new ParsedMimeType(seq, slashLocation, plusLocation, firstSemiLocation);
    }
}
