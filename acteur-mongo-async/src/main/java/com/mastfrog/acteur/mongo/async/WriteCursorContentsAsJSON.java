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

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoIterable;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.GONE;

/**
 *
 * @author Tim Boudreau
 */
@Description("Write out the contents of a cursor as JSON to the response, in small batches so the "
        + "entire result never needs to be held in memory.")
public class WriteCursorContentsAsJSON extends Acteur {

    @Inject
    WriteCursorContentsAsJSON(Deferral def, CursorControl ctrl, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, Closables clos, MongoIterable<?> find) {
        find(find, ctrl, def, chain, clos);
    }

    @SuppressWarnings("unchecked")
    private <T> MongoIterable<T> find(MongoIterable<T> result, CursorControl ctrl, Deferral def, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, final Closables clos) {
        result = ctrl._apply(result);
        final MongoIterable<T> it = result;
        def.defer((Resumer resumer) -> {
            if (ctrl.isFindOne()) {
                it.first(new SingleResultResume<>(resumer));
            } else {
                it.batchCursor(new CursorResultResume<>(clos, resumer));
            }
        });
        if (ctrl.isFindOne()) {
            chain.add(SendSingleResult.class);
        } else {
            chain.add(SendCursorResult.class);
        }
        next(result);
        return result;
    }

    static final class SingleResultResume<T> implements SingleResultCallback<T> {

        private final Resumer resumer;

        public SingleResultResume(Resumer resumer) {
            this.resumer = resumer;
        }

        @Override
        public void onResult(T t, Throwable thrwbl) {
            resumer.resume(new SingleResult(t, thrwbl));
        }
    }

    static final class CursorResultResume<T> implements SingleResultCallback<AsyncBatchCursor<T>> {

        private final Closables clos;
        private final Resumer resumer;

        public CursorResultResume(Closables clos, Resumer resumer) {
            this.clos = clos;
            this.resumer = resumer;
        }

        @Override
        public void onResult(AsyncBatchCursor<T> t, Throwable thrwbl) {
            clos.add(t);
            resumer.resume(new CursorResult<>(t, thrwbl));
        }

    }

    static final class SingleResult {

        final Object result;
        final Throwable thrown;

        public SingleResult(Object result, Throwable thrown) {
            this.result = result;
            this.thrown = thrown;
        }
    }

    static class SendSingleResult extends Acteur {

        @Inject
        SendSingleResult(SingleResult result, ApplicationControl ctrl) {
            if (result.thrown != null) {
                reply(Err.of(result.thrown));
                ctrl.internalOnError(result.thrown);
            } else {
                if (result.result == null) {
                    reply(GONE, "No such object");
                } else {
                    ok(result.result);
                }
            }
        }
    }

    static final class CursorResult<T> {

        final AsyncBatchCursor<T> cursor;
        final Throwable thrwbl;

        public CursorResult(AsyncBatchCursor<T> t, Throwable thrwbl) {
            this.cursor = t;
            this.thrwbl = thrwbl;
        }
    }

    static class SendCursorResult extends Acteur {

        @Inject
        @SuppressWarnings("unchecked")
        SendCursorResult(CursorResult res, HttpEvent evt, ApplicationControl ctrl, CursorWriterFactory fact) {
            if (res.thrwbl != null) {
                reply(Err.of(res.thrwbl));
            } else {
                reply(HttpResponseStatus.OK);
                setResponseBodyWriter((ChannelFutureListener) fact.create(res.cursor));
            }
        }
    }
}
