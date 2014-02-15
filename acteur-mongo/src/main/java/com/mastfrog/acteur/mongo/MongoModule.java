package com.mastfrog.acteur.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.mastfrog.util.ConfigurationError;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple bindings for MongoDB
 *
 * @author Tim Boudreau
 */
public final class MongoModule extends AbstractModule {

    public static final String MONGO_HOST = "mongoHost";
    public static final String MONGO_PORT = "mongoPort";
    public static final String DATABASE_NAME = "_dbName";
    public static final String SETTINGS_KEY_MONGO_USER = "mongo.user";
    public static final String SETTINGS_KEY_MONGO_PASSWORD = "mongo.password";
    private boolean configured;
    private final Map<String, String> collectionForName = new HashMap<>();
    private final String databaseName;
    private final Set<Class<? extends MongoInitializer>> initializers = new HashSet<>();

    /**
     * Create a new module, attempting to find the main class name and use that
     * as the database name.
     */
    public MongoModule() {
        this(getMainClassName());
    }

    /**
     * Create a new module, and use the specified database name
     *
     * @param databaseName
     */
    public MongoModule(String databaseName) {
        this.databaseName = databaseName;
    }

    public MongoModule addInitializer(Class<? extends MongoInitializer> type) {
        initializers.add(type);
        return this;
    }

    private static String getMainClassName() {
        Exception e = new Exception();
        StackTraceElement[] els = e.getStackTrace();
        String className = els[els.length - 1].getClassName();
        if (className.contains(".")) {
            int ix = className.lastIndexOf(".");
            if (ix < className.length() - 1) {
                className = className.substring(ix + 1);
            }
        }
        System.out.println("Using MongoDB database " + className.toLowerCase());
        return className.toLowerCase();
    }

    /**
     * Bind a collection so it can be injected using &#064;Named, using the same
     * name in code and as a collection name
     *
     * @param bindingName The name that will be used in code
     * @return this
     */
    public final MongoModule bindCollection(String bindingName) {
        return bindCollection(bindingName, bindingName);
    }

    /**
     * Bind a collection so it can be injected using &#064;Named
     *
     * @param bindingName The name that will be used in code
     * @param collectionName The name of the actual collection
     * @return this
     */
    public final MongoModule bindCollection(String bindingName, String collectionName) {
        if (configured) {
            throw new ConfigurationError("Cannot add bindings after application is started");
        }
        collectionForName.put(bindingName, collectionName);
        return this;
    }

    public final String getDatabaseName() {
        return databaseName;
    }

    @Override
    protected void configure() {
        configured = true;
        bind(String.class).annotatedWith(Names.named(DATABASE_NAME)).toInstance(databaseName);
        // We want to bail during startup if we can't contact the
        // database, so use eager singleton to ensure we'll be
        bind(MongoClient.class).toProvider(MongoClientProvider.class);
        bind(DB.class).toProvider(DatabaseProvider.class);

        for (Class<? extends MongoInitializer> c : initializers) {
            bind(c).asEagerSingleton();
        }

        for (Map.Entry<String, String> e : collectionForName.entrySet()) {
            bind(DBCollection.class).annotatedWith(Names.named(e.getKey())).toProvider(
                    new CollectionProvider(binder().getProvider(DB.class),
                    e.getValue(), binder().getProvider(MongoInitializer.Registry.class)));
        }
    }
}
