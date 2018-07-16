/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
 * Aggregate object for a host and port.
 *
 * @author Tim Boudreau
 */
public class HostAndPort {
    
    public final Host host;
    public final Port port;

    public HostAndPort(Host host, Port port) {
        Checks.notNull("host", host);
        Checks.notNull("port", port);
        this.host = host;
        this.port = port;
    }
    
    public boolean isValid() {
        return host.isValid() && port.isValid();
    }
    
    public String toString() {
        return host.toString() + ":" + port.toString();
    }
    
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof HostAndPort) {
            HostAndPort other = (HostAndPort) o;
            return port.equals(other.port) && host.equals(other.host);
        } else {
            return false;
        }
    }
    
    public int hashCode() {
        return port.hashCode() + (53 * host.hashCode());
    }
    
    public String host() {
        return host.toString();
    }
    
    public int port() {
        return port.intValue();
    }
}
