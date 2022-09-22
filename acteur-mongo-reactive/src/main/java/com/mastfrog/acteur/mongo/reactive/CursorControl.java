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
package com.mastfrog.acteur.mongo.reactive;

import com.mastfrog.util.preconditions.Checks;
import com.mongodb.CursorType;
import com.mongodb.client.model.Collation;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import java.time.Duration;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.bson.conversions.Bson;
import org.reactivestreams.Publisher;

/**
 * Sets up the parameters on a <code>FindIterable</code> - call its setters,
 * then use its <code>apply()</code> method. Or in the case of
 * WriterCursorContentAsJSON, simply pass it to next().
 *
 * @author Tim Boudreau
 */
public final class CursorControl {

    private boolean findOne;
    private int batchSize = 10;
    private CursorType type = CursorType.NonTailable;
    private int limit = Integer.MAX_VALUE;
    private Duration maxTime;
    private Bson projection;
    private Bson filter;
    private Boolean noTimeout;

    static final CursorControl DEFAULT = new CursorControl().lock();
    static final CursorControl FIND_ONE = new CursorControl().findOne().batchSize(1).lock();
    private Bson sort;
    private Bson modifiers;
    private boolean partial;
    private boolean locked;
    private Boolean allowDiskUse;
    private Boolean bypassDocumentValidation;
    private Collation collation;
    private int skip = -1;

    public CursorControl() {

    }

    CursorControl(CursorControl orig) {
        this.findOne = orig.findOne;
        this.batchSize = orig.batchSize;
        this.type = orig.type;
        this.limit = orig.limit;
        this.maxTime = orig.maxTime;
        this.projection = orig.projection;
        this.filter = orig.filter;
        this.noTimeout = orig.noTimeout;
        this.sort = orig.sort;
        this.modifiers = orig.modifiers;
        this.allowDiskUse = orig.allowDiskUse;
        this.bypassDocumentValidation = orig.bypassDocumentValidation;
        this.collation = orig.collation;
        this.skip = orig.skip;
        this.partial = orig.partial;
    }

    int batchSize() {
        return batchSize;
    }

    public CursorControl skip(int val) {
        this.skip = val;
        return this;
    }

    /**
     * Make this CursorControl read-only.
     *
     * @return this
     */
    public CursorControl lock() {
        locked = true;
        return this;
    }

    public CursorControl partial() {
        partial = true;
        return this;
    }

    public CursorControl noPartial() {
        partial = false;
        return this;
    }

    public CursorControl copy() {
        return new CursorControl(this);
    }

    public boolean isFindOne() {
        return findOne;
    }

    public CursorControl findOne() {
        return findOne(true);
    }

    private void checkLocked() {
        if (locked) {
            throw new IllegalStateException("Read-only CursorControl - use copy()"
                    + " to create a writable one");
        }
    }

    public CursorControl findOne(boolean findOne) {
        checkLocked();
        this.findOne = findOne;
        this.batchSize = 1;
        return this;
    }

    public CursorControl batchSize(int batchSize) {
        Checks.nonNegative("batchSize", batchSize);
        checkLocked();
        this.batchSize = batchSize;
        return this;
    }

    public CursorControl cursorType(CursorType type) {
        Checks.notNull("type", type);
        checkLocked();
        this.type = type;
        return this;
    }

    public CursorControl limit(int limit) {
        Checks.nonNegative("limit", limit);
        checkLocked();
        this.limit = limit;
        return this;
    }

    public CursorControl maxTime(Duration maxTime) {
        checkLocked();
        this.noTimeout = null;
        this.maxTime = maxTime;
        return this;
    }

    public CursorControl projection(Bson projection) {
        checkLocked();
        this.projection = projection;
        return this;
    }

    public CursorControl sort(Bson sort) {
        checkLocked();
        this.sort = sort;
        return this;
    }

    public CursorControl filter(Bson filter) {
        checkLocked();
        this.filter = filter;
        return this;
    }

    public CursorControl modifiers(Bson modifiers) {
        checkLocked();
        this.modifiers = modifiers;
        return this;
    }

    public CursorControl noTimeout(boolean noTimeout) {
        checkLocked();
        this.noTimeout = noTimeout;
        this.maxTime = null;
        return this;
    }

    public CursorControl allowDiskUse(boolean allowDiskUse) {
        checkLocked();
        this.allowDiskUse = allowDiskUse;
        return this;
    }

    public CursorControl bypassDocumentValidation(boolean bypassDocumentValidation) {
        checkLocked();
        this.bypassDocumentValidation = bypassDocumentValidation;
        return this;
    }

    public CursorControl collation(Collation collation) {
        checkLocked();
        this.collation = collation;
        return this;
    }

    <T> Publisher<T> _apply(Publisher<T> it) {
        if (it instanceof AggregatePublisher<?>) {
            apply((AggregatePublisher<?>) it);
        } else if (it instanceof FindPublisher<?>) {
            apply((FindPublisher<?>) it);
        }
        return it;
    }

    public <T> AggregatePublisher<T> apply(AggregatePublisher<T> ai) {
        ai = ai.batchSize(batchSize);
        if (maxTime != null) {
            ai = ai.maxTime(maxTime.toMillis(), MILLISECONDS);
        }
        if (allowDiskUse != null) {
            ai = ai.allowDiskUse(allowDiskUse);
        }
        if (bypassDocumentValidation != null) {
            ai = ai.bypassDocumentValidation(bypassDocumentValidation);
        }
        return ai;
    }

    @SuppressWarnings("deprecation")
    public <T> FindPublisher<T> apply(FindPublisher<T> fi) {
        fi = fi.batchSize(batchSize).cursorType(type).limit(limit).partial(partial);
        if (maxTime != null) {
            fi = fi.maxTime(maxTime.toMillis(), MILLISECONDS);
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
//            fi = fi.modifiers(modifiers);
            // XXX is hint really what was modifiers on the 3.x driver?
            fi = fi.hint(modifiers);
        }
        if (collation != null) {
            fi = fi.collation(collation);
        }
        if (skip > 0) {
            fi = fi.skip(skip);
        }
        return fi;
    }

}
