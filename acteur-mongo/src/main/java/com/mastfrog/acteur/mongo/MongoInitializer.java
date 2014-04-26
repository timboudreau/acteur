package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Intercepts MongoClient and collection creation calls. Note: This class
 * registers itself in the superclass constructor - it's best of constructors of
 * subclasses do nothing of significance.
 * <p/>
 * Note that instances should not be stateful - if the connection is lost, it is
 * entirely possible for an initializer to be called more than once for the same
 * purpose. Implementations should be stateless.
 * <p/>
 * To register your MongoInitializer, simply bind it as an eager singleton.
 *
 * @author Tim Boudreau
 */
public abstract class MongoInitializer {

    @Inject
    protected MongoInitializer(Registry registry) {
        registry.register(this);
    }

    protected void onBeforeCreateMongoClient(String host, int port) {
    }

    /**
     * Called once the client is created
     *
     * @param client
     */
    protected void onMongoClientCreated(MongoClient client) {
    }

    /**
     * Override to ensure indexes, etc..
     *
     * @param collection
     */
    protected void onCreateCollection(DBCollection collection) {
    }

    /**
     * Called to set up parameters for a collection which is being created
     */
    protected void onBeforeCreateCollection(String name, BasicDBObject params) {
    }

    @Singleton
    protected static final class Registry implements Iterable<MongoInitializer> {

        private final List<MongoInitializer> initializers = Collections.synchronizedList(new ArrayList<MongoInitializer>());
        private Reference<MongoClient> client = null;
        private final ScheduledExecutorService tp = Executors.newScheduledThreadPool(1);

        void register(MongoInitializer init) {
            initializers.add(init);
            MongoClient client = null;
            synchronized (this) {
                if (this.client != null) {
                    client = this.client.get();
                }
            }
            if (client != null) {
                // XXX this is so the wrong way to get out of a chicken-and-the-egg situation
                tp.schedule(new RunInit(client, init), 200, TimeUnit.MILLISECONDS);
            }
        }

        static class RunInit implements Runnable {

            private final MongoClient client;
            private final MongoInitializer init;

            public RunInit(MongoClient client, MongoInitializer init) {
                this.client = client;
                this.init = init;
            }

            @Override
            public void run() {
                synchronized (init) {
                    init.onMongoClientCreated(client);
                }
            }
        }

        protected void onBeforeCreateMongoClient(String host, int port) {
            for (MongoInitializer ini : initializers) {
                ini.onBeforeCreateMongoClient(host, port);
            }
        }

        @Override
        public Iterator<MongoInitializer> iterator() {
            return Collections.unmodifiableList(initializers).iterator();
        }

        void onBeforeCreateCollection(String name, BasicDBObject params) {
            for (MongoInitializer mi : initializers) {
                mi.onBeforeCreateCollection(name, params);
            }
        }

        void onCreateCollection(DBCollection collection) {
            for (MongoInitializer mi : initializers) {
                mi.onCreateCollection(collection);
            }
        }

        synchronized void onMongoClientCreated(MongoClient client) {
            this.client = new WeakReference<>(client);
            for (MongoInitializer mi : initializers) {
                synchronized (mi) {
                    mi.onMongoClientCreated(client);
                }
            }
        }
    }
}
