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
import com.mastfrog.util.Checks;
import com.mastfrog.util.Streams;
import java.io.*;

/**
 *
 * @author Tim Boudreau
 */
public class PlaintextPasswordIO implements PasswordIO {

    
    @Inject(optional=true)
    @Named("minimum.password.length")
    protected int minPasswordLength = 5;


    String readPassword(File file) throws IOException {
        if (file == null) {
            return null;
        }
        if (!file.exists()) {
            return null;
        }
        FileInputStream in = new FileInputStream(file);
        try {
            String result = Streams.readUTF8String(in);
            return result;
        } finally {
            in.close();
        }
    }
    
    @Override
    public void writePassword(String password, File file) throws IOException {
        Checks.notNull("file", file);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw new IOException("Could not create " + file);
            }
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            if (password.length() < minPasswordLength) {
                throw new PasswordTooWeakException("Password length " + password.length() + " is less than " + minPasswordLength + " characters");
            }
            out.write(password.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    @Override
    public boolean checkPassword(String password, File file) throws IOException {
        if (file == null) {
            return false;
        }
        return password != null && password.equals(readPassword(file));
    }
}
