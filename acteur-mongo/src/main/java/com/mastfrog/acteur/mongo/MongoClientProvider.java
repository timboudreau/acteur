package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mongodb.MongoClient;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class MongoClientProvider implements Provider<MongoClient>, Runnable {

    private final Settings settings;
    private volatile MongoClient client;
    private final ShutdownHookRegistry hooks;
    private volatile boolean added;
    private final MongoInitializer.Registry registry;

    @Inject
    public MongoClientProvider(Settings settings, ShutdownHookRegistry hooks, MongoInitializer.Registry registry) {
        this.settings = settings;
        this.hooks = hooks;
        this.registry = registry;
    }

    public void reset() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                Logger.getLogger(MongoClientProvider.class.getName())
                        .log(Level.INFO, "Exception closing client", e);
            }
        }
        client = null;
    }

    @Override
    public MongoClient get() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        String host = settings.getString(MongoModule.MONGO_HOST, "localhost");
                        int port = settings.getInt(MongoModule.MONGO_PORT, 27017);
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
        client.close();
    }
}
