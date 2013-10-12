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

import com.google.inject.ImplementedBy;
import com.mastfrog.url.Path;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.URL;

/**
 * Provider of paths for a web application, which can normalize paths and strip
 * out the base path for the web application, if any. The default implementation
 * looks for the two string constants defined on this class as
 * <code>&#064;Named</code> Guice strings.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultPathFactory.class)
public interface PathFactory {
    /**
     * The string "basepath" as a settings key / &#064;Named string
     */
    public static final String BASE_PATH_SETTINGS_KEY = "basepath";
    /**
     * The string "hostname" as a settings key / &#064;Named string
     */
    public static final String HOSTNAME_SETTINGS_KEY = "hostname";

    public static final String EXTERNAL_PORT = "external.port";

    public static final String EXTERNAL_SECURE_PORT = "external.secure.port";
    /**
     * Convert a URI from a request to a path, stripping out the basepath if
     * any.
     *
     * @param uri A URI
     * @return A path
     */
    public Path toPath(String uri);
    /**
     * Construct a URL suitable for external use, inserting the basepath and
     * host name if necessary.
     * @param path The path relative to the web application
     * @param secure Whether or not to construct an https URL
     * @return A url
     */
    public URL constructURL(Path path, boolean secure);
    /**
     * Construct a URL suitable for external use, inserting the basepath and
     * host name if necessary.
     * @param protocol The protocol for the new url
     * @param path The path relative to the web application
     * @return A url
     */    
    public URL constructURL(Protocol protocol, Path path);
    public URL constructURL(Protocol protocol, Path path, boolean secure);
    public URL constructURL(Protocol protocol, Path path, int port);

    /**
     * Prepend the base path if necessary
     * @param path A path
     * @return a path
     */
    public Path toExternalPath(Path path);
    /**
     * Prepend the base path if necessary
     * @param path A path
     * @return a path
     */
    public Path toExternalPath(String path);
}
