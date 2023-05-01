package com.mastfrog.acteur.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.mongo.MongoDaemonVersion.MongoVersion;
import com.mastfrog.giulius.Ordered;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import com.mongodb.MongoClient;
import java.io.File;
import java.io.IOException;
import static java.lang.System.currentTimeMillis;
import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/**
 * Starts a local mongodb over java.io.tmpdir and cleans it up on shutdown; uses
 * a random, available port. Simply request this be injected in your test, and
 * use MongoHarness.Module, to have the db started for you and automatically
 * cleaned up.
 * <p/>
 * Test <code>failed()</code> if you want to detect if you're running on a
 * machine where mongodb is not installed.
 *
 * @author Tim Boudreau
 */
@Singleton
public class MongoHarness {

    private final int port;
    private final Init mongo;
    private static int count = 1;
    /*
    Try to connect too soon and you get a crash: https://jira.mongodb.org/browse/SERVER-23441
     */
    private static final int CONNECT_WAIT_MILLIS = 250;

    @Inject
    MongoHarness(@Named("mongoPort") int port, Init mongo) throws IOException, InterruptedException {
        this.port = port;
        this.mongo = mongo;
    }

    @Singleton
    @Ordered(Integer.MIN_VALUE)
    static class Init extends MongoInitializer implements Runnable {

        private final File mongoDir;
        private Process mongo;
        private int port;
        private volatile boolean mongodbGreaterThan36 = false;
        private final String replSetId = "rs" + Long.toString(currentTimeMillis(), 36);
        private final boolean replicaSet;
        private final List<String> additionalArgs = new ArrayList<>();
        private final String explicitName;

        @SuppressWarnings("LeakingThisInConstructor")
        @Inject
        public Init(Registry registry, ShutdownHookRegistry shutdownHooks, Settings settings) {
            super(registry);
            replicaSet = settings.getBoolean("mongo.replica.set", false);
            shutdownHooks.add(this);
            explicitName = settings.getString("testname");
            String addtl = settings.getString("mongod.additional.args");
            if (addtl != null) {
                for (CharSequence seq : Strings.splitUniqueNoEmpty(' ', addtl)) {
                    additionalArgs.add(seq.toString());
                }
            }
            mongoDir = createMongoDir();
        }

        @Override
        public void onMongoClientCreated(MongoClient client) {
            if (replicaSet) {
                client.getDatabase("admin").runCommand(
                        new Document("replSetInitiate", new Document("_id", replSetId)
                                .append("members", Arrays.asList(new Document("host", "localhost:" + port)
                                        .append("_id", 1)))));
            }
            mongodbGreaterThan36 = version().majorVersion() > 3
                    || (version().majorVersion() == 3 && version().minorVersion() > 6);
        }

        @Override
        public void onBeforeCreateMongoClient(String host, int port) {
            try {
                mongo = startMongoDB(port);
            } catch (IOException | InterruptedException ex) {
                Exceptions.chuck(ex);
            }
        }

        void handleOutput(ProcessBuilder pb, String suffix) {
            if (suffix == null) {
                suffix = "";
            }
            if (Boolean.getBoolean("acteur.debug")) {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            } else if (Boolean.getBoolean("mongo.tmplog")) {
                String tmq = System.getProperty("testMethodQname");
                if (tmq == null) {
                    tmq = MongoHarness.class.getSimpleName();
                }
                tmq += "-" + System.currentTimeMillis() + "-" + suffix;
                File tmp = new File(System.getProperty("java.io.tmpdir"));
                File err = new File(tmp, tmq + ".err");
                File out = new File(tmp, tmq + ".err");
                pb.redirectError(err);
                pb.redirectOutput(out);
            } else {
                System.err.println("Discarding mongodb output.  Set system property acteur.debug to true to inherit "
                        + "it, or mongo.tmplog to true to write it to a file in /tmp");
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            }
        }

        public void stop() {
            stop(true);
        }

