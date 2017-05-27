/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.marshallers;

import com.mastfrog.util.Checks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Registry of interpreters which can read and write objects of various types
 * into a byte-array or stream-like storage medium. Interpreters may be
 * registered to support various types. Interpreters are queried in such an
 * order that the most specific types are tried first, so, for example, if you
 * have interpreters for CharSequence, Object and String, they will be tried in
 * the order String, CharSequence, Object - so interpreters can be registered
 * for a specific type without worrying about the order they are added in.
 *
 * @author Tim Boudreau
 */
public class ContentMarshallers<R, MyType extends ContentMarshallers> {

    private final List<MarshallerEntry<?, R>> entries = new ArrayList<>(10);

    protected ContentMarshallers() {
    }

    @SuppressWarnings("unchecked")
    public final <T> MyType add(Class<T> type, Marshaller<T, R> interpreter) {
        Checks.notNull("type", type);
        Checks.notNull("interpreter", interpreter);
        for (Iterator<MarshallerEntry<?, R>> iter = entries.iterator(); iter.hasNext();) {
            if (type == iter.next().type) {
                iter.remove();
            }
        }
        entries.add(new MarshallerEntry<>(type, interpreter));
        Collections.sort(entries);
        return (MyType) this;
    }

    @SuppressWarnings("unchecked")
    public final <T> T read(Class<T> type, R buf, Object... hints) throws Exception {
        Checks.notNull("type", type);
        Checks.notNull("buf", buf);
        if (hints.length == 0) {
            hints = new Object[] { type };
        } else {
            Object[] newHints = new Object[hints.length + 1];
            System.arraycopy(hints, 0, newHints, 0, hints.length);
            newHints[newHints.length-1] = type;
            hints = newHints;
        }
        for (MarshallerEntry<?, R> e : entries) {
            if (e.match(type)) {
                return ((MarshallerEntry<T, R>) e).read(buf, hints);
            }
        }
        throw new IllegalArgumentException("No interpreter for " + type);
    }

    public final <T> void write(T obj, R into, Object... hints) throws Exception {
        Checks.notNull("obj", obj);
        Checks.notNull("into", into);
        for (MarshallerEntry<?, R> e : entries) {
            if (e.write(obj, into, hints)) {
                return;
            }
        }
        throw new IllegalArgumentException("No interpreter to write " + obj);
    }

    private static final class MarshallerEntry<T, R> implements Comparable<MarshallerEntry<?, R>> {

        private final Class<T> type;
        private final Marshaller<T, R> interpreter;

        MarshallerEntry(Class<T> type, Marshaller<T, R> interpreter) {
            this.type = type;
            this.interpreter = interpreter;
        }

        boolean match(Class<?> what) {
            return what == type || type.isAssignableFrom(what);
        }

        T read(R buf, Object[] hints) throws Exception {
            return interpreter.read(buf, hints);
        }

        boolean write(Object o, R buf, Object[] hints) throws Exception {
            if (type.isInstance(o)) {
                interpreter.write(type.cast(o), buf, hints);
                return true;
            }
            return false;
        }

        @Override
        public int compareTo(MarshallerEntry<?, R> o) {
            int result = 0;
            // Sort from most specific type to least specific type, so a handler
            // for, say, Object doesn't grab everything
            if (o.type != type) {
                boolean related = o.type.isAssignableFrom(type) || type.isAssignableFrom(o.type);
                if (related) {
                    if (Arrays.asList(o.type.getInterfaces()).contains(type)) {
                        return 1;
                    } else if (Arrays.asList(type.getInterfaces()).contains(o.type)) {
                        return -1;
                    }
                    int aDepth = depth(o.type);
                    int bDepth = depth(type);
                    result = aDepth == bDepth ? 0 : aDepth > bDepth ? 1 : -1;
                }
            }
            return result;
        }

        int depth(Class<?> type) {
            int result;
            boolean iface = type.isInterface();
            for (result = 0; type != null; type = type.getSuperclass(), result++);
            if (iface) {
                result++;
            }
            return result;
        }

        public String toString() {
            return type.getSimpleName();
        }
    }
}
