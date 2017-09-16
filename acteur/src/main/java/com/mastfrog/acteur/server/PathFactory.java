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
import com.mastfrog.acteur.HttpEvent;
import static com.mastfrog.acteur.server.ServerModule.*;
import com.mastfrog.url.Path;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.URL;
import com.mastfrog.util.Exceptions;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provider of paths for a web application, which can normalize paths and strip
 * out the base path for the web application, if any. Uses Settings to look up
 * default values for host name, port, etc. This enables an application to
 * generate fully qualified URLs to paths within it, without needing to know if
 * it is reverse-proxied, or if a path element is prepended to all URLs as part
 * of that proxying.
 *
 * @see ServerModule.SETTINGS_KEY_BASE_PATH
 * @see ServerModule.SETTINGS_KEY_URLS_HOST_NAME
 * @see ServerModule.SETTINGS_KEY_URLS_EXTERNAL_PORT
 * @see ServerModule.SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT
 * @see ServerModule.SETTINGS_KEY_GENERATE_SECURE_URLS
 * @see ServerModule.SETTINGS_KEY_GENERATE_URLS_WITH_INET_ADDRESS_GET_LOCALHOST
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultPathFactory.class)
public interface PathFactory {

    /**
     * The string "basepath" as a settings key / &#064;Named string
     *
     * @deprecated use @link{ServerModule.SETTINGS_KEY_BASE_PATH}
     */
    @Deprecated
    public static final String BASE_PATH_SETTINGS_KEY = SETTINGS_KEY_BASE_PATH;
    /**
     * @deprecated use @link{ServerModule.SETTINGS_KEY_URLS_HOST_NAME}
     */
    @Deprecated
    public static final String HOSTNAME_SETTINGS_KEY = SETTINGS_KEY_URLS_HOST_NAME;

    /**
     * @deprecated use @link{ServerModule.SETTINGS_KEY_URLS_EXTERNAL_PORT}
     */
    @Deprecated
    public static final String EXTERNAL_PORT = SETTINGS_KEY_URLS_EXTERNAL_PORT;

    /**
     * @deprecated use
     * @link{ServerModule.SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT}
     */
    @Deprecated
    public static final String EXTERNAL_SECURE_PORT = SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT;

    /**
     * @deprecated use @link{ServerModule.SETTINGS_KEY_GENERATE_SECURE_URLS}
     */
    @Deprecated
    public static final String SETTINGS_KEY_DEFAULT_SECURE_URLS = SETTINGS_KEY_GENERATE_SECURE_URLS;

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
     *
     * @param path The path relative to the web application
     * @param secure Whether or not to construct an https URL
     * @return A url
     */
    public URL constructURL(Path path, boolean secure);

    /**
     * Construct a URL suitable for external use, inserting the basepath and
     * host name if necessary.
     *
     * @param protocol The protocol for the new url
     * @param path The path relative to the web application
     * @return A url
     */
    public URL constructURL(Protocol protocol, Path path);

    public URL constructURL(Protocol protocol, Path path, boolean secure);

    public URL constructURL(Protocol protocol, Path path, int port);

    /**
     * Prepend the base path if necessary
     *
     * @param path A path
     * @return a path
     */
    public Path toExternalPath(Path path);

    /**
     * Prepend the base path if necessary
     *
     * @param path A path
     * @return a path
     */
    public Path toExternalPath(String path);

    /**
     * Generate a URL to this application, using the path and the system default
     * for whether to use http or https.
     *
     * @param path
     * @return
     */
    public URL constructURL(Path path);

    public int portForProtocol(Protocol protocol);

    /**
     * Generate a URL to this application, using the path and the system default
     * for whether to use http or https.
     *
     * @param path
     * @return
     */
    default URL constructURL(String path) {
        Path pth = Path.parse(path);
        if (!pth.isValid()) {
            throw new IllegalArgumentException("Invalid path '" + path + "'");
        }
        return constructURL(pth);
    }

    /**
     * Generate a URL to this application, using the path and the system default
     * for whether to use http or https.
     * <p>
     * This method will preserve any query string in the path, assuming the
     * following:
     * <ul>
     * <li>The query item follows the pattern ?param1=x&amp;param2=y</li>
     * <li>The same query item does not repeat</li>
     * </ul>
     * (<b>Hint</b>: if you need to do a redirect that doesn't follow this
     * pattern,
     * <code>add(Headers.LOCATION.stringHeader(), "http://weird.url?foo&foo&foo&bar=");</code>)
     *
     * @param path A url path
     * @param evt An event to use the host header from if available.
     * @throws IllegalArgumentException if the path is not a legal URL path
     * (character set, etc.) - convert using IDN first if you need to; or if the
     * port in the Host header is not a number.
     *
     *
     * @return A URL
     */
    URL constructURL(String path, HttpEvent evt);

    default URI constructURI(String path, HttpEvent evt) {
        try {
            return constructURL(path, evt).toURI();
        } catch (URISyntaxException ex) {
            return Exceptions.chuck(ex);
        }
    }

    default URI constructURI(String path) {
        try {
            return constructURL(path).toURI();
        } catch (URISyntaxException ex) {
            return Exceptions.chuck(ex);
        }
    }
}
