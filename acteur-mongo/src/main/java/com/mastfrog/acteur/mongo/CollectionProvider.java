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

    public CollectionProvider(Provider<DB> db, String name) {
        this.db = db;
        this.name = name;
    }

    @Override
    public DBCollection get() {
        DB d = db.get();
        if (!d.collectionExists(name)) {
            return d.createCollection(name, new BasicDBObject());
        } else {
            return d.getCollection(name);
        }
    }
}
