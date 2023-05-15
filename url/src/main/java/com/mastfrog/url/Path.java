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

import com.mastfrog.util.builder.AbstractBuilder;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import static com.mastfrog.util.strings.Strings.charSequencesEqual;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.netbeans.validation.localization.LocalizationSupport;

/**
 * The path portion of a URL. In particular, this class deals with normalizing
 * relative (..) references, and leading and trailing slashes.
 *
 * @author Tim Boudreau
 */
public final class Path implements URLComponent, Iterable<PathElement> {

    private static final Path EMPTY = new Path();
    private static final long serialVersionUID = 1L;
    private final PathElement[] elements;
    private final boolean illegal;

    private Path() {
        this.elements = new PathElement[0];
        this.illegal = false;
    }

    public Path(PathElement... elements) {
        Checks.notNull("elements", elements);
        this.elements = new PathElement[elements.length];
        System.arraycopy(elements, 0, this.elements, 0, elements.length);
        illegal = normalizePath().illegal;
    }

    Path(NormalizeResult n) {
        Checks.notNull("n", n);
        illegal = n.illegal;
        this.elements = illegal ? n.original : n.elements.toArray(new PathElement[n.elements.size()]);
    }

    static final Pattern PATH_PATTERN = Pattern.compile(URLBuilder.PATH_ELEMENT_DELIMITER + "([$.]*?)");

    /**
     * Parse a path in the format <code>element1/element2/element3</code>
     *
     * @param path
     * @return
     */
    public static Path parse(String path) {
        return Path.parse(path, false);
    }

    public static Path parse(String path, boolean decode) {
        return parse((CharSequence) path, decode);
    }

    public static Path parse(CharSequence path, boolean decode) {
        int len = notNull("path", path).length();
        if (len == 0 || len == 1 && path.charAt(0) == '/') {
            return EMPTY;
        }
        //XXX handle relative paths
        List<PathElement> l = new ArrayList<>(12);
        StringBuilder sb = new StringBuilder();

        // http://foo.com/relative/path/../../stuff = http://foo.com/stuff
        // http://foo.com/./,/stuff = http://foo.com/stuff
        try {
            for (int i = 0; i < len; i++) {
                char c = path.charAt(i);
                switch (c) {
                    case '/':
                        if (i == 0) {
                            continue;
                        }
                        if (i == len - 1) {
                            if (decode) {
                                l.add(new PathElement(URLDecoder.decode(sb.toString(), "UTF-8"), true, decode));
                            } else {
                                l.add(new PathElement(sb.toString(), true));
                            }
                        } else {
                            if (decode) {
                                l.add(new PathElement(URLDecoder.decode(sb.toString(), "UTF-8"), false, decode));

                            } else {
                                l.add(new PathElement(sb.toString(), false));
                            }
                        }
                        sb.setLength(0);
                        break;
                    default:
                        sb.append(c);
                }
            }
            if (sb.length() > 0) {
                if (decode) {
                    l.add(new PathElement(URLDecoder.decode(sb.toString(), "UTF-8"), false, decode));

                } else {
                    l.add(new PathElement(sb.toString(), false));
                }
            }
            if (!l.isEmpty() && (len > 0 && path.charAt(len - 1) == '/')) {
                PathElement el = l.get(l.size() - 1);
                l.set(l.size() - 1, el.toTrailingSlashElement());
            }
        } catch (UnsupportedEncodingException e) {
            return Exceptions.chuck(e);
        }
        return new Path(l.toArray(new PathElement[l.size()]));
    }

    public Path toURLDecodedPath() {
        List<PathElement> el = new ArrayList<>(size());
        for (PathElement p : this) {
            try {
                el.add(new PathElement(URLDecoder.decode(p.toString(), "UTF-8"), true));
            } catch (UnsupportedEncodingException ex) {
                return Exceptions.chuck(ex);
            }
        }
        Path result = new Path(el.toArray(new PathElement[size()]));
        return result.equals(this) ? this : result;
    }

    /**
     * Determine if this path and the path string have the same content,
     * ignoring leading and trailing slashes in the input string.
     *
     * @param seq A string
     * @return Whether or not this path is a match for it
     */
    public boolean is(CharSequence seq) {
        return parse(seq.toString()).equals(this);
    }

    public Iterator<PathElement> iterator() {
        return CollectionUtils.toIterator(elements);
    }

    public PathElement lastElement() {
        return elements.length == 0 ? new PathElement("") : elements[elements.length - 1];
    }

    /**
     * Normalize a path, resolving <code>..</code> elements.
     *
     * @return A path
     */
    public Path normalize() {
        return new Path(normalizePath());
    }

