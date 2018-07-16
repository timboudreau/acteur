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

import com.mastfrog.util.preconditions.Checks;
import org.netbeans.validation.localization.LocalizationSupport;

/**
 * One element of a URL's path.
 *
 * @author Tim Boudreau
 */
public final class PathElement implements URLComponent {

    private static final long serialVersionUID = 1L;
    private final String element;
    private final boolean trailingSlash;
    private final boolean noEncode;

    public PathElement(String element) {
        this(element, false);
    }

    public PathElement(String element, boolean trailingSlash) {
        this(element, trailingSlash, false);
    }

    public PathElement(String element, boolean trailingSlash, boolean noEncode) {
        Checks.notNull("element", element);
        this.element = element;
        this.trailingSlash = trailingSlash;
        this.noEncode = noEncode;
    }

    String rawText() {
        return element;
    }

    PathElement toTrailingSlashElement() {
        return trailingSlash ? this : new PathElement(element, true, noEncode);
    }

    PathElement toNonTrailingSlashElement() {
        return trailingSlash ? new PathElement(element, false) : this;
    }

    @Override
    public boolean isValid() {
        return element.indexOf('/') < 0 && URLBuilder.isEncodableInLatin1(element);
    }

    @Override
    public String toString() {
        return noEncode ? element : URLBuilder.escape(element, '/', '+', ':', '?', '=', '_');
    }

    @Override
    public String getComponentName() {
        return LocalizationSupport.getMessage(PathElement.class, "path_element");
    }

    @Override
    public void appendTo(StringBuilder sb) {
        appendTo(sb, false);
    }

    public void appendTo(StringBuilder sb, boolean includeTrailingSlashIfPresent) {
        if (noEncode) {
            sb.append(element);
        } else {
            URLBuilder.append(sb, element, '/', '+', ':', '?', '=', '-');
        }
        if (trailingSlash && includeTrailingSlashIfPresent) {
            sb.append('/');
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
        final PathElement other = (PathElement) obj;
        if ((this.element == null) ? (other.element != null) : !this.element.equals(other.element)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + (this.element != null ? this.element.hashCode() : 0);
        return hash;
    }

    public boolean isProbableFileReference() {
        return !trailingSlash && !"..".equals(element)
                && !".".equals(element) && element.indexOf('.') > 0;
    }

    public String extension() {
        int ix = element.lastIndexOf('.');
        if (ix < 0 || ix == element.length() - 1) {
            return null;
        }
        return element.substring(ix + 1);
    }

    public boolean extensionEquals(String ext) {
        int last = element.length() - 1;
        if (element.isEmpty() || element.charAt(last) == '/') {
            return false;
        }
        if (ext.length() > last + 2) {
            return false;
        }
        int pos = last;
        for (int i = 0; i < ext.length(); i++) {
            int extLoc = (ext.length() - 1) - i;
            char test = ext.charAt(extLoc);
            char got = element.charAt(last - i);
            if (test != got) {
                return false;
            }
            pos--;
        }
        if (pos == -1 || element.charAt(pos) != '.') {
            return false;
        }
        return true;
    }
}
