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

import com.mastfrog.util.Checks;
import com.mastfrog.util.Strings;
import org.netbeans.validation.localization.LocalizationSupport;

/**
 * One key/value pair in the query portion of a URL.
 *
 * @author Tim Boudreau
 */
public final class ParametersElement implements URLComponent, Comparable<ParametersElement> {
    private static final long serialVersionUID = 1L;
    private final String value;
    private final String key;
    public static final ParametersElement EMPTY = new ParametersElement ();
    public ParametersElement (String key, String value) {
        this.key = key;
        this.value = value;
        //One may be null but not both
        if (key == null && value == null) {
            Checks.notNull("key and value", key);
        }
    }

    private ParametersElement() {
        key = null;
        value = null;
    }

    public static ParametersElement parse (String queryString) {
        Checks.notNull("qeString", queryString);
        int ix = queryString.indexOf('=');
        if (ix == 0 && queryString.length() > 1) {
            return new ParametersElement (null, queryString);
        } else if (ix < 0) {
            return new ParametersElement (queryString, null);
        } else if (queryString.length() > 0) {
            String key = queryString.substring(0, ix);
            String val = queryString.substring(ix + 1);
            return new ParametersElement(key, val);
        } else {
            return new ParametersElement (queryString, null);
        }
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String decodedKey() {
        return Strings.urlDecode(key);
    }

    public String decodedValue() {
        return Strings.urlDecode(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendTo (sb);
        return sb.toString();
    }

    public boolean isValid() {
        boolean result = key != null;
        if (result) {
            result &= URLBuilder.isoEncoder().canEncode(key);
        }
        if (value != null) {
            result &= URLBuilder.isoEncoder().canEncode(value);
        }
        return result;
    }

    public String getComponentName() {
        return LocalizationSupport.getMessage(ParametersElement.class, "parameters_element");
    }

    public void appendTo(StringBuilder sb) {
        if (key != null) {
            URLBuilder.append(sb, key, '/', '+', ':', '?', '=');
        }
        if (value != null) {
            sb.append ('=');
        }
        if (value != null) {
            URLBuilder.append(sb, value, '/', '+', ':', '?', '=');
        }
    }

    public int compareTo(ParametersElement o) {
        return o.key.compareToIgnoreCase(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ParametersElement other = (ParametersElement) obj;
        if ((this.value == null) ? (other.value != null) : !this.value.equals(other.value)) {
            return false;
        }
        if ((this.key == null) ? (other.key != null) : !this.key.equals(other.key)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + (this.value != null ? this.value.hashCode() : 0);
        hash = 59 * hash + (this.key != null ? this.key.hashCode() : 0);
        return hash;
    }
}
