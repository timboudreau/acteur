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
import org.netbeans.validation.localization.LocalizationSupport;

/**
 * Enum of standard URL protocols.
 *
 * @author Tim Boudreau
 */
public enum Protocols implements Protocol {
    HTTP,
    HTTPS,
    FTP,
    FILE,
    WS,
    WSS
    ;

    @Override
    public String getName() {
        return name().toLowerCase();
    }

    @Override
    public Port getDefaultPort() {
        switch(this) {
            case HTTP :
            case WS : 
                return new Port(80);
            case WSS :
            case HTTPS :
                return new Port(443);
            case FTP :
                return new Port(21);
            default :
                return null;
        }
    }

    @Override
    public boolean isSecure() {
        return this == HTTPS;
    }

    public boolean match(Protocol protocol) {
        return protocol != null && protocol.getName().toLowerCase().equals(getName());
    }

    @Override
    public boolean match(String protocol) {
        Checks.notNull("protocol", protocol);
        return name().compareToIgnoreCase(protocol) == 0;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public static Protocol forName (String name) {
        for (Protocols p : values()) {
            if (p.match(name)) {
                return p;
            }
        }
        return name == null ? null : new GenericProtocol(name);
    }

    @Override
    public boolean isKnownProtocol() {
        return true;
    }

    @Override
    public String getComponentName() {
        return LocalizationSupport.getMessage(Protocols.class, "protocol");
    }

    @Override
    public void appendTo(StringBuilder sb) {
        sb.append (getName());
    }

    @Override
    public boolean isNetworkProtocol() {
        return this != FILE;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    @Override
    public boolean isSecureVersionOf(Protocol other) {
        return this == HTTPS && "http".equals(other.getName().toLowerCase());
    }

    @Override
    public boolean isInsecureVersionOf(Protocol other) {
        return this == HTTP && "https".equals(other.getName().toLowerCase());
    }

    private static final class GenericProtocol implements Protocol {
        private static final long serialVersionUID = 1L;
        private final String name;

        GenericProtocol (String name) {
            this.name = name;
        }

        public String getName() {
            return name.toLowerCase();
        }

        public Port getDefaultPort() {
            return new Port(-1);
        }

        public boolean isSecure() {
            return false;
        }

        public boolean match(String protocol) {
            return name.equalsIgnoreCase(protocol);
        }

        public boolean isKnownProtocol() {
            return false;
        }
        
        public String toString() {
            return name;
        }

        public boolean equals (Object o) {
            return o instanceof Protocol ? ((Protocol) o).getName().equals(getName()) : false;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 37 * hash + (this.name != null ? this.name.hashCode() : 0);
            return hash;
        }

        public boolean isValid() {
            char[] chars = name.toCharArray();
            for (int i=0; i < chars.length; i++) {
                char c = chars[i];
                if (i == 0 && !URLBuilder.isLetter(c)) {
                    return false;
                }
                if (!URLBuilder.isLetter(c) && !URLBuilder.isNumber(c) && c != '+' && c != '-') {
                    return false;
                }
            }
            return true;
        }

        public String getComponentName() {
            return "protocol";
        }

        public void appendTo(StringBuilder sb) {
            sb.append(getName());
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
    }
}
