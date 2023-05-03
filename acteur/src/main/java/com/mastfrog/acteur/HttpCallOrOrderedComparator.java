/*
 * The MIT License
 *
 * Copyright 2018 tim.
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

import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.giulius.Ordered;
import java.util.Comparator;

/**
 * Compares objects or classes based on either the order parameter in an
 * HttpCall annotation, or the Ordered annotation, using 0 if neither present.
 *
 * @author Tim Boudreau
 */
class HttpCallOrOrderedComparator implements Comparator<Object> {

    static final HttpCallOrOrderedComparator INSTANCE = new HttpCallOrOrderedComparator();

    private int intFor(Class<?> type) {
        HttpCall hc = type.getAnnotation(HttpCall.class);
        if (hc != null) {
            return hc.order();
        }
        Ordered ord = type.getAnnotation(Ordered.class);
        if (ord != null) {
            return ord.value();
        }
        return 0;
    }

    @Override
    public int compare(Object a, Object b) {
        int ai, bi;
        Class<?> ca, cb;
        HttpCall ac, bc;
        if (a instanceof Class<?>) {
            ca = (Class<?>) a;
        } else {
            ca = a.getClass();
        }
        if (b instanceof Class<?>) {
            cb = (Class<?>) b;

        } else {
            cb = b.getClass();
        }
        ai = intFor(ca);
        bi = intFor(cb);
        return Integer.compare(ai, bi);
    }
}
