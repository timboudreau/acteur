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
package com.mastfrog.acteur.auth.file;

import java.io.IOException;
import java.util.Properties;

/**
 * User object that works with the simple authentication framework included.
 * There is no requirement to use it;  it just provides a general-purpose
 * convention.  The real heavy-lifting is in the implementations of 
 * {@link Authenticator} and {@link Users}.
 *
 * @author Tim Boudreau
 */
public abstract class User {

    public abstract String getName();
    
    public String getDisplayName() {
        return getName();
    }

    @Override
    public final boolean equals(Object o) {
        return o != null && o.getClass() == getClass() && ((User) o).getName() != null
                && ((User) o).getName().equals(getName());
    }

    @Override
    public final int hashCode() {
        String nm = getName();
        return nm == null ? 0 : nm.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getName() + "}";
    }

    public abstract boolean checkPassword(String password) throws IOException;

    public abstract Properties getProperties() throws IOException;

    public abstract boolean isValid();

    public abstract void setPassword(String oldPassword, String password) throws IOException;
}
