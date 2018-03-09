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
import org.netbeans.validation.localization.LocalizationSupport;

/**
 * One Label in a <a href="Host.html"><code>Host</code></a> - i.e. one component
 * of an IP address or host name such as <code>www.<b>example</b>.com</code> or
 * <code>192.<b>168</b>.2.32</code>.
 *
 * @author Tim Boudreau
 */
public final class Label implements URLComponent {

    private static final long serialVersionUID = 2L;
    private final String label;

    public Label(String domainPart) {
        Checks.notNull("domainPart", domainPart);
        this.label = domainPart;
    }

    public boolean isEmpty() {
        return label.isEmpty();
    }

    public int length() {
        return label.length();
    }

    @Override
    public String toString() {
        return label.toLowerCase();
    }

    public boolean isNumeric() {
        int len = label.length();
        boolean result = len > 0;
        if (result) {
            for (int i = 0; i < len && result; i++) {
                result &= URLBuilder.isNumber(label.charAt(i));
            }
        }
        return result;
    }
    
    /**
     * Get the literal label.
     * @return The label
     */
    public String getLabel() {
        return label;
    }

    public boolean isHex() {
        boolean result = true; // empty is considered hex 0
        int len = label.length();
        for (int i = 0; i < len && result; i++) {
            result &= isHexNumber(label.charAt(i));
        }
        return result;
    }

    private boolean isHexNumber(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    /**
     * Get an int value for this label, if it can be parsed, or -1 if not.
     * @param hex Whether to parse as hexadecimal
     * @return An int value
     */
    public int asInt(boolean hex) {
        try {
            if (hex) {
                if (label.isEmpty()) {
                    return 0;
                }
                return Integer.parseInt(label, 16);
            } else {
                return Integer.parseInt(label);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Get an int value for this label.
     *
     * @return An integer value or -1 if the label value is not a number
     * @deprecated Use asInt(hex) instead
     */
    @Deprecated
    public int asInt() {
        return asInt(isHex());
    }

    public boolean isValidIpComponent() {
        return isValidIpV4Component() || isValidIpV6Component();
    }

    public boolean isValidIpV6Component() {
        if (label.isEmpty()) {
            return true;
        }
        if (label.length() <= 4) {
            boolean result = true;
            for (char c : label.toCharArray()) {
                result = isHexNumber(c);
                if (!result) {
                    break;
                }
            }
            return result;
        }
        return false;
    }

    public boolean isValidIpV4Component() {
        boolean result = isNumeric();
        if (result) {
            int comp = asInt(false);
            return comp >= 0 && comp <= 255;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        if (label.length() == 0) {
            return true;
        }
        if (label.length() > 63) {
            return false;
        }
        boolean ipV6 = isValidIpV6Component();
        int val = asInt(ipV6);
        if (val != -1) {
            int limit = ipV6 ? 65536 : 255;
            if (val < 0) {
                return false;
            }
            return val <= limit;
        }
        char[] chars = label.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            switch (c) {
                case '-':
                    if (i == 0 || i == chars.length - 1) {
                        return false;
                    }
                    break;
                case '.':
                    return false;
            }
            boolean number = URLBuilder.isNumber(c);
            boolean letter = URLBuilder.isLetter(c);
            if (!number && !letter) {
                switch (c) {
                    case '-':
                        return true;

                }
                return false;
            }
        }
        return true;
    }

    @Override
    public String getComponentName() {
        return LocalizationSupport.getMessage(Label.class, "label");
    }

    @Override
    public void appendTo(StringBuilder sb) {
        sb.append(toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Label other = (Label) obj;
        if (this.label.equals(other.label)) {
            return true;
        }
        if ((this.isValidIpV4Component() && other.isValidIpV4Component()) || (this.isValidIpV6Component() && other.isValidIpV6Component())) {
            return asInt(true) == other.asInt(true);
        }
        //lower case normalization
        return toString().equals(other.toString());
    }

    private int cachedHashCode = -1;

    @Override
    public int hashCode() {
        if (cachedHashCode != -1) {
            return cachedHashCode;
        }
        if (isNumeric() || isHex()) {
            return cachedHashCode = asInt(true);
        }
        int hash = 3;
        hash = 59 * hash + (this.label != null ? this.toString().hashCode() : 0);
        return cachedHashCode = hash;
    }
}
