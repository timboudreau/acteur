/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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
package com.mastfrog.acteur.request;

import com.mastfrog.acteur.headers.HeaderValueType;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provides access to http request headers and URL.
 *
 * @author Tim Boudreau
 */
public interface HttpProtocolRequest {

    /**
     * Get an HTTP header.
     *
     * @param <T> A type
     * @param header A header spec
     * @return
     */
    default <T> Optional<T> httpHeader(HeaderValueType<T> header) {
        return httpHeader(notNull("header", header).name()).map(
                val -> header.toValue(val));
    }

    /**
     * Get the set of all header names in a request.
     *
     * @return A set of header names
     */
    Set<? extends CharSequence> httpHeaderNames();

    /**
     * Get an HTTP header.
     *
     * @param name A name
     * @return An optional
     */
    Optional<CharSequence> httpHeader(CharSequence name);

    /**
     * Get the anchor portion of the URI, if one was passed.
     *
     * @return An anchor if one is present
     */
    Optional<CharSequence> uriAnchor();

    /**
     * Get a path element of the URL path, if one exists.
     *
     * @param index The index of the /-delimited path element
     * @return An optional
     */
    Optional<CharSequence> uriPathElement(int index);

    /**
     * Get a query parameter, if one is present.
     *
     * @param name A name
     * @param decode If true, apply URL decoding to the result
     * @return A string or empty
     */
    Optional<CharSequence> uriQueryParameter(CharSequence name, boolean decode);

    /**
     * Get a query parameter, applying URL decoding.
     *
     * @param name The parameter name
     * @return A string if one is matched
     */
    default Optional<CharSequence> uriQueryParameter(CharSequence name) {
        return uriQueryParameter(name, true);
    }

    /**
     * Get a query parameter as a number, which may be one of Byte, Integer,
     * Short, Long, Double, Float, BigDecimal or BigInteger.
     *
     * @param <N> The number type
     * @param name The name of the query parameter
     * @param type The type to parse it to
     * @return An optional which may contain a number of the passed type
     */
    default <N extends Number> Optional<N> uriQueryParameter(CharSequence name, Class<N> type) {
        return asNumber(uriQueryParameter(name, false), type);
    }

    /**
     * Get a URI query parameter as a boolean.
     *
     * @param name A query parameter name
     * @return A boolean, if the query parameter is present
     */
    default Optional<Boolean> booleanUriQueryParameter(CharSequence name) {
        return uriQueryParameter(name, false)
                .map(val -> Strings.charSequencesEqual("true", val)
                ? Boolean.TRUE
                : Boolean.FALSE);
    }

    /**
     * Get a path element as a number.
     *
     * @param <N> A number type
     * @param index the index within the URL path of the parameter
     * @param type The type
     * @return An optional which may contain a number of the passed type
     */
    default <N extends Number> Optional<N> uriPathElement(int index, Class<N> type) {
        return asNumber(uriPathElement(index), type);
    }

    /**
     * Get the URL anchor as a number.
     *
     * @param <N> A number type
     * @param type The type
     * @return An optional which may contain a number of the passed type
     */
    default <N extends Number> Optional<N> uriAnchor(Class<N> type) {
        return asNumber(uriAnchor(), type);
    }

    /**
     * Get the HTTP method of the request.
     *
     * @return A method such as "GET"
     */
    String httpMethod();

    /**
     * Determine if the method matches the string value of the passed object
     * (allowing for anything that resolves case insensitively to an http method
     * name to match).
     *
     * @param o An object
     * @return True if the method name is a case insensitive match for the
     * passed object
     */
    default boolean isMethod(Object o) {
        return notNull("o", o).toString().equalsIgnoreCase(httpMethod());
    }

    /**
     * Get the request URI.
     *
     * @param preferHeaders If true, parse any of the common proxy headers
     * x-forwarded-for, etc. and use that if present
     * @return A string
     */
    String requestUri(boolean preferHeaders);

    /**
     * Get the request URI preferring that value from the headers if there is
     * one.
     *
     * @return A request uri
     */
    default String requestUri() {
        return requestUri(true);
    }

    static <N extends Number> Optional<N> asNumber(
            Optional<CharSequence> value,
            Class<N> type) {
        try {
            if (type == Long.class) {
                return value.map(v -> type.cast(Strings.parseLong(v)));
            } else if (type == Integer.class) {
                return value.map(v -> type.cast(Strings.parseInt(v)));
            } else if (type == Short.class) {
                return value.map(val -> type.cast(Short.valueOf(val.toString())));
            } else if (type == Byte.class) {
                return value.map(val -> type.cast(Byte.valueOf(val.toString())));
            } else if (type == Double.class) {
                return value.map(val -> type.cast(Double.valueOf(val.toString())));
            } else if (type == Float.class) {
                return value.map(val -> type.cast(Float.valueOf(val.toString())));
            } else if (type == BigInteger.class) {
                return value.map(val -> type.cast(new BigInteger(val.toString())));
            } else if (type == BigDecimal.class) {
                return value.map(val -> type.cast(new BigDecimal(val.toString())));
            } else {
                throw new IllegalArgumentException("Unknown numeric type " + type);
            }
        } catch (NumberFormatException nfe) {
            return Optional.empty();
        }
    }

}
