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

import org.openide.util.NbBundle;

/**
 * A TCP port.  Represents the optional port portion of a URL, such as
 * <code>https://www.getservo.com:<b>8443</b>/management/</code>
 *
 * @author Tim Boudreau
 */
public final class Port implements URLComponent {
    private static final long serialVersionUID = 1L;
    private String port;
    private boolean valid;
    public Port (int port) {
        this.port = Integer.toString(port);
        valid = true;
    }

    public Port (String port) {
        try {
            this.port = Integer.toString(Integer.parseInt(port));
            valid = true;
        } catch (NumberFormatException nfe) {
            this.port = port;
            valid = false;
        }
    }

    boolean isIllegalChars() {
        return !valid;
    }

    /**
     * Get the integer value of this port
     * @return
     */
    public int intValue() {
        return valid ? Integer.parseInt(port) : -1;
    }

    @Override
    public String toString() {
        return port;
    }

    /**
     * Determine if this is a valid port according to specification (i.e.
     * port is non-negative and value is between 1 and 65535).
     * @return
     */
    public boolean isValid() {
        if (valid) {
            int val = intValue();
            return val > 0 && val < 65536;
        }
        return false;
    }

    @Override
    public String getComponentName() {
        return NbBundle.getMessage(Port.class, "port");
    }

    @Override
    public void appendTo(StringBuilder sb) {
        sb.append (port);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Port && (((Port) obj).port == null ? port == null : ((Port) obj).port.equals(port));
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
