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
import org.openide.util.NbBundle;

/**
 * One Label in a <a href="Host.html"><code>Host</code></a> - i.e.
 * one component of an IP address or host name such as
 * <code>www.<b>example</b>.com</code> or <code>192.<b>168</b>.2.32</code>.
 *
 * @author Tim Boudreau
 */
public final class Label implements URLComponent {
    private static final long serialVersionUID = 1L;
    private final String label;
    public Label (String domainPart) {
        Checks.notNull("domainPart", domainPart);
        this.label = domainPart;
    }

    public int length() {
        return label.length();
    }

    @Override
    public String toString() {
        return label.toLowerCase();
    }

    public boolean isNumeric() {
        boolean result = label.length() > 0;
        if (result) {
            for (char c : label.toCharArray()) {
                result &= URLBuilder.isNumber(c);
            }
        }
        return result;
    }

    public int asInt() {
        int val = isNumeric() ? Integer.parseInt(label) : 0;
        return val;
    }

    public boolean isValidIpComponent() {
        boolean result = isNumeric();
        if (result) {
            int comp = asInt();
            return comp >= 0 && comp <= 255;
        }
        return false;
    }

    public boolean isValid() {
        if (label.length() == 0) {
            return false;
        }
        if (label.length() > 63) {
            return false;
        }
        int val = asInt();
        if (val != 0) {
            if (val < 0) {
                return false;
            }
            if (val > 255) {
                return false;
            }
        }
        char[] chars = label.toCharArray();
        for (int i=0; i < chars.length; i++) {
            char c = chars[i];
            switch (c) {
                case '-' :
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
                switch(c) {
                    case '-' : 
                        return true;
                        
                }
                return false;
            }
        }
        return true;
    }

    public String getComponentName() {
        return NbBundle.getMessage(Label.class, "label");
    }

    public void appendTo(StringBuilder sb) {
        sb.append (toString());
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
        if ((this.label == null) ? (other.label != null) : !this.toString().equals(other.toString())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + (this.label != null ? this.toString().hashCode() : 0);
        return hash;
    }
}
