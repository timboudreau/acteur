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
package com.mastfrog.acteur.resources;

import com.google.inject.ImplementedBy;
import com.mastfrog.mime.MimeType;
import com.mastfrog.url.Path;
import java.time.ZonedDateTime;

/**
 * Determines the value of the Expires header for various files
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultExpiresPolicy.class)
public interface ExpiresPolicy {
    /**
     * Determine the latest date when markup should be cached for
     * the passed path - used in constructing cache control and
     * expires headers.
     * 
     * @param mimeType The mime type being used
     * @param path The url path
     * @return A DateTime, or null if none should be used
     */
    public ZonedDateTime get(MimeType mimeType, Path path);
}
