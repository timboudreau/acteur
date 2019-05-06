package com.mastfrog.acteur.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.mongo.MongoModuleTest.MM;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith({MongoHarness.Module.class, MM.class})
@IfBinaryAvailable("mongod")
public class MongoModuleTest {
    static class MM extends AbstractModule {
        @Override
        protected void configure() {
            MongoModule mod = new MongoModule("testit");
            mod.bindCollection("users", "ttusers");
            mod.bindCollection("capped", "cappedStuff");
            install(mod);
            bind(Initializer.class).asEagerSingleton();
        }
    }

    @Test
    public void testIt(MongoHarness mongo, MongoClient client, DB db, Fixture f, Dependencies deps) throws IOException, InterruptedException {
        assertNotNull(db);
        assertEquals("testit", db.getName());
        assertNotNull(f.users);
        assertNotNull(f.capped);
        assertEquals("ttusers", f.users.getName());
        assertEquals("cappedStuff", f.capped.getName());
        assertTrue(f.capped.isCapped());
//
//        mongo.stop();
//
//        Ge ge = new Ge(deps);
//        Thread t = new Thread(ge);
//        t.setDaemon(true);
//        t.start();
//        ge.await();
//        Thread.yield();
//
//        mongo.start();

        DB db3 = deps.getInstance(DB.class);

        assertNotNull(db3);
        Fixture f1 = deps.getInstance(Fixture.class);
        assertNotSame(f, f1);

        assertEquals("testit", db3.getName());

//        assertNotNull(ge.db);
//        assertEquals(db3.getName(), ge.db.getName());
        System.out.println("Test done");
    }

    private static class Ge implements Runnable {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final Dependencies deps;

        public Ge(Dependencies deps) {
            this.deps = deps;
        }

        void await() throws InterruptedException {
            latch.await();
        }

        private DB db;

        public void run() {
            latch.countDown();
            db = deps.getInstance(DB.class);
        }

    }

    static class Fixture {

        final DBCollection users;
        final DBCollection capped;

        @Inject
        Fixture(@Named("users") DBCollection users, @Named("capped") DBCollection capped) {
            this.users = users;
            this.capped = capped;
        }
    }
    static class Initializer extends MongoInitializer {

        volatile boolean created;
        Set<String> onCreateCalled = new HashSet<>();
        Set<String> onBeforeCreateCalled = new HashSet<>();

        @Inject
        public Initializer(Registry registry) {
            super(registry);
        }

        @Override
        protected void onMongoClientCreated(MongoClient client) {
            created = true;
        }

        @Override
        protected void onCreateCollection(DBCollection collection) {
            System.out.println("On create " + collection.getName());
            onCreateCalled.add(collection.getName());
        }

        @Override
        protected void onBeforeCreateCollection(String name, BasicDBObject params) {
            onBeforeCreateCalled.add(name);
            switch (name) {
                case "cappedStuff":
                    System.out.println("Set up capped");
                    params.append("capped", true).append("size", 10000).append("max", 1000);
            }
        }
    }
}
