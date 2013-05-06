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

import com.mastfrog.util.Checks;
import org.openide.util.NbBundle;

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
        return noEncode ? element : URLBuilder.escape(element, '/', '+', ':', '?', '=');
    }

    @Override
    public String getComponentName() {
        return NbBundle.getMessage(PathElement.class, "path_element");
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
}
