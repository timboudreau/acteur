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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
public class FolderUser extends User {
    public static final String USERS_FOLDER = Filer.USERS_FOLDER;

    private final String name;
    private final Filer filer;

    FolderUser(String name, Filer filer) {
        this.name = name;
        this.filer = filer;
    }
    
    public File getRoot() {
        return filer.getRoot();
    }

    @Override
    public String getName() {
        return name;
    }

    public File getFile(String path, boolean isFile, boolean create) throws IOException {
        return filer.getFile(this, path, isFile, create);
    }

    @Override
    public boolean checkPassword(String password) throws IOException {
        return filer.getPasswordIO().checkPassword(password, filer.getUserPasswordsFile(this, false));
    }

    @Override
    public Properties getProperties() throws IOException {
        Properties p = filer.getUserProperties(this);
        if (p == null) {
            p = new Properties();
        }
        return p;
    }

    @Override
    public boolean isValid() {
        try {
            return filer.isValidUser(this);
        } catch (IOException ex) {
            Logger.getLogger(FolderUser.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public String toString() {
        try {
            return super.toString() + "=" + filer.getUserFolder(this, false);
        } catch (IOException ex) {
            Logger.getLogger(FolderUser.class.getName()).log(Level.SEVERE, null, ex);
            return super.toString();
        }
    }

    @Override
    public void setPassword(String oldPassword, String password) throws IOException {
        if (!oldPassword.equals(password)) {
            if (checkPassword(oldPassword)) {
                if (password.length() < 3) {
                    throw new PasswordTooWeakException();
                }
                filer.getPasswordIO().writePassword(password, filer.getUserPasswordsFile(this, true));
            } else {
                throw new IncorrectPasswordException(oldPassword);
            }
        }
    }

    void setDisplayName(String realName) throws IOException {
        Properties props = getProperties();
        props.setProperty("displayName", realName);
        realNameForPath.put(filer.getRoot().getPath(), realName);
        filer.saveUserProperties(this, props);
    }

    @Override
    public String getDisplayName() {
        String result = realNameForPath.get(filer.getRoot().getPath());
        if (result == null) {
            try {
                result = getProperties().getProperty("displayName");
            } catch (IOException ex) {
                Logger.getLogger(FolderUser.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (result == null) {
            result = super.getDisplayName();
        }
        return result;
    }
    
    private static final Map<String, String> realNameForPath = new HashMap<>();
}