        public void stop(boolean deleteDbDir) {
            try {
                if (mongo != null) {
                    String mongodExe = System.getProperty("mongo.binary", "mongod");
                    String[] cmd = new String[]{mongodExe, "--dbpath",
                        mongoDir.getAbsolutePath(), "--shutdown", "--port", "" + port};
                    ProcessBuilder pb = new ProcessBuilder().command(cmd);
                    handleOutput(pb, "mongodb-shutdown");
                    try {
                        boolean exited = false;
                        boolean destroyCalled = false;
                        if (!mongodbGreaterThan36) {
                            Process shutdown = pb.start();
                            System.err.println("Try graceful mongodb shutdown " + Arrays.toString(cmd));
                            for (int i = 0; i < 19000; i++) {
                                try {
                                    int exit = shutdown.exitValue();
                                    System.err.println("Shutdown mongodb call exited with " + exit);
                                    break;
                                } catch (IllegalThreadStateException ex) {
                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException ex1) {
                                        Exceptions.printStackTrace(ex1);
                                    }
                                }
                            }
                        } else {
                            destroyCalled = true;
                            mongo.destroy();
                        }
                        System.err.println("Wait for mongodb exit");
                        for (int i = 0; i < 10000; i++) {
                            try {
                                int code = mongo.exitValue();
                                System.err.println("Mongo server exit code " + code);
                                exited = true;
                                mongo = null;
                                return;
                            } catch (IllegalThreadStateException ex) {
//                            System.out.println("Not exited yet; sleep 100ms");
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException ex1) {
                                    Exceptions.printStackTrace(ex1);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (!exited && i > 30) {
//                            System.err.println("Mongodb has not exited; kill it");
                                if (destroyCalled) {
                                    mongo.destroyForcibly();
                                } else {
                                    destroyCalled = true;
                                    mongo.destroy();
                                }
                            }
                            if (!exited && i > 100) {
                                mongo.destroyForcibly();
                            }
                        }
                    } catch (IOException ex) {
                        Exceptions.chuck(ex);
                        mongo = null;
                    }
                    mongo = null;
                }
            } finally {
                if (mongoDir != null && mongoDir.exists()) {
                    try {
                        FileUtils.deltree(mongoDir.toPath());
                    } catch (IOException ex) {
                        Logger.getLogger(MongoHarness.class.getName())
                                .log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        public void start() throws IOException, InterruptedException {
            if (mongo != null) {
                throw new IllegalStateException("MongoDB already started");
            }
            mongo = startMongoDB(port);
        }

        @Override
        public void run() {
            try {
                stop();
            } finally {
                if (mongoDir != null && mongoDir.exists()) {
                    cleanup(mongoDir);
                }
            }
        }

        private File createMongoDir() {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            String fname = System.getProperty("testMethodQname");
            if (fname == null) {
                fname = explicitName;
                if (fname == null) {
                    fname = "mongo-" + System.currentTimeMillis() + "-" + count++;
                }
            } else {
                fname += "-" + System.currentTimeMillis() + "-" + count++;
            }
            File mongoDir = new File(tmp, fname);
            if (!mongoDir.mkdirs()) {
                throw new AssertionError("Could not create " + mongoDir);
            }
            return mongoDir;
        }

        private volatile boolean failed;

        public boolean failed() {
            return failed;
        }

        static boolean macOs() {
            return System.getProperty("os.name", "").contains("Mac OS");
        }

        private String mongodExe() {
            return System.getProperty("mongo.binary", "mongod");
        }

        private MongoVersion version() {
            String mongodExe = mongodExe();
            MongoDaemonVersion verFinder;
            if (mongodExe != null) {
                verFinder = MongoDaemonVersion.forBinary(Paths.get(mongodExe));
            } else {
                verFinder = MongoDaemonVersion.systemPath();
            }
            MongoVersion ver = verFinder.version();
            return ver;
        }

        Process startMongoDB(int port) throws IOException, InterruptedException {
            Checks.nonZero("port", port);
            Checks.nonNegative("port", port);
            System.err.println("Starting mongodb on port " + port + " with data dir " + mongoDir);
            boolean useInMemoryEngine = Boolean.getBoolean("mongo.harness.memory");
            String mongodExe = mongodExe();
            ProcessBuilder pb;
            boolean setCacheSize = Boolean.getBoolean("mongodb.set.cache.size");
            MongoVersion ver = version();
            if (ver.isNone()) {
                System.err.println("COULD NOT FIND THE MONGO VERSION - configuraing cli args blindly");
            }
            System.err.println("Starting mongod " + ver);

            if (useInMemoryEngine) {
                switch (ver.majorVersion()) {
                    case 3:
                        pb = new ProcessBuilder().command(mongodExe,
                                "--storageEngine", "inMemory",
                                "--nounixsocket",
                                "--maxConns", "50",
                                "--port", "" + port);
                        break;
                    case 6:
                        if (!macOs()) {
                            // mongodb-community 6.0.1 from homebrew has broken socket options
                            // for the inMemory storage engine and will crash
                            pb = new ProcessBuilder().command(mongodExe,
                                    "--dbpath", mongoDir.getAbsolutePath(),
                                    "--nojournal",
                                    "--slowms", "5",
                                    "--port", "" + port,
                                    "--maxConns", "50",
                                    "--oplogSize", "1",
                                    "--nounixsocket");
                            break;
                        }
                    default: // v4 and up - needs dbPath for metadata and index generation
                        pb = new ProcessBuilder().command(mongodExe,
                                "--dbpath", mongoDir.getAbsolutePath(),
                                "--storageEngine", "inMemory",
                                "--nounixsocket",
                                "--maxConns", "50",
                                "--port", "" + port);

                }
            } else {
                List<String> cmd;
                switch (ver.majorVersion()) {
                    case 3:
                        cmd = new ArrayList<>(Arrays.asList(
                                mongodExe,
                                "--dbpath", mongoDir.getAbsolutePath(),
                                //                                "--nojournal",
                                "--smallfiles",
                                "-nssize", "1",
                                "--noprealloc",
                                "--slowms", "5",
                                "--port", "" + port,
                                "--maxConns", "50",
                                "--syncdelay", "0",
                                "--oplogSize", "1",
                                "--nounixsocket"));
                        break;
                    case 4:
                        cmd = new ArrayList<>(Arrays.asList(
                                mongodExe,
                                "--dbpath", mongoDir.getAbsolutePath(),
                                "--nojournal",
                                "--slowms", "5",
                                "--port", "" + port,
                                "--maxConns", "50",
                                "--oplogSize", "1",
                                "--nounixsocket"));
                        break;
                    default:
                        cmd = new ArrayList<>(Arrays.asList(
                                mongodExe,
                                "--dbpath", mongoDir.getAbsolutePath(),
                                //                                "--nojournal",
                                "--slowms", "5",
                                "--port", "" + port,
                                "--maxConns", "50",
                                "--oplogSize", "1",
                                "--nounixsocket"));
                }
                // These options are good on 3.x through at least 6.0.1
                if (replicaSet) {
                    cmd.add("--replSet");
                    cmd.add(replSetId);
                }
                if (setCacheSize) {
                    cmd.add("--wiredTigerCacheSizeGB");
                    cmd.add("1");
                }
                cmd.addAll(additionalArgs);
                System.out.println(Strings.join(' ', cmd));
                pb = new ProcessBuilder().command(cmd);
            }
            System.err.println(pb.command());
            handleOutput(pb, "mongodb");

            // XXX instead of sleep, loop trying to connect?
            Process result = pb.start();
            Thread.sleep(CONNECT_WAIT_MILLIS);
            for (int i = 0;; i++) {
                try {
                    Socket s = new Socket("localhost", port);
                    s.close();
                    Thread.sleep(CONNECT_WAIT_MILLIS);
                    break;
                } catch (ConnectException e) {
                    if (i > 1750) {
                        throw new IOException("Could not connect to mongodb "
                                + "after " + i + " attempts.  Assuming it's dead.");
                    }
                    Thread.sleep(i > 1700 ? 400 : i > 1500 ? 250 : i > 1000 ? 125 : 50);
                }
            }
            return result;
        }
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

    /**
     * Determine if starting MongoDB failed (the process exited with non-zero a
     * few seconds after launch). Use this to allow tests to pass when building
     * on a machine which does not have mongodb installed.
     *
     * @return True if mongodb was started and failed for some reason (details
     * will be on system.out)
     */
    public boolean failed() {
        return mongo.failed();
    }

    /**
     * Stop mongodb. This is done automatically on system shutdown - only call
     * this if you want to test the behavior of something when the database is
     * <i>not</i> there for some reason.
     */
    public void stop() {
        mongo.stop();
    }

    /**
     * Start mongodb, if stop has been called. Otehrwise, it is automatically
     * started for you.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void start() throws IOException, InterruptedException {
        mongo.port = port;
        mongo.start();
    }

    /**
     * Get the randomly selected available port we wnat to use
     *
     * @return a port
     */
    public int port() {
        return port;
    }

    /**
     * Use this module in a test to automatically start MongoDB the first time
     * something requests a class related to it for injection. Automatically
     * finds an unused, non-standard port. Inject MongoHarness and call its
     * <code>port()</code> method if you need the port.
     */
    public static class Module extends AbstractModule {

        private final int port;

        public Module() {
            this(-1);
        }

        public Module(int port) {
            this.port = port;
        }

        @Override
        protected void configure() {
            bind(String.class).annotatedWith(Names.named("mongoPort")).toInstance("" + findPort());
            bind(String.class).annotatedWith(Names.named("mongoHost")).toInstance("localhost");
            bind(Init.class).asEagerSingleton();
        }

        private int findPort() {
            if (port > 0) {
                return port;
            }
            Random r = new Random(System.currentTimeMillis());
            int port;
            do {
                // Make sure we're out of the way of a running mongo instance,
                // both the mongo port and the http port
                int startPort = 28002;
                port = r.nextInt(65536 - startPort) + startPort;
            } while (!available(port));
            return port;
        }

        private boolean available(int port) {
            try (ServerSocket ss = new ServerSocket(port)) {
                ss.setReuseAddress(true);
                try (DatagramSocket ds = new DatagramSocket(port)) {
                    ds.setReuseAddress(true);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
    }
}
