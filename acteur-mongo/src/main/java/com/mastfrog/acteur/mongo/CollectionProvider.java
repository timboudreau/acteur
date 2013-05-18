package com.mastfrog.acteur.mongo;

import com.google.inject.Provider;
import com.mastfrog.util.Exceptions;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;

/**
 *
 * @author Tim Boudreau
 */
final class CollectionProvider implements Provider<DBCollection> {

    private final Provider<DB> db;
    private final String name;
    private final Provider<MongoInitializer.Registry> reg;
    private DBCollection collection;

    public CollectionProvider(Provider<DB> db, String name, Provider<MongoInitializer.Registry> reg) {
        this.db = db;
        this.name = name;
        this.reg = reg;
    }
    
    private DBCollection _get() {
        DB d = db.get();
        if (!d.collectionExists(name)) {
            synchronized (this) {
                if (!d.collectionExists(name)) {
                    BasicDBObject arg = new BasicDBObject();
                    MongoInitializer.Registry registry = reg.get();
                    try {
                        registry.onBeforeCreateCollection(name, arg);
                        DBCollection coll = d.createCollection(name, arg);
                        registry.onCreateCollection(coll);
                        return collection = coll;
                    } catch (com.mongodb.CommandFailureException ex) {
                        if ("collection already exists".equals(ex.getCommandResult().getErrorMessage())) {
                            return d.getCollection(name);
                        }
                        return Exceptions.chuck(ex);
                    }

                } else {
                    return collection = d.getCollection(name);
                }
            }
        } else {
            return collection = d.getCollection(name);
        }
    }

    @Override
    public DBCollection get() {
        if (collection == null) {
            synchronized(this) {
                if (collection == null) {
                    return _get();
                }
            }
        }
        return collection;
    }
}
