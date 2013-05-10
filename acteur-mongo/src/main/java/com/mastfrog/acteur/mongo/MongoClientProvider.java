package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mongodb.MongoClient;
import java.net.UnknownHostException;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class MongoClientProvider implements Provider<MongoClient>, Runnable {

    private volatile MongoClient client;
    private final Dependencies deps;
    private final Settings settings;
    private final ShutdownHookRegistry hooks;
    private volatile boolean added;
    private final MongoInitializer.Registry registry;

    @Inject
    public MongoClientProvider(Dependencies deps, Settings settings, ShutdownHookRegistry hooks, MongoInitializer.Registry registry) {
        this.deps = deps;
        this.settings = settings;
        this.hooks = hooks;
        this.registry = registry;
    }

    private String mongoHost() {
        try {
            return deps.getInstance(Key.get(String.class, Names.named(MongoModule.MONGO_HOST)));
        } catch (Exception e) {
            return "localhost";
        }
    }

    private Integer mongoPort() {
        try {
            return deps.getInstance(Key.get(Integer.class, Names.named(MongoModule.MONGO_PORT)));
        } catch (Exception e) {
            return 27017;
        }
    }

    @Override
    public MongoClient get() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        String host = settings.getString(MongoModule.MONGO_HOST, mongoHost());
                        int port = settings.getInt(MongoModule.MONGO_PORT, mongoPort());
                        registry.onBeforeCreateMongoClient(host, port);
                        client = new MongoClient(host, port);
                        registry.onMongoClientCreated(client);
                        if (!added) {
                            hooks.add(this);
                            added = true;
                        }
                    } catch (UnknownHostException ex) {
                        Exceptions.chuck(ex);
                    }
                }
            }
        }
        return client;
    }

    @Override
    public void run() {
        System.out.println("CLOSING CLIENT ON " + Thread.currentThread());
        client.close();
    }
}
