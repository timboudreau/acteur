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
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mongodb.WriteConcern;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 *
 * @author Tim Boudreau
 */
public final class MongoUpdater {

    private final Chain<Acteur, ? extends Chain<Acteur, ?>> chain;
    private final Deferral deferral;
    private final ApplicationControl ctrl;

    @Inject
    MongoUpdater(Chain<Acteur, ? extends Chain<Acteur, ?>> chain, Deferral deferral, ApplicationControl ctrl) {
        this.chain = chain;
        this.deferral = deferral;
        this.ctrl = ctrl;
    }

    public <T> Updates<T> withCollection(MongoCollection<T> collection) {
        return new UpdatesImpl<>(collection);
    }

    private class UpdatesImpl<T> implements Updates<T> {

        private MongoCollection<T> collection;
        private boolean used;
        private Callable<?> onSuccess;
        private volatile HttpResponseStatus onFailure;
        private volatile Object failureMessage;

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
            insertOne(obj, message, onSuccess, null);
        }

        @Override
        public void insertMany(List<T> objs) {
            insertMany(objs, null, OK, null);
        }

        @Override
        public void insertMany(List<T> objs, Object message) {
            insertMany(objs, message, OK, null);
        }

        @Override
        public void insertMany(List<T> objs, Object message, HttpResponseStatus onSuccess) {
            insertMany(objs, message, onSuccess, null);
        }

        @Override
        public void insertMany(List<T> objs, Object message, HttpResponseStatus onSuccess, Consumer<Throwable> onInsert) {
            checkUsed();
            used = true;
            chain.add(MongoResultActeur.class);
            deferral.defer((Resumer resumer) -> {
                collection.insertMany(objs, new VoidCallback(resumer, message == null ? objs : message, onSuccess, onInsert));
            });
        }

        @Override
        public void deleteOne(Bson obj) {
            deleteOne(obj, null, OK);
        }

        @Override
        public void deleteOne(Bson obj, Object message) {
            deleteOne(obj, message, OK, null);
        }

        @Override
        public void deleteOne(Bson obj, Object message, HttpResponseStatus onSuccess) {
            deleteOne(obj, message, onSuccess, null);
        }

        public void deleteOne(Bson obj, Object message, HttpResponseStatus onSuccess, BiConsumer<Throwable, DeleteResult> onDelete) {
            deleteSome(obj, message, onSuccess, onDelete, false);
        }

        @Override
        public void deleteMany(Bson obj) {
            deleteMany(obj, null, OK);
        }

        @Override
        public void deleteMany(Bson obj, Object message) {
            deleteMany(obj, message, OK, null);
        }

        @Override
        public void deleteMany(Bson obj, Object message, HttpResponseStatus onSuccess) {
            deleteMany(obj, message, onSuccess, null);
        }

        public void deleteMany(Bson obj, Object message, HttpResponseStatus onSuccess, BiConsumer<Throwable, DeleteResult> onDelete) {
            deleteSome(obj, message, onSuccess, onDelete, true);
        }

        private void deleteSome(Bson obj, Object message, HttpResponseStatus onSuccess, BiConsumer<Throwable, DeleteResult> onDelete, boolean isMany) {
            checkUsed();
            used = true;
            chain.add(MongoResultActeur.class);
            deferral.defer((Resumer resumer) -> {
                if (isMany) {
                    collection.deleteMany(obj, new DeleteCallback(resumer, message, onSuccess, onDelete));
                } else {
                    collection.deleteOne(obj, new DeleteCallback(resumer, message, onSuccess, onDelete));
                }
            });
        }

        @Override
        public void insertOne(T obj, Object message, HttpResponseStatus onSuccess, Consumer<Throwable> onInsert) {
            checkUsed();
            used = true;
            chain.add(MongoResultActeur.class);
            deferral.defer((Resumer resumer) -> {
                collection.insertOne(obj, new VoidCallback(resumer, message == null ? obj : message, onSuccess, onInsert));
            });
        }

        @Override
        public void updateOne(Document query, Map<String, Object> m) {
            updateOne(query, m, null);
        }

        @Override
        public void updateOne(Document query, Map<String, Object> m, Object message) {
            updateOne(query, m, message, OK, null);
        }

        @Override
        public void updateOne(Document query, Map<String, Object> m, Object message, HttpResponseStatus status) {
            updateOne(query, m, message, status, null);
        }

        @Override
        public void updateOne(Document query, Map<String, Object> m, Object message, HttpResponseStatus status, BiConsumer<Throwable, UpdateResult> onUpdate) {
            checkUsed();
            used = true;
            chain.add(MongoResultActeur.class);
            deferral.defer((Resumer resumer) -> {
                collection.updateOne(query, new Document("$set", new Document(m)), new UpdateResultCallback(resumer, message, status, onUpdate));
            });
        }

        public Updates onFailure(HttpResponseStatus status, Object failureMessage) {
            checkUsed();
            this.onFailure = status;
            this.failureMessage = failureMessage;
            return this;
        }

        public Updates onFailure(HttpResponseStatus status) {
            return onFailure(status, null);
        }

        private class UpdateResultCallback implements SingleResultCallback<UpdateResult> {

