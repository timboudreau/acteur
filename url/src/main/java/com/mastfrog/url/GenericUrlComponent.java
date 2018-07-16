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
 * Url component with unspecified type.
 *
 * @author Tim Boudreau
 */
final class GenericUrlComponent implements URLComponent {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final String value;
    private final boolean isAuthorityComponent;
    public GenericUrlComponent(String name, String value, boolean isAuthorityComponent) {
        Checks.notNull("name", name);
        Checks.notNull("value", value);
        this.name = name;
        this.value = value;
        this.isAuthorityComponent = isAuthorityComponent;
    }

    public boolean isValid() {
        boolean result = value != null;
        if (result) {
            for (char c : value.toCharArray()) {
                if (isAuthorityComponent) {
                    if (!URLBuilder.isLetter(c) && !URLBuilder.isNumber(c) && c != '.' && c != '-') {
                        return false;
                    }
                }
            }
        }
        return result;
    }

    public String getComponentName() {
        return name;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendTo(sb);
        return sb.toString();
    }

    public void appendTo(StringBuilder sb) {
        Checks.notNull("sb", sb);
        if (isAuthorityComponent) {
            sb.append(value);
        } else {
            URLBuilder.append(sb, value);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GenericUrlComponent other = (GenericUrlComponent) obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        if ((this.value == null) ? (other.value != null) : !this.value.equals(other.value)) {
            return false;
        }
        if (this.isAuthorityComponent != other.isAuthorityComponent) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 19 * hash + (this.value != null ? this.value.hashCode() : 0);
        hash = 19 * hash + (this.isAuthorityComponent ? 1 : 0);
        return hash;
    }

}
