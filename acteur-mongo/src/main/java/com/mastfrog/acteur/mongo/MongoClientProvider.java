package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
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
    @Inject(optional = true)
    @Named(MongoModule.MONGO_HOST)
    private String host;
    @Inject(optional = true)
    @Named(MongoModule.MONGO_PORT)
    private int port;

    @Inject
    public MongoClientProvider(Dependencies deps, Settings settings, ShutdownHookRegistry hooks, MongoInitializer.Registry registry) {
        this.deps = deps;
        this.settings = settings;
        this.hooks = hooks;
        this.registry = registry;
    }

    private String mongoHost() {
        return host == null ? "localhost" : host;
    }

    private Integer mongoPort() {
        return port == 0 ? 27017 : port;
    }

    @Override
    public MongoClient get() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        registry.onBeforeCreateMongoClient(mongoHost(), mongoPort());
                        client = new MongoClient(mongoHost(), mongoPort());
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
        client.close();
    }
}
