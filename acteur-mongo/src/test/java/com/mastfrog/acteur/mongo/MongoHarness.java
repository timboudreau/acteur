package com.mastfrog.acteur.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

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
    private static final int CONNECT_WAIT_MILLIS = 500;

    @Inject
    MongoHarness(@Named("mongoPort") int port, Init mongo) throws IOException, InterruptedException {
        this.port = port;
        this.mongo = mongo;
    }

    @Singleton
    static class Init extends MongoInitializer implements Runnable {

        private final File mongoDir;
        private Process mongo;
        private int port;

        @SuppressWarnings("LeakingThisInConstructor")
        @Inject
        public Init(MongoInitializer.Registry registry, ShutdownHookRegistry shutdownHooks) {
            super(registry);
            shutdownHooks.add(this);
            mongoDir = createMongoDir();
        }

        @Override
        protected void onBeforeCreateMongoClient(String host, int port) {
            try {
                this.port = port;
                mongo = startMongoDB(port);
            } catch (IOException | InterruptedException ex) {
                Exceptions.chuck(ex);
            }
        }

        public void stop() {
            if (mongo != null) {
                ProcessBuilder pb = new ProcessBuilder().command("mongod", "--dbpath",
                        mongoDir.getAbsolutePath(), "--shutdown", "--port", "" + port);
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                try {
                    System.out.println("Try graceful mongodb shutdown " + pb);
                    Process shutdown = pb.start();
                    boolean exited = false;
                    for (int i = 0; i < 19000; i++) {
                        try {
                            int exit = shutdown.exitValue();
                            System.out.println("Shutdown mongodb call exited with " + exit);
                            break;
                        } catch (IllegalThreadStateException ex) {
//                            System.out.println("no exit code yet, sleeping");
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException ex1) {
                                Exceptions.printStackTrace(ex1);
                            }
                        }
                    }
                    System.out.println("Wait for mongodb exit");
                    for (int i = 0; i < 10000; i++) {
                        try {
                            int code = mongo.exitValue();
                            System.out.println("Exit code " + code);
                            exited = true;
                            break;
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
                            System.out.println("Mongodb has not exited; kill it");
                            mongo.destroy();
                        }
                    }
                } catch (IOException ex) {
                    Exceptions.chuck(ex);
                    mongo = null;
                }
                mongo = null;
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
                fname = "mongo-" + System.currentTimeMillis() + "-" + count++;
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

        private Process startMongoDB(int port) throws IOException, InterruptedException {
            Checks.nonZero("port", port);
            Checks.nonNegative("port", port);
            System.out.println("Starting mongodb on port " + port + " with data dir " + mongoDir);
            ProcessBuilder pb = new ProcessBuilder().command("mongod", "--dbpath",
                    mongoDir.getAbsolutePath(), "--nojournal", "--smallfiles", "-nssize", "1",
                    "--noprealloc", "--slowms", "5", "--port", "" + port,
                    "--maxConns", "50", /*"--nohttpinterface",*/ "--syncdelay", "0", "--oplogSize", "1");
            System.out.println(pb.command());
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            // XXX instead of sleep, loop trying to connect?
            Process result = pb.start();
            Thread.sleep(CONNECT_WAIT_MILLIS);
            for (int i = 0;; i++) {
                try {
                    Socket s = new Socket("localhost", port);
                    s.close();
                    Thread.sleep(CONNECT_WAIT_MILLIS);;
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

    public static class Module extends AbstractModule {

        @Override
        protected void configure() {
            bind(String.class).annotatedWith(Names.named("mongoPort")).toInstance("" + findPort());
            bind(String.class).annotatedWith(Names.named("mongoHost")).toInstance("localhost");
            bind(Init.class).asEagerSingleton();
        }

        private int findPort() {
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
