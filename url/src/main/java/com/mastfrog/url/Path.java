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
package com.mastfrog.url;

import com.mastfrog.util.AbstractBuilder;
import com.mastfrog.util.Checks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.openide.util.NbBundle;

/**
 * The path portion of a URL.
 *
 * @author Tim Boudreau
 */
public final class Path implements URLComponent {
    private static final long serialVersionUID = 1L;
    private final PathElement[] elements;
    private final boolean illegal;
    public Path (PathElement... elements) {
        Checks.notNull("elements", elements);
        this.elements = new PathElement[elements.length];
        System.arraycopy(elements, 0, this.elements, 0, elements.length);
        illegal = normalizePath().illegal;
    }

    Path (NormalizeResult n) {
        Checks.notNull("n", n);
        illegal = n.illegal;
        this.elements = illegal ? n.original : n.elements.toArray(new PathElement[n.elements.size()]);
    }

    static final Pattern PATH_PATTERN = Pattern.compile (URLBuilder.PATH_ELEMENT_DELIMITER + "([$.]*?)");

    /**
     * Parse a path in the format <code>element1/element2/element3</code>
     * @param path
     * @return
     */
    public static Path parse(String path) {
        Checks.notNull("path", path);
        //XXX handle relative paths
        List<PathElement> l = new ArrayList<>(12);
        char[] ch = path.toCharArray();
        StringBuilder sb = new StringBuilder();

        // http://foo.com/relative/path/../../stuff = http://foo.com/stuff
        // http://foo.com/./,/stuff = http://foo.com/stuff

        for (int i = 0; i < ch.length; i++) {
            char c = ch[i];
            switch (c) {
                case '/':
                    if (i == 0) {
                        continue;
                    }
                    if (i == ch.length - 1) {
                        l.add (new PathElement(sb.toString(), true));
                    } else {
                        l.add (new PathElement(sb.toString(), false));
                    }
                    sb.setLength(0);
                    break;
                default:
                    sb.append (c);
            }
        }
        if (sb.length() > 0) {
            l.add (new PathElement(sb.toString(), false));
        }
        if (!l.isEmpty() && path.endsWith("/")) {
            PathElement el = l.get(l.size() - 1);
            l.set (l.size() - 1, el.toTrailingSlashElement());
        }
        return new Path(l.toArray(new PathElement[l.size()]));
    }
    
    public Path normalize() {
        return new Path (normalizePath());
    }
    
    public Path replace (String old, String nue) {
        PathElement[] els = this.getElements();
        for (int i = 0; i < els.length; i++) {
            if (els[i].toNonTrailingSlashElement().toString().equals(old)) {
                boolean hadTrailingSlash = !els[i].toString().equals(els[i].toNonTrailingSlashElement().toString());
                PathElement nu = new PathElement(nue, hadTrailingSlash);
                els[i] = nu;
            }
        }
        return new Path(els);
    }

    NormalizeResult normalizePath() {
        List<PathElement> result = new ArrayList<PathElement>();
        boolean illegal = false;
        for (PathElement e : elements) {
            if (".".equals(e.rawText())) {
                continue;
            }
            if ("..".equals(e.rawText())) {
                if (result.size() > 0) {
                    result.remove(result.size() - 1);
                    if (result.size() > 0) {
                        result.set(result.size() - 1, result.get(result.size() - 1).toTrailingSlashElement());
                    }
                    continue;
                } else {
                    illegal = true;
                }
            }
            result.add(e);
        }
        NormalizeResult res = new NormalizeResult (result, this.elements, illegal);
        return res;
    }

    static final class NormalizeResult {
        private final List<PathElement> elements;
        private final boolean illegal;
        private final PathElement[] original;

        public NormalizeResult(List<PathElement> elements, PathElement[] original, boolean illegal) {
            Checks.notNull("original", original);
            Checks.notNull("elements", elements);
            this.original = new PathElement[original.length];
            System.arraycopy(original, 0, this.original, 0, original.length);
            this.elements = elements;
            this.illegal = illegal;
        }
    }
    
    public Path prepend (String part) {
        return merge (Path.parse(part), this);
    }
    
    public Path append (String part) {
        return merge (this, Path.parse(part));
    }

    /**
     * Merge an array of paths together
     * @param paths An array of paths
     * @return A merged path which appends all elements of all passed paths
     */
    public static Path merge(Path... paths) {
        Checks.notEmptyOrNull("paths", paths);
        List<PathElement> l = new ArrayList<PathElement>(paths.length * 10);
        for (Path p : paths) {
            l.addAll (Arrays.asList(p.getElements()));
        }
        return new Path (l.toArray(new PathElement[l.size()]));
    }

