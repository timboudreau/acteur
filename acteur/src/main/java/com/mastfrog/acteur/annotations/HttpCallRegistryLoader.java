/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur.annotations;

import com.google.inject.Module;
import com.mastfrog.acteur.Page;
import com.mastfrog.parameters.gen.Origin;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import static java.util.Collections.emptySet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
public final class HttpCallRegistryLoader implements Iterable<Class<? extends Page>> {

    private final Class<?> type;
    private final List<Entry> entries = new LinkedList<>();

    public HttpCallRegistryLoader(Class<?> type) {
        // Get the type so we have the classloader
        this.type = type;
    }

    public Set<Class<?>> implicitBindings() {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Entry e : entries()) {
            types.addAll(e.bindings);
        }
        // Load META-INF/http/numble.list - technically we should have
        // pluggable loaders in ServiceLoader for this, but this will do for now
        ClassLoader cl = type.getClassLoader();
        if (cl != null) { // graal
            try {
                Set<String> seenLines = new HashSet<>();
                Enumeration<URL> numbleLists = cl.getResources(Origin.META_INF_PATH);
                if (numbleLists != null) {
                    for (URL url : CollectionUtils.toIterable(numbleLists)) {
                        try (final InputStream in = url.openStream()) {
                            String[] lines = Streams.readString(in, "UTF-8").split("\n");
                            for (String line : lines) {
                                // CRLF issues if build was done on Windows
                                // gives us class names ending in a \r
                                line = line.trim();
                                if (line.isEmpty() || line.startsWith("#") || seenLines.contains(line)) {
                                    continue;
                                }
                                // If A and B depend on C, and D depends on both, D can
                                // wind up with duplicates - harmless but has overhead, so
                                // nip that in the bud here
                                seenLines.add(line);
                                try {
                                    types.add(cl.loadClass(line));
                                } catch (Throwable t) {
                                    // Graal
                                    t.printStackTrace();
                                    types.add(Class.forName(line));
                                }
                            }
                        } catch (ClassNotFoundException ex) {
                            return Exceptions.chuck(ex);
                        }
                    }
                }
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    public Set<Class<? extends Module>> modules() throws IOException, ClassNotFoundException {
        Set<Class<? extends Module>> types = new HashSet();
        ClassLoader cl = type.getClassLoader();
        if (cl == null) {
            return emptySet(); // graal
        }
        Enumeration<URL> eurls = cl.getResources(GuiceModule.META_INF_PATH);
        if (eurls == null) {
            return emptySet();
        }
        for (URL url : CollectionUtils.toIterable(eurls)) {
            try (final InputStream in = url.openStream()) {
                // Split into lines
                String[] lines = Streams.readString(in, "UTF-8").split("\n");
                for (String line : lines) {
                    line = line.trim();
                    // Skip comments and blanks - these could be
                    // generated manually
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    Class<?> moduleType = Class.forName(line);
                    if (!Module.class.isAssignableFrom(moduleType)) {
                        throw new ClassCastException("Not a subclass of " + Module.class.getName() + ": " + line);
                    }
                    types.add((Class<? extends Module>) moduleType);
                }
            }
        }
        return types;
    }

    private synchronized List<Entry> entries() {
        if (entries.isEmpty()) {
            try {
                ClassLoader cl = type.getClassLoader();
                // Lines are in the form fqn:order
                Pattern classAndOrder = Pattern.compile("(.*?):(-?\\d+)");
                Pattern classAndOrderWithImplicitBindings = Pattern.compile("(.*?):(-?\\d+)\\{(.*)\\}$");
                // We are keeping track of both classpath order and ordering
                // attributes!
                int ix = 0;
                // Look up all META-INF/http/pages.list files on the classpath
                if (cl == null) { // graal?
                    return Collections.emptyList();
                }
                Enumeration<URL> eurls = cl.getResources(HttpCall.META_INF_PATH);
                if (eurls == null) { // graal
                    return Collections.emptyList();
                }
                for (URL url : CollectionUtils.toIterable(eurls)) {
                    try (final InputStream in = url.openStream()) {
                        // Split into lines
                        String[] lines = Streams.readString(in, "UTF-8").split("\n");
                        for (String line : lines) {
                            // Skip comments and blanks - these could be
                            // generated manually
                            if (line.isEmpty() || line.charAt(0) == '#') {
                                continue;
                            }
                            Matcher m = classAndOrderWithImplicitBindings.matcher(line);
                            if (m.find()) {
                                String className = m.group(1);
                                int order = Integer.parseInt(m.group(2));
                                String bindings = m.group(3);
                                entries.add(new Entry(ix, className, order, url, bindings));
                            } else {
                                m = classAndOrder.matcher(line);
                                if (m.find()) {
                                    String className = m.group(1);
                                    int order = Integer.parseInt(m.group(2));
                                    entries.add(new Entry(ix, className, order, url));
                                }
                            }
                        }
                        ix++;
                    }
                }
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
            // Sort the entries
            Collections.sort(entries);
        }
        return entries;
    }

    @Override
    public Iterator<Class<? extends Page>> iterator() {
        // Convert its iterator to an Iterator<Class<? extends Page>>
        return CollectionUtils.convertedIterator(new EntryConverter(), entries().iterator());
    }

    private static class Entry implements Comparable<Entry> {

        private final int order;
        private final int classpathOrder;
        private final Class<? extends Page> type;
        private final Set<Class<?>> bindings = new LinkedHashSet<>();

        @SuppressWarnings(value = "unchecked")
        Entry(int classpathOrder, String className, int order, URL url) throws ClassNotFoundException {
            this.order = order;
            this.classpathOrder = classpathOrder;
            // Load the class (and fail early)
            Class<?> type = Class.forName(className);
            if (!Page.class.isAssignableFrom(type)) {
                throw new ClassCastException(type.getName() + " is not a subtype of " + Page.class.getName() + " in " + url);
            }
            this.type = (Class<? extends Page>) type;
        }

        Entry(int classpathOrder, String className, int order, URL url, String bindings) throws ClassNotFoundException {
            this(classpathOrder, className, order, url);
            for (String type : bindings.split(",")) {
                type = type.trim();
                this.bindings.add(Class.forName(type));
            }
        }

        @Override
        public int compareTo(Entry o) {
            Integer mine = classpathOrder;
            Integer theirs = o.classpathOrder;
            if (mine.equals(theirs)) {
                mine = order;
                theirs = o.order;
            }
            return mine.compareTo(theirs);
        }

        public String toString() {
            return type.getSimpleName();
        }
    }

    static class EntryConverter implements Converter<Class<? extends Page>, Entry> {

        @Override
        public Class<? extends Page> convert(Entry r) {
            return r.type;
        }

        @Override
        public Entry unconvert(Class<? extends Page> t) {
            throw new UnsupportedOperationException();
        }
    }
}
