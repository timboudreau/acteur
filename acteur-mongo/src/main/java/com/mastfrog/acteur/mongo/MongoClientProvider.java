package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import static com.mastfrog.acteur.mongo.MongoModule.DATABASE_NAME;
import static com.mastfrog.acteur.mongo.MongoModule.SETTINGS_KEY_MONGO_PASSWORD;
import static com.mastfrog.acteur.mongo.MongoModule.SETTINGS_KEY_MONGO_USER;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class MongoClientProvider implements Provider<MongoClient>, Runnable {

    private volatile MongoClient client;
    private final String dbName;
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
    public MongoClientProvider(@Named(DATABASE_NAME) String dbName, Settings settings, ShutdownHookRegistry hooks, MongoInitializer.Registry registry) {
        this.dbName = dbName;
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
                        String host = mongoHost();
                        int port = mongoPort();
                        registry.onBeforeCreateMongoClient(host, port);
                        MongoClientOptions opts = MongoClientOptions.builder()
                                .autoConnectRetry(true).connectionsPerHost(1500)
                                .cursorFinalizerEnabled(true)
                                .readPreference(ReadPreference.nearest())
                                .maxWaitTime(20000).build();
                        List<MongoCredential> credentials = new ArrayList<>();

                        String un = settings.getString(SETTINGS_KEY_MONGO_USER);
                        String pw = settings.getString(SETTINGS_KEY_MONGO_PASSWORD);
                        if (un != null && pw != null) {
                            MongoCredential cred = MongoCredential.createMongoCRCredential(un, 
                                    dbName, pw.toCharArray());
                            credentials.add(cred);
                        } else if (un != null) {
                            MongoCredential cred = MongoCredential.createGSSAPICredential(un);
                            credentials.add(cred);
                        }
                        ServerAddress addr = new ServerAddress(host, port);
                        client = credentials.isEmpty() ? new MongoClient(addr, opts)
                                : new MongoClient(addr, credentials, opts);
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
