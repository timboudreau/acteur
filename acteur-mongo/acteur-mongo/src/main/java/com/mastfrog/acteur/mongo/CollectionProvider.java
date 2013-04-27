package com.mastfrog.acteur.mongo;

import com.google.inject.Provider;
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

    public CollectionProvider(Provider<DB> db, String name, Provider<MongoInitializer.Registry> reg) {
        this.db = db;
        this.name = name;
        this.reg = reg;
    }

    @Override
    public DBCollection get() {
        DB d = db.get();
        if (!d.collectionExists(name)) {
            BasicDBObject arg = new BasicDBObject();
            MongoInitializer.Registry registry = reg.get();
            registry.onBeforeCreateCollection(name, arg);
            DBCollection coll = d.createCollection(name, arg);
            registry.onCreateCollection(coll);
            return coll;
        } else {
            return d.getCollection(name);
        }
    }
}
