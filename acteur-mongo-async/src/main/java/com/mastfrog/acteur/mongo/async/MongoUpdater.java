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
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mongodb.WriteConcern;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoCollection;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.util.concurrent.Callable;

/**
 *
 * @author Tim Boudreau
 */
public final class MongoUpdater {

    private final Chain<Acteur> chain;
    private final Deferral deferral;
    private final ApplicationControl ctrl;

    @Inject
    MongoUpdater(Chain<Acteur> chain, Deferral deferral, ApplicationControl ctrl) {
        this.chain = chain;
        this.deferral = deferral;
        this.ctrl = ctrl;
    }

    public <T> Updates withCollection(MongoCollection<T> collection) {
        return new UpdatesImpl<>(collection);
    }

    private class UpdatesImpl<T> implements Updates<T> {

        private MongoCollection<T> collection;
        private boolean used;
        private Callable<?> onSuccess;

        public UpdatesImpl(MongoCollection<T> collection) {
            this.collection = collection;
        }

        public UpdatesImpl<T> onSuccess(Callable<?> r) {
            checkUsed();
            this.onSuccess = r;
            return this;
        }

        public UpdatesImpl<T> withWriteConcern(WriteConcern concern) {
            checkUsed();
            collection = collection.withWriteConcern(concern);
            return this;
        }

        private void checkUsed() {
            if (used) {
                throw new IllegalArgumentException("Can only use an Updates instance once");
            }
        }

        @Override
        public void insertOne(T obj) {
            insertOne(obj, null);
        }

        @Override
        public void insertOne(T obj, Object message) {
            insertOne(obj, message, OK);
        }

        @Override
        public void insertOne(T obj, Object message, HttpResponseStatus onSuccess) {
            checkUsed();
            used = true;
            collection.insertOne(obj, new VoidCallback(deferral.defer(), message == null ? obj : message, onSuccess));
            chain.add(MongoResultActeur.class);
        }

        private class VoidCallback implements SingleResultCallback<Void> {

            private final Resumer resumer;
            private final Object message;
            private final HttpResponseStatus status;

            public VoidCallback(Resumer resumer, Object message) {
                this(resumer, message, OK);
            }

            public VoidCallback(Resumer resumer, Object message, HttpResponseStatus status) {
                this.resumer = resumer;
                this.message = message;
                this.status = status == null ? OK : status;
            }

            @Override
            public void onResult(Void t, Throwable thrwbl) {
                if (thrwbl == null && onSuccess != null) {
                    try {
                        onSuccess.call();
                    } catch (Exception e) {
                        ctrl.internalOnError(e);
                    }
                }
                resumer.resume(new MongoResult(message, thrwbl, status));
            }
        }
    }

    public interface Updates<T> {

        public void insertOne(T obj);

        public void insertOne(T obj, Object message);

        public void insertOne(T obj, Object message, HttpResponseStatus onSuccess);

        public Updates<T> onSuccess(Callable<?> r);

        public Updates<T> withWriteConcern(WriteConcern concern);
    }

    public static class MongoResult {

        private final Object message;
        private final Throwable thrown;
        private final HttpResponseStatus status;

        public MongoResult(Object message, Throwable t, HttpResponseStatus status) {
            this.message = message;
            this.thrown = t;
            this.status = status;
        }

        public MongoResult(Object message, Throwable t) {
            this.message = message;
            this.thrown = t;
            this.status = HttpResponseStatus.OK;
        }
    }

    static class MongoResultActeur extends Acteur {

        @Inject
        MongoResultActeur(MongoResult res) {
            if (res.thrown != null) {
                reply(Err.of(res.thrown));
            } else {
                reply(res.status, res.message);
            }
        }
    }
}
