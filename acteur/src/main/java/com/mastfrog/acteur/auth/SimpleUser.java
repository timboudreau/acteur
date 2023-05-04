/*
 * The MIT License
 *
 * Copyright 2014 tim.
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

package com.mastfrog.acteur.auth;

import java.util.Objects;

/**
 * A base interface for user object types which allows authentication frameworks
 * such as acteur-auth to share basic user properties, so they can be dropped
 * in. Nothing in Acteur assumes this will be the base user class for anything -
 * it is just a convenience to allow swapping of authentication frameworks that
 * do use it.
 *
 * @author Tim Boudreau
 */
public interface SimpleUser<IdType> {

    /**
     * Programmatic name of this user suitable for use in urls
     *
     * @return A name
     */
    String name();

    /**
     * Version of this user, to be incremented when the record of this user is
     * modified
     *
     * @return a version
     */
    int version();

    /**
     * An ID type, typically a database primary key or something like MongoDB's
     * ObjectID
     *
     * @return The id that uniquely identifies this user
     */
    IdType id();

    /**
     * Get a string representation of the ID suitable for use in URLs or other
     * places
     *
     * @return The string form of the id
     */
    String idAsString();

    /**
     * Get a human-readable display name, falling back to the name if none is
     * set
     *
     * @return A human readable name
     */
    String displayName();

    /**
     * Simple implementation of SimpleUser
     *
     * @param <T> The ID type
     */
    class Base<T> implements SimpleUser<T> {

        private final String name;
        private final int version;
        private final T id;
        private final String displayName;

        public Base(String name, int version, T id) {
            this(name, version, id, null);
        }

        public Base(String name, int version, T id, String displayName) {
            this.name = name;
            this.version = version;
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public final String name() {
            return name;
        }

        @Override
        public final int version() {
            return version;
        }

        @Override
        public final T id() {
            return id;
        }

        @Override
        public String idAsString() {
            return Objects.toString(id);
        }

        @Override
        public final String displayName() {
            return displayName == null ? name : displayName;
        }

        @Override
        public final boolean equals(Object o) {
            return o instanceof SimpleUser && id.equals(((SimpleUser) o).id());
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(id);
        }

        @Override
        public String toString() {
            return name + " (" + id + ")";
        }
    }
}
