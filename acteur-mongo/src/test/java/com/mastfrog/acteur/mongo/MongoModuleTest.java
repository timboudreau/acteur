package com.mastfrog.acteur.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.giulius.Dependencies;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openide.util.Exceptions;

/**
 *
 * @author tim
 */
public class MongoModuleTest {

    private static final long port = 29001;

    private static File createMongoDir() {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File mongoDir = new File(tmp, "mongo-" + System.currentTimeMillis());
        if (!mongoDir.mkdirs()) {
            throw new AssertionError("Could not create " + mongoDir);
        }
        return mongoDir;
    }

    private static Process startMongoDB() throws IOException, InterruptedException {
        if (mongoDir == null) {
            mongoDir = createMongoDir();
        }
        ProcessBuilder pb = new ProcessBuilder().command("mongod", "--dbpath",
                mongoDir.getAbsolutePath(), "--nojournal", "--smallfiles", "-nssize", "1",
                "--noprealloc", "--slowms", "5", "--port", "" + port);

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        Process result = pb.start();
        Thread.sleep(1000);
        try {
            int code = result.exitValue();
            System.out.println("MongoDB process exited with " + code);
            return null;
        } catch (IllegalThreadStateException ex) {
            return result;
        }
    }

    private static Process mongo;
    private static File mongoDir;
    private MongoClient client;

    @BeforeClass
    public static void setUpClass() throws IOException, InterruptedException {
        mongo = startMongoDB();
    }

    private static void cleanup(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                cleanup(f);
                f.delete();
            }
        }
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                f.delete();
            }
        }
        dir.delete();
    }

    @AfterClass
    public static void tearDownClass() {
        if (mongo != null) {
            mongo.destroy();
            try {
                mongo.waitFor();
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                cleanup(mongoDir);
            }
        }
        System.out.println("REALLY EXIT");
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testIt() throws IOException, InterruptedException {
        if (mongo == null) {
            return;
        }
        MongoModule mod = new MongoModule("testit");
        mod.bindCollection("users", "ttusers");
        mod.bindCollection("capped", "cappedStuff");
        M m = new M();
        Dependencies deps = Dependencies.builder().add(mod).add(m).build();
        client = deps.getInstance(MongoClient.class);
        DB db = deps.getInstance(DB.class);

        assertNotNull(db);
        assertEquals("testit", db.getName());

        Fixture f = deps.getInstance(Fixture.class);
        assertNotNull(f);

        assertNotNull(f.users);
        assertNotNull(f.capped);
        assertEquals("ttusers", f.users.getName());
        assertEquals("cappedStuff", f.capped.getName());
        assertTrue(f.capped.isCapped());

        mongo.destroy();

        Ge ge = new Ge(deps);
        Thread t = new Thread(ge);
        t.setDaemon(true);
        t.start();
        ge.await();
        Thread.yield();

        mongo = startMongoDB();

        DB db3 = deps.getInstance(DB.class);

        assertNotNull(db3);
        Fixture f1 = deps.getInstance(Fixture.class);
        assertNotSame(f, f1);

        assertEquals("testit", db3.getName());

        assertNotNull(ge.db);
        assertEquals(db3.getName(), ge.db.getName());
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

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(Initializer.class).asEagerSingleton();
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
