/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.acteur;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Utility for tracking down the origin of gratuitous printlns.
 *
 * @author Tim Boudreau
 */
public final class PrintlnDetector extends PrintStream {

    private final String[] patterns;
    private volatile boolean detected;
    private final boolean err;

    PrintlnDetector(boolean err, PrintStream delegate, String... patterns) {
        super(delegate);
        this.err = err;
        this.patterns = patterns;
    }

    static void attach(String... patterns) {
        attach(false, patterns);
    }

    static void attachErr(String... patterns) {
        attach(true, patterns);
    }

    private static void attach(boolean err, String... patterns) {
        PrintlnDetector det = new PrintlnDetector(err, err ? System.err : System.out, patterns);
        if (err) {
            System.setErr(det);
        } else {
            System.setOut(det);
        }
    }

    private boolean check(Object x) {
        if (detected) {
            return false;
        }
        String val = Objects.toString(x);
        for (String p : patterns) {
            if (val.contains(p)) {
                detected = true;
                return true;
            }
        }
        return false;
    }

    private void logit(Object o) {
        if (check(o)) {
            PrintStream real = ((PrintStream) super.out);
            new Exception("Matched").printStackTrace(real);
            if (err) {
                System.setErr(real);
            } else {
                System.setOut(real);
            }
        }
    }

    @Override
    public void println(Object x) {
        logit(x);
        super.println(x);
    }

    @Override
    public void println(String x) {
        logit(x);
        super.println(x);
    }

    @Override
    public void print(String s) {
        logit(s);
        super.print(s);
    }

}
