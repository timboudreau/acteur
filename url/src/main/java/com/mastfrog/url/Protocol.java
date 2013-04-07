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

/**
 * The protocol portion of a URL (e.g. "http").  See the enum
 * <a href="Protocols.html"><code>Protocols</code></a> for standard
 * protocols.
 * <p/>
 * Note:  Any external implementation of this class should implement equals and
 * hash code test equality of getName() without case-sensitivity.
 * <p/>
 *
 * @author Tim Boudreau
 */
public interface Protocol extends URLComponent {
    public String getName();
    public Port getDefaultPort();
    public boolean isSecure();
    public boolean match(String protocol);
    public boolean isKnownProtocol();
    public boolean isNetworkProtocol();
    public boolean isSecureVersionOf (Protocol other);
    public boolean isInsecureVersionOf (Protocol other);
    public static Protocol INVALID = new Protocol() {
        private static final long serialVersionUID = 1L;
            
        public String getName() {
            return "invalid_protocol";
        }

        public Port getDefaultPort() {
            return new Port(0);
        }

        public boolean isSecure() {
            return false;
        }

        public boolean match(String protocol) {
            return getName().equals(protocol);
        }

        public boolean isKnownProtocol() {
            return false;
        }

        public boolean isValid() {
            return false;
        }

        public String getComponentName() {
            return Protocols.HTTP.getComponentName();
        }

        public void appendTo(StringBuilder sb) {
            sb.append ("invalid");
        }

        public boolean isNetworkProtocol() {
            return true;
        }

        public boolean isSecureVersionOf(Protocol other) {
            return false;
        }

        public boolean isInsecureVersionOf(Protocol other) {
            return false;
        }
    };
}