            private final Resumer resumer;
            private final Object message;
            private HttpResponseStatus status;
            private final BiConsumer<Throwable, UpdateResult> onDelete;

            public UpdateResultCallback(Resumer resumer, Object message, HttpResponseStatus status, BiConsumer<Throwable, UpdateResult> onDelete) {
                this.resumer = resumer;
                this.message = message;
                this.status = status == null ? OK : status;
                this.onDelete = onDelete;
            }

            @Override
            public void onResult(UpdateResult t, Throwable thrwbl) {
                if (thrwbl == null && onSuccess != null) {
                    try {
                        onSuccess.call();
                    } catch (Exception e) {
                        ctrl.internalOnError(e);
                    }
                }
                if (onDelete != null) {
                    onDelete.accept(thrwbl, t);
                }
                Object msg = message;
                if (msg == null) {
                    msg = map("matched").to(t.getMatchedCount())
                            .map("acknowledged").to(t.wasAcknowledged())
                            .map("updated").to(t.getModifiedCount())
                            .map("upsert_id").to(t.getUpsertedId())
                            .build();
                }
                if (t.getModifiedCount() == 0) {
                    status = onFailure == null ? BAD_REQUEST : onFailure;
                    if (failureMessage != null) {
                        msg = failureMessage;
                    }
                }
                resumer.resume(new MongoResult(msg, thrwbl, status));
            }
        }

        private class DeleteCallback implements SingleResultCallback<DeleteResult> {

            private final Resumer resumer;
            private final Object message;
            private HttpResponseStatus status;
            private final BiConsumer<Throwable, DeleteResult> onDelete;

            public DeleteCallback(Resumer resumer, Object message, HttpResponseStatus status, BiConsumer<Throwable, DeleteResult> onDelete) {
                this.resumer = resumer;
                this.message = message;
                this.status = status == null ? OK : status;
                this.onDelete = onDelete;
            }

            @Override
            public void onResult(DeleteResult t, Throwable thrwbl) {
                if (thrwbl == null && onSuccess != null) {
                    try {
                        onSuccess.call();
                    } catch (Exception e) {
                        ctrl.internalOnError(e);
                    }
                }
                if (onDelete != null) {
                    onDelete.accept(thrwbl, t);
                }
                Object msg = message;
                if (msg == null) {
                    msg = map("count").to(t.getDeletedCount()).map("acknowledged").to(t.wasAcknowledged()).build();
                }
                if (t.getDeletedCount() == 0 && t.wasAcknowledged()) {
                    status = onFailure == null ?  BAD_REQUEST : onFailure;
                    if (failureMessage != null) {
                        msg = failureMessage;
                    }
                }
                resumer.resume(new MongoResult(msg, thrwbl, status));
            }
        }

        private class VoidCallback implements SingleResultCallback<Void> {

            private final Resumer resumer;
            private final Object message;
            private final HttpResponseStatus status;
            private final Consumer<Throwable> onInsert;

            public VoidCallback(Resumer resumer, Object message, HttpResponseStatus status, Consumer<Throwable> onInsert) {
                this.resumer = resumer;
                this.message = message;
                this.status = status == null ? OK : status;
                this.onInsert = onInsert;
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
                if (onInsert != null) {
                    onInsert.accept(thrwbl);
                }
                resumer.resume(new MongoResult(message, thrwbl, status));
            }
        }
    }

    public interface Updates<T> {

        public Updates onFailure(HttpResponseStatus status);

        public Updates onFailure(HttpResponseStatus status, Object failureMessage);

        public void insertOne(T obj);

        public void insertOne(T obj, Object message);

        public void insertOne(T obj, Object message, HttpResponseStatus onSuccess);

        public void insertOne(T obj, Object message, HttpResponseStatus onSuccess, Consumer<Throwable> onInsert);

        void insertMany(List<T> objs);

        void insertMany(List<T> objs, Object message);

        void insertMany(List<T> objs, Object message, HttpResponseStatus onSuccess);

        void insertMany(List<T> objs, Object message, HttpResponseStatus onSuccess, Consumer<Throwable> onInsert);

        void deleteOne(Bson obj);

        void deleteOne(Bson obj, Object message);

        void deleteOne(Bson obj, Object message, HttpResponseStatus onSuccess);

        void deleteOne(Bson obj, Object message, HttpResponseStatus onSuccess, BiConsumer<Throwable, DeleteResult> onDelete);

        void deleteMany(Bson obj);

        void deleteMany(Bson obj, Object message);

        void deleteMany(Bson obj, Object message, HttpResponseStatus onSuccess);

        void deleteMany(Bson obj, Object message, HttpResponseStatus onSuccess, BiConsumer<Throwable, DeleteResult> onDelete);

        void updateOne(Document query, Map<String, Object> m);

        void updateOne(Document query, Map<String, Object> m, Object message);

        void updateOne(Document query, Map<String, Object> m, Object message, HttpResponseStatus status);

        void updateOne(Document query, Map<String, Object> m, Object message, HttpResponseStatus status, BiConsumer<Throwable, UpdateResult> onUpdate);

        Updates<T> onSuccess(Callable<?> r);

        Updates<T> withWriteConcern(WriteConcern concern);
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
