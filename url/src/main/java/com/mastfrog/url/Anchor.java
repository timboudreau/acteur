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
package com.mastfrog.url;

import com.mastfrog.util.preconditions.Checks;


/**
 *
 * @author tim
 */
public class Anchor implements URLComponent {
    private static final long serialVersionUID = 1L;
    private final String anchor;
    
    public Anchor (String anchor) {
        Checks.notNull("anchor", anchor);
        this.anchor = anchor;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(anchor.length() * 2);
        URLBuilder.append(sb, anchor);
        return sb.toString();
    }

    public boolean isValid() {
        return anchor.length() >= 0 && URLBuilder.isEncodableInLatin1(anchor);
    }

    public String getComponentName() {
        return "anchor";
    }

    public void appendTo(StringBuilder sb) {
        Checks.notNull("sb", sb);
        sb.append (URLBuilder.ANCHOR_DELIMITER);
        URLBuilder.append(sb, anchor);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Anchor other = (Anchor) obj;
        if ((this.anchor == null) ? (other.anchor != null) : !this.anchor.equals(other.anchor)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.anchor != null ? this.anchor.hashCode() : 0);
        return hash;
    }
}
