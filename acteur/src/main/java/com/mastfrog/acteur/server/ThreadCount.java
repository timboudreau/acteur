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
package com.mastfrog.acteur.server;

import com.google.inject.Provider;
import com.mastfrog.settings.Settings;

/**
 * Wraps a number with a default value, a property name and an override value
 *
 * @author Tim Boudreau
 */
final class ThreadCount {

    private final Provider<Settings> set;
    private final int defaultValue;
    private final int overrideValue;
    private final String propertyName;

    ThreadCount(Provider<Settings> set, int defaultValue, int overrideValue, String propertyName) {
        this.set = set;
        this.defaultValue = defaultValue;
        this.overrideValue = overrideValue;
        this.propertyName = propertyName;
    }

    public int get() {
        if (overrideValue > 0) {
            return overrideValue;
        }
        return set.get().getInt(propertyName, defaultValue);
    }
}
