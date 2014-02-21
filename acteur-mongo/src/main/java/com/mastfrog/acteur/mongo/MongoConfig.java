package com.mastfrog.acteur.mongo;

/**
 * Implemented by MongoModule - used for configuring MongoDB initialization
 *
 * @author Tim Boudreau
 */
public interface MongoConfig<T extends MongoConfig> {

    /**
     * Add another object to be called on MongoClient creation and collection 
     * creation, to do things like set up indexes.
     * 
     * @param type A type
     * @return this
     */
    T addInitializer(Class<? extends MongoInitializer> type);

    /**
     * Bind the collection with the passed name to &#064;Named DBCollection of the
     * same name
     * @param bindingName The binding and collection name
     * @return this
     */
    T bindCollection(String bindingName);

    /**
     * Bind the collection with the passed name to &#064;Named DBCollection wth the
     * passed binding name
     * @param bindingName The binding used in &#064;Named
     * @param collectionName The collection name
     * @return this
     */
    T bindCollection(String bindingName, String collectionName);
}
