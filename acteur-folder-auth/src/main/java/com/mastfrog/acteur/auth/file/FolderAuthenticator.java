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

import com.google.inject.Inject;
import com.mastfrog.acteur.auth.Authenticator;
import com.mastfrog.acteur.util.BasicCredentials;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
final class FolderAuthenticator implements Authenticator {

    private final Filer filer;

    @Inject
     FolderAuthenticator(Filer filer) {
        this.filer = filer;
    }
    
    @Override
    public String toString() {
        return super.toString() + "{" + filer + "}";
    }

    @Override
    public Object[] authenticate(String realm, BasicCredentials credentials) throws IOException {
        FolderUser u = new FolderUser(credentials.username, filer);
        if (u.isValid()) {
            if (u.checkPassword(credentials.password)) {
                Role role = getRole(u);
                return new Object[] { u, role, new LoginInfo<>(u, role) };
            }
        }
        return null;
    }

    protected Role getRole(FolderUser u) {
        return new RoleImpl("User");
    }


    private static final class RoleImpl implements Role {

        private final String name;

        RoleImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RoleImpl other = (RoleImpl) obj;
            if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 53 * hash + (this.name != null ? this.name.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
