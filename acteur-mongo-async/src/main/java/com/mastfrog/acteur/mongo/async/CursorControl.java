/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteur.mongo.async;

import com.mastfrog.util.Checks;
import com.mongodb.CursorType;
import com.mongodb.async.client.FindIterable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.bson.conversions.Bson;

/**
 * Sets up the parameters on a <code>FindIterable</code> - call its setters,
 * then use its <code>apply()</code> method. Or in the case of
 * WriterCursorContentAsJSON, simply pass it to next().
 *
 * @author Tim Boudreau
 */
public final class CursorControl {

    private boolean findOne;
    private int batchSize = 1;
    private CursorType type = CursorType.NonTailable;
    private int limit = Integer.MAX_VALUE;
    private Duration maxTime;
    private Bson projection;
    private Bson filter;
    private Boolean noTimeout;

    static final CursorControl DEFAULT = new CursorControl();
    static final CursorControl FIND_ONE = new CursorControl().findOne().batchSize(1);
    private Bson sort;
    private Bson modifiers;

    boolean isFindOne() {
        return findOne;
    }

    public CursorControl findOne() {
        return findOne(findOne);
    }

    public CursorControl findOne(boolean findOne) {
        this.findOne = findOne;
        this.batchSize = 1;
        return this;
    }

    public CursorControl batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public CursorControl cursorType(CursorType type) {
        this.type = type;
        return this;
    }

    public CursorControl limit(int limit) {
        Checks.nonNegative("limit", limit);
        this.limit = limit;
        return this;
    }

    public CursorControl maxTime(Duration maxTime) {
        this.noTimeout = null;
        this.maxTime = maxTime;
        return this;
    }

    public CursorControl projection(Bson projection) {
        this.projection = projection;
        return this;
    }

    public CursorControl sort(Bson sort) {
        this.sort = sort;
        return this;
    }

    public CursorControl filter(Bson filter) {
        this.filter = filter;
        return this;
    }

    public CursorControl modifiers(Bson modifiers) {
        this.modifiers = modifiers;
        return this;
    }

    public CursorControl noTimeout(boolean noTimeout) {
        this.noTimeout = noTimeout;
        this.maxTime = null;
        return this;
    }

    public <T> FindIterable<T> apply(FindIterable<T> fi) {
        fi = fi.batchSize(batchSize).cursorType(type).limit(limit);
        if (maxTime != null) {
            fi = fi.maxTime(maxTime.toMillis(), TimeUnit.MILLISECONDS);
        }
        if (projection != null) {
            fi = fi.projection(projection);
        }
        if (filter != null) {
            fi = fi.filter(filter);
        }
        if (noTimeout != null && noTimeout) {
            fi = fi.noCursorTimeout(true);
        }
        if (sort != null) {
            fi = fi.sort(sort);
        }
        if (modifiers != null) {
            fi.modifiers(modifiers);
        }
        return fi;
    }
}
