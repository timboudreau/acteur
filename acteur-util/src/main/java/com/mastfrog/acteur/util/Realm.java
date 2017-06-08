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
package com.mastfrog.acteur.util;

import com.mastfrog.util.Checks;
import com.mastfrog.util.Strings;
import static com.mastfrog.util.Strings.charSequenceHashCode;
import static com.mastfrog.util.Strings.charSequencesEqual;
import javax.inject.Inject;
import javax.inject.Named;

/**
 *
 * @author Tim Boudreau
 */
public class Realm implements Comparable<Realm> {

    private final CharSequence name;

    @Inject
    protected Realm(@Named("realm") String name) {
        Checks.notNull("name", name);
        this.name = name;
    }
    
    protected Realm(@Named("realm") CharSequence name) {
        Checks.notNull("name", name);
        this.name = name;
    }

    @Override
    public String toString() {
        return name.toString();
    }

    public CharSequence value() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Realm && charSequencesEqual(((Realm) o).name, name);
    }

    @Override
    public int hashCode() {
        return charSequenceHashCode(name);
    }

    public int compareTo(Realm o) {
        return o == this ? 0 : Strings.compareCharSequences(name, o.name, false);
    }

    public static Realm createSimple(CharSequence name) {
        return new Realm(name);
    }
}
