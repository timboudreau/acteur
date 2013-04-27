package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Intercepts MongoClient and collection creation calls.  Note:  This class
 * registers itself in the superclass constructor - it's best of constructors
 * of subclasses do nothing of significance.
 * <p/>
 * Note that instances should not be stateful - if the connection is lost, it is
 * entirely possible for an initializer to be called more than once for the 
 * same purpose.  Implementations should be stateless.
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
    
    /**
     * Called once the client is created
     * @param client 
     */
    protected abstract void onMongoClientCreated(MongoClient client);

    /**
     * Override to ensure indexes, etc..
     * @param collection 
     */
    protected abstract void onCreateCollection(DBCollection collection);
    
    /**
     * Called to set up parameters for a collection which is being created
     */
    protected abstract void onBeforeCreateCollection(String name, BasicDBObject params);
    
    @Singleton
    protected static final class Registry implements Iterable<MongoInitializer> {
        private final List<MongoInitializer> initializers = Collections.synchronizedList(new ArrayList<MongoInitializer>());
        
        void register(MongoInitializer init) {
            initializers.add(init);
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
        
        void onMongoClientCreated(MongoClient client) {
            for (MongoInitializer mi : initializers) {
                mi.onMongoClientCreated(client);
            }
        }
    }
}
