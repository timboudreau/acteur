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
import com.google.inject.name.Named;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.util.Checks;
import com.mastfrog.util.ConfigurationError;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
@Defaults(Filer.USERS_FOLDER + "=/tmp/Users")
final class Filer {

    private final File root;
    public static final String USERS_FOLDER = "usersFolder";
    private final PasswordIO passwordIO;

    Filer(File root, PasswordIO passwordIO) {
        if (!root.exists()) {
            if (!root.mkdirs()) {
                throw new ConfigurationError("Could not create " + root.getPath());
            }
        }
        this.root = root;
        this.passwordIO = passwordIO;
    }

    File getRoot() {
        return root;
    }

    @Override
    public String toString() {
        return "Filer over " + root;
    }

    PasswordIO getPasswordIO() {
        return passwordIO;
    }

    @Inject
     Filer(@Named(USERS_FOLDER) String root, PasswordIO passwordIO) {
        this(new File(root), passwordIO);
    }

    public File getUserFolder(User user, boolean create) throws IOException {
        File f = new File(root, user.getName());
        if (!f.exists() && create) {
            if (!f.mkdirs()) {
                throw new IOException("Could not create " + f.getPath());
            }
        }
        return f;
    }

    public FolderUser newUser(String name, String password) throws IOException {
        Checks.notNull("name", name);
        Checks.notNull("password", password);

        File f = new File(root, name);
        if (f.exists()) {
            throw new UserExistsException(name);
        }
        if (!f.mkdirs()) {
            throw new IOException("Could not create " + f.getPath());
        }
        FolderUser u = new FolderUser(name, this);
        getPasswordIO().writePassword(password, getUserPasswordsFile(u, true));
        return u;
    }

    public File getUserPasswordsFile(User user, boolean create) throws IOException {
        return getFile(user, "passwords.bin", true, create);
    }

    public File getUserPropertiesFile(User user, boolean create) throws IOException {
        return getFile(user, "info.properties", true, create);
    }

    public void saveUserProperties(User user, Properties props) throws IOException {
        File f = getUserPropertiesFile(user, true);
        FileOutputStream out = new FileOutputStream(f);
        try {
            props.store(out, new DateTime().toString());
        } finally {
            out.close();
        }
    }

    public Properties getUserProperties(User user) throws IOException {
        if (isValidUser(user)) {
            Properties p = new Properties();
            File f = getUserPropertiesFile(user, false);
            if (f != null && f.exists() && f.length() > 0) {
                FileInputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
            }
            return p;
        }
        return null;
    }

    public boolean isValidUser(User user) throws IOException {
        File userDir = getUserFolder(user, false);
        return userDir != null && userDir.exists();
    }

    File getFile(User user, String name, boolean isFile, boolean create) throws IOException {
        File f = getUserFolder(user, create);
        if (f != null) {
            File result = new File(f, name);
            if (create && !result.exists()) {
                if (isFile) {
                    result.createNewFile();
                } else {
                    if (!result.mkdirs()) {
                        throw new IOException("Could not create folder " + result.getPath());
                    }
                }
            }
            return result;
        }
        return null;
    }
}
