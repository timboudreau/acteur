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
package com.mastfrog.acteur;

import com.google.common.base.Optional;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.util.HttpMethod;
import com.mastfrog.url.Path;
import io.netty.handler.codec.http.HttpRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public interface HttpEvent extends Event<HttpRequest> {

    /**
     * Get the HTTP method for this request
     *
     * @return A method name
     * @since 2.0.0
     */
    HttpMethod method();

    /**
     * Get a single HTTP header
     *
     * @param nm The header name
     * @return The header
     * @since 2.0.0
     */
    String header(CharSequence nm);

    /**
     * Get a single request parameter
     *
     * @param param The parameter name
     * @return A parameter or null
     * @since 2.0.0
     */
    String urlParameter(String param);

    /**
     * Get the <i>logical</i> path of this request. The web application may be
     * "mounted" on some path (i.e. it is being proxied as part of a larger
     * site). This method will give you the path, sans the base portion of it.
     * So, if a request URL might be for
     * <code>http://example.com/myapp/foo/bar</code>, <code>path()</code> might
     * return <code>foo/bar</code>.
     *
     * @return
     * @since 2.0.0
     */
    Path path();

    /**
     * Get a header as an appropriate Java object, or null if it is not present.
     *
     * @see Headersfor a standard HTTP header types
     * @param <>> The return type
     * @param value A header definition/parser/encoder
     * @return An object or null if the header is missing or invalid
     */
    <T> T header(HeaderValueType<T> value);

    /**
     * Get all headers matching a type.
     *
     * @param <T> The type
     * @param headerType The header
     * @return A list of headers
     * @since 2.0.0
     */
    <T> List<T> headers(HeaderValueType<T> headerType);

    /**
     * Get all headers (pruning duplicate names) as a case-insensitive map of
     * CharSequence to header value.
     *
     * @return A map.
     * @since 2.0.0
     */
    Map<CharSequence, CharSequence> headersAsMap();

    /**
     * Gets the HTTP parameters as a flat map, ignoring duplicated keys. To be
     * technically correct, the same request parameter may be repeated any
     * number of times, so a Map &lt;String, List&lt;String&gt;&gt; would
     * losslessly represent parameters. In practice, this is usually a bit
     * pedantic and annoying, so this provides a convenient way to flatten it
     * into a map.
     *
     * @return A map
     * @since 2.0.0
     */
    Map<String, String> urlParametersAsMap();

    /**
     * Very primitive Java dynamic proxy magic: You write a Java interface with
     * methods that each return a primitive type, and whose name corresponds to
     * a URL parameter you expect to get.
     * <p/>
     * Calling this method will generate a dynamic proxy object of the interface
     * type you pass, which implements these methods to return objects, doing
     * the necessary conversions.
     * <p/>
     * Be aware that conversion can result in NumberFormatExceptions, etc.
     *
     * @param <T>
     * @param type
     * @return
     * @since 2.0.0
     */
    <T> T urlParametersAs(Class<T> type);

    /**
     * Get a URL query string parameter as an integer.
     *
     * @param name The parameter name
     * @return A parameter which may not be present
     * @since 2.0.0
     */
    Optional<Integer> intUrlParameter(String name);

    /**
     * Get a URL query string parameter as a long.
     *
     * @param name The parameter name
     * @return A long which may not be present
     * @since 2.0.0
     */
    Optional<Long> longUrlParameter(String name);

    /**
     * Get the request body as a string, in the encoding specified by
     * the request's content-type header, or UTF-8 if none.
     *
     * @return A string
     * @throws IOException If something goes wrong decoding the body
     * @since 2.0.0
     */
    String stringContent() throws IOException;

    /**
     * Determine if this request's connection header requests leaving the
     * connection open.  This method is used by the framework to decide what to
     * do at the end of sending a response.  It can also be used with the settings
     * value "neverKeepAlive" to disable any keep-alive behavior within the
     * application.  In particular, unlike Netty's utility methods, this method
     * defaults to <code>false</code> if no <code>Connection</code> header is
     * present.
     *
     * @return True if the connection should be kept alive after the conclusion
     * of responding to this request.
     * @since 2.0.0
     */
    boolean requestsConnectionStayOpen();
    
    /**
     * Determine if this event was over an encrypted connection.
     * @return True if it was encrypted
     * @since 2.0.0
     */
    boolean isSsl();

    default boolean isPreContent() {
        return false;
    }

    /**
     * Get the HTTP method for this request
     *
     * @return A method name
     * @deprecated use method()
     */
    @Deprecated
    default HttpMethod getMethod() {
        return method();
    }

    /**
     * Get a single HTTP header
     *
     * @param nm The header name
     * @return The header
     * @deprecated use header()
     */
    @Deprecated
    default String getHeader(CharSequence nm) {
        return header(nm);
    }

    /**
     * Get a single request parameter
     *
     * @param param The parameter name
     * @return A parameter or null
     * @deprecated Use urlParameter()
     */
    @Deprecated
    default String getParameter(String param) {
        return urlParameter(param);
    }

    /**
     * Get the <i>logical</i> path of this request. The web application may be
     * "mounted" on some path (i.e. it is being proxied as part of a larger
     * site). This method will give you the path, sans the base portion of it.
     * So, if a request URL might be for
     * <code>http://example.com/myapp/foo/bar</code>, <code>getPath()</code>
     * might return <code>foo/bar</code>.
     *
     * @return A url path
     * @deprecated use path()
     */
    @Deprecated
    default Path getPath() {
        return path();
    }

    /**
     * Get a header as an appropriate Java object, or null if it is not present.
     *
     * @see Headersfor a standard HTTP header types
     * @param <>> The return type
     * @param value A header definition/parser/encoder
     * @return An object or null if the header is missing or invalid
     * @deprecated use header()
     */
    @Deprecated
    default <T> T getHeader(HeaderValueType<T> value) {
        return header(value);
    }

    /**
     * Get all headers matching a type.
     *
     * @param <T> The type
     * @param headerType The header
     * @return A list of headers
     * @deprecated use headers()
     */
    @Deprecated
    default <T> List<T> getHeaders(HeaderValueType<T> headerType) {
        return headers(headerType);
    }

    /**
     * Get all headers (pruning duplicate names) as a <i>case-insensitive</i> map of
     * CharSequence to header value.
     *
     * @return A map.
     * @deprecated use headersAsMap()
     */
    @Deprecated
    default Map<CharSequence, CharSequence> getHeadersAsMap() {
        return headersAsMap();
    }

    /**
     * Gets the HTTP parameters as a flat map, ignoring duplicated keys. To be
     * technically correct, the same request parameter may be repeated any
     * number of times, so a Map &lt;String, List&lt;String&gt;&gt; would
     * losslessly represent parameters. In practice, this is usually a bit
     * pedantic and annoying, so this provides a convenient way to flatten it
     * into a map.
     *
     * @return A map
     * @deprecated use urlParametersAsMap()
     */
    @Deprecated
    default Map<String, String> getParametersAsMap() {
        return urlParametersAsMap();
    }

    /**
     * Very primitive Java dynamic proxy magic: You write a Java interface with
     * methods that each return a primitive type, and whose name corresponds to
     * a URL parameter you expect to get.
     * <p/>
     * Calling this method will generate a dynamic proxy object of the interface
     * type you pass, which implements these methods to return objects, doing
     * the necessary conversions.
     * <p/>
     * Be aware that conversion can result in NumberFormatExceptions, etc.
     *
     * @param <T>
     * @param type
     * @return
     * @deprecated use urlParametersAs
     */
    @Deprecated
    default <T> T getParametersAs(Class<T> type) {
        return urlParametersAs(type);
    }

    /**
     * Get a URL query string parameter as an integer.
     *
     * @param name The parameter name
     * @return A parameter which may not be present
     * @deprecated Use intUrlParameter()
     */
    @Deprecated
    default Optional<Integer> getIntParameter(String name) {
        return intUrlParameter(name);
    }

    /**
     * Get a URL query string parameter as a long.
     *
     * @param name The parameter name
     * @return A long which may not be present
     * @deprecated Use longUrlParameter()
     */
    @Deprecated
    default Optional<Long> getLongParameter(String name) {
        return longUrlParameter(name);
    }

    /**
     * Get the request body as a string, in the content-type
     * header's encoding, or UTF-8 if none.
     *
     * @return A string, or null if no body
     * @throws IOException If decoding fails
     * @deprecated use stringContent()
     */
    @Deprecated
    default String getContentAsString() throws IOException {
        return stringContent();
    }

    /**
     * Determine if this request's connection header requests leaving the
     * connection open.  This method is used by the framework to decide what to
     * do at the end of sending a response.  It can also be used with the settings
     * value "neverKeepAlive" to disable any keep-alive behavior within the
     * application.  In particular, unlike Netty's utility methods, this method
     * defaults to <code>false</code> if no <code>Connection</code> header is
     * present.
     *
     * @return True if the connection should be kept alive after the conclusion
     * of responding to this request.
     * @deprecated use requestsConnectionStayOpen()
     */
    @Deprecated
    default boolean isKeepAlive() {
        return requestsConnectionStayOpen();
    }
}
