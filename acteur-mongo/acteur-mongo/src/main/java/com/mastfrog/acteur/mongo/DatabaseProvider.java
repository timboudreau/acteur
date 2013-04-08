package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.mongodb.DB;
import com.mongodb.MongoClient;

/**
 *
 * @author Tim Boudreau
 */
final class DatabaseProvider implements Provider<DB> {
    private final MongoClient client;
    private final String name;

    @Inject
    public DatabaseProvider(MongoClient client, @Named(value = "_dbName") String name) {
        this.client = client;
        this.name = name;
    }

    @Override
    public DB get() {
        return client.getDB(name);
    }

}