    /**
     * Determine if this path is a path to a parent of the passed path.  E.g.
     * <pre>
     * assert Path.parse ("com/foo").isParentOf(Path.parse("com/foo/bar")) == true;
     * </pre>
     *
     * @param path A path
     * @return whether or not this path is a parent of the passed path.
     */
    public boolean isParentOf (Path path) {
        Checks.notNull("path", path);
        return path.toString().startsWith(toString());
    }

    /**
     * Determine if this path is a path to a child of the passed path.  E.g.
     * <pre>
     * assert Path.parse ("com/foo/bar").isChildOf(Path.parse("com/foo")) == true;
     * </pre>
     *
     * @param path a path
     * @return whether or not this Path is a child of the passed Path
     */
    public boolean isChildOf (Path path) {
        Checks.notNull("path", path);
        return path.isParentOf(this);
    }

    /**
     * Get the number of elements in this path.
     * @return
     */
    public int size() {
        return elements.length;
    }

    /**
     * Get the individual elements of this path.
     * @return An array of elements
     */
    public PathElement[] getElements() {
        PathElement[] result = new PathElement[elements.length];
        System.arraycopy(elements, 0, result, 0, elements.length);
        return result;
    }

    /**
     * Get a Path which does not include the first element of this path.
     * E.g. the child path of <code>com/foo/bar</code> is <code>foo/bar</code>.
     *
     * @return A child path
     */
    public Path getChildPath() {
        if (elements.length > 1) {
            PathElement[] els = new PathElement[elements.length - 1];
            System.arraycopy(elements, 1, els, 0, els.length);
            return new Path(els);
        }
        return null;
    }
    
    public String toStringWithLeadingSlash() {
        StringBuilder result = new StringBuilder();
        appendTo(result);
        if (result.charAt(0) != '/') {
            result.insert(0, '/');
        }
        return result.toString();
    }

    /**
     * Get the parent of this path.  E.g. the parent of
     * <code>com/foo/bar</code> is <code>com/foo</code>.
     * @return A path
     */
    public Path getParentPath() {
        if (elements.length > 1) {
            PathElement[] els = new PathElement[elements.length - 1];
            System.arraycopy(elements, 0, els, 0, els.length);
            return new Path(els);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendTo(sb);
        return sb.toString();
    }

    public static AbstractBuilder<PathElement, Path> builder() {
        return new PathBuilder();
    }

    boolean isIllegal() {
        return illegal;
    }

    @Override
    public boolean isValid() {
        if (illegal) {
            return false;
        }
        for (PathElement e : elements) {
            if (!e.isValid()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getComponentName() {
        return NbBundle.getMessage(Path.class, "path");
    }

    /**
     * Determine if this path is probably a reference to a file (last element
     * contains a . character).  May be used to decide whether or not to append
     * a '/' character.
     * @return true if this is a probable file.
     */
    public boolean isProbableFileReference() {
        return elements.length == 0 ? false : elements[elements.length - 1].isProbableFileReference();
    }

    public void appendTo(StringBuilder sb) {
        Checks.notNull("sb", sb);
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                sb.append (URLBuilder.PATH_ELEMENT_DELIMITER);
            }
            elements[i].appendTo(sb, i == elements.length - 1);
        }
    }

    public PathElement getElement(int ix) {
        Checks.nonNegative("ix", ix);
        return elements[ix];
    }
    
    public PathElement getLastElement() {
        if (elements.length > 0) {
            PathElement last = elements[elements.length - 1];
            return last.toNonTrailingSlashElement();
        } else {
            return new PathElement("", true);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Path other = (Path) obj;
        if (!Arrays.equals(this.elements, other.elements)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Arrays.deepHashCode(this.elements);
        return hash;
    }

    private static final class PathBuilder extends AbstractBuilder<PathElement, Path> {

        @Override
        public Path create() {
            PathElement[] elements = new PathElement[size()];
            elements = elements().toArray(elements);
            return new Path (elements);
        }

        @Override
        protected PathElement createElement(String element) {
            Checks.notNull("string", element);
            return new PathElement(element, false);
        }

        protected PathElement createElementWithTrailingSlash(String element) {
            Checks.notNull("string", element);
            return new PathElement(element, true);
        }
    }
}
