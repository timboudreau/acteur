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
package com.mastfrog.acteur.mongo.reactive;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Asynchronously computed etag result which is placed into the scope to resume
 * the request chain.
 *
 * @author Tim Boudreau
 */
final class ETagResult {

    private String etag;
    private Throwable thrown;
    private ZonedDateTime lastModified;

    boolean ifNotThrown(Consumer<Throwable> thr) {
        if (hasThrown()) {
            thr.accept(thrown);
            return false;
        }
        return true;
    }

    boolean hasThrown() {
        return thrown != null;
    }

    Optional<String> etag() {
        return Optional.ofNullable(etag);
    }

    Optional<ZonedDateTime> lastModified() {
        return Optional.ofNullable(lastModified);
    }

    ETagResult setEtag(String etag) {
        this.etag = etag;
        return this;
    }

    ETagResult setThrown(Throwable thrown) {
        this.thrown = thrown;
        return this;
    }

    ETagResult updateLastModified(ZonedDateTime lastModified) {
        if (lastModified != null) {
            if (this.lastModified == null) {
                this.lastModified = lastModified;
            } else if (lastModified.isAfter(this.lastModified)) {
                this.lastModified = lastModified;
            }
        }
        return this;
    }

}