    /**
     * Create a new path, replacing any path <i>elements</i> that exactly match
     * the passed string with new elements that match the replacement.
     *
     * @param old The string to look for
     * @param nue The string to replace it with
     * @return A path
     */
    public Path replace(String old, String nue) {
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

    public final Path elideEmptyElements() {
        boolean returnSelf = true;
        outer:
        for (int i = 0; i < this.elements.length; i++) {
            String raw = elements[i].rawText();
            if (raw.isEmpty()) {
                returnSelf = false;
                break;
            }
            switch (raw) {
                case ".":
                case "..":
                    returnSelf = false;
                    break outer;
            }
        }
        if (returnSelf) {
            return this;
        }
        List<PathElement> result = new ArrayList<>(size());
        boolean illegal = false;
        for (PathElement e : elements) {
            if (e.rawText().isEmpty()) {
                continue;
            }
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
        return new Path(result.toArray(new PathElement[result.size()]));
    }

    NormalizeResult normalizePath() {
        List<PathElement> result = new ArrayList<>(size());
        boolean illegal = false;
        for (PathElement e : elements) {
//            if (e.rawText().isEmpty()) {
//                continue;
//            }
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
        NormalizeResult res = new NormalizeResult(result, this.elements, illegal);
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

    public Path prepend(String part) {
        return merge(Path.parse(part), this);
    }

    public Path append(String part) {
        return merge(this, Path.parse(part));
    }

    /**
     * Merge an array of paths together
     *
     * @param paths An array of paths
     * @return A merged path which appends all elements of all passed paths
     */
    public static Path merge(Path... paths) {
        Checks.notEmptyOrNull("paths", paths);
        List<PathElement> l = new ArrayList<>(paths.length * 10);
        for (Path p : paths) {
            l.addAll(Arrays.asList(p.getElements()));
        }
        return new Path(l.toArray(new PathElement[l.size()]));
    }

    /**
     * Determine if this path is a path to a parent of the passed path. E.g.
     * <pre>
     * assert Path.parse ("com/foo").isParentOf(Path.parse("com/foo/bar")) == true;
     * </pre>
     *
     * @param path A path
     * @return whether or not this path is a parent of the passed path.
     */
    public boolean isParentOf(Path path) {
        Checks.notNull("path", path);
        return path.toString().startsWith(toString());
    }

    /**
     * Determine if this path is a path to a child of the passed path. E.g.
     * <pre>
     * assert Path.parse ("com/foo/bar").isChildOf(Path.parse("com/foo")) == true;
     * </pre>
     *
     * @param path a path
     * @return whether or not this Path is a child of the passed Path
     */
    public boolean isChildOf(Path path) {
        Checks.notNull("path", path);
        return path.isParentOf(this);
    }

    /**
     * Get the number of elements in this path.
     *
     * @return
     */
    public int size() {
        return elements.length;
    }

    /**
     * Get the individual elements of this path.
     *
     * @return An array of elements
     */
    public PathElement[] getElements() {
        PathElement[] result = new PathElement[elements.length];
        System.arraycopy(elements, 0, result, 0, elements.length);
        return result;
    }

    /**
     * Get a Path which does not include the first element of this path. E.g.
     * the child path of <code>com/foo/bar</code> is <code>foo/bar</code>.
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

    /**
     * Create a string version of this path, prepending a leading slash if
     * necessary.
     *
     * @return A string
     */
    public String toStringWithLeadingSlash() {
        StringBuilder result = new StringBuilder();
        appendTo(result);
        if (result.length() == 0) {
            return "/";
        } else if (result.charAt(0) != '/') {
            result.insert(0, '/');
        }
        return result.toString();
    }

    /**
     * Get the parent of this path. E.g. the parent of <code>com/foo/bar</code>
     * is <code>com/foo</code>.
     *
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

    /**
     * Returns true if the last path element is a textual match for the passed
     * CharSequence, considering case.
     *
     * @param s The string to match
     * @return True if it matches
     */
    public boolean lastElementMatches(CharSequence s) {
        return lastElementMatches(s, false);
    }

    /**
     * Returns true if the last path element is a textual match for the passed
     * CharSequence, ignoring case if the passed boolean flag is true.
     *
     * @param s The string to match
     * @param ignoreCase Whether or not to do a case-insensitive match
     * @return True if it matches
     */
    public boolean lastElementMatches(CharSequence s, boolean ignoreCase) {
        if (this.elements.length == 0) {
            return false;
        }
        return charSequencesEqual(s, elements[elements.length - 1].toString(), ignoreCase);
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
        return LocalizationSupport.getMessage(Path.class, "path");
    }

    /**
     * Determine if this path is probably a reference to a file (last element
     * contains a . character). May be used to decide whether or not to append a
     * '/' character.
     *
     * @return true if this is a probable file.
     */
    public boolean isProbableFileReference() {
        return elements.length == 0 ? false : elements[elements.length - 1].isProbableFileReference();
    }

    public void appendTo(StringBuilder sb) {
        Checks.notNull("sb", sb);
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                sb.append(URLBuilder.PATH_ELEMENT_DELIMITER);
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
        return Arrays.equals(this.elements, other.elements);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Arrays.deepHashCode(this.elements);
        return hash;
    }

    public URI toURI() {
        try {
            return new URI(toString());
        } catch (URISyntaxException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public URI toURIWithLeadingSlash() {
        try {
            return new URI(toStringWithLeadingSlash());
        } catch (URISyntaxException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public String[] toStringArray() {
        String[] result = new String[this.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = getElement(i).toString();
        }
        return result;
    }

    private static final class PathBuilder extends AbstractBuilder<PathElement, Path> {

        @Override
        public Path create() {
            PathElement[] elements = new PathElement[size()];
            elements = elements().toArray(elements);
            return new Path(elements);
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
