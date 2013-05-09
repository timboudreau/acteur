package com.mastfrog.acteur.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.giulius.ShutdownHookRegistry;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Random;

/**
 *
 * @author tim
 */
public class MongoHarness {

    private final File mongoDir;
    private Process mongo;
    private final int port;

    @Inject
    MongoHarness(@Named("mongoPort") int port, ShutdownHookRegistry reg) throws IOException, InterruptedException {
        reg.add(new Shutdown());
        mongoDir = createMongoDir();
        mongo = startMongoDB();
        this.port = port;
    }

    private class Shutdown implements Runnable {

        @Override
        public void run() {
            try {
                if (mongo != null) {
                    mongo.destroy();
                }
            } finally {
                if (mongoDir.exists()) {
                    cleanup(mongoDir);
                }
            }
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

    public void stop() {
        if (mongo != null) {
            mongo.destroy();
        }
    }

    public void start() throws IOException, InterruptedException {
        mongo = startMongoDB();
    }

    public void restart() throws IOException, InterruptedException {
        if (mongo != null) {
            mongo.destroy();
        }
        mongo = startMongoDB();
    }

    private File createMongoDir() {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File mongoDir = new File(tmp, "mongo-" + System.currentTimeMillis());
        if (!mongoDir.mkdirs()) {
            throw new AssertionError("Could not create " + mongoDir);
        }
        return mongoDir;
    }

    private Process startMongoDB() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder().command("mongod", "--dbpath",
                mongoDir.getAbsolutePath(), "--nojournal", "--smallfiles", "-nssize", "1",
                "--noprealloc", "--nohttpinterface", "--slowms", "5", "--port", "" + port);

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        Process result = pb.start();
//        Thread.sleep(2000);
        try {
            int code = result.exitValue();
            System.out.println("MongoDB process exited with " + code);
            return null;
        } catch (IllegalThreadStateException ex) {
            System.out.println("Started MongoDB");
            return result;
        }
    }

    public static class Module extends AbstractModule {

        @Override
        protected void configure() {
            bind(String.class).annotatedWith(Names.named("mongoPort")).toInstance("" + findPort());
            bind(String.class).annotatedWith(Names.named("mongoHost")).toInstance("localhost");
        }

        private int findPort() {
            Random r = new Random(System.currentTimeMillis());
            int port;
            do {
                port = r.nextInt(28002) + 42002;
            } while (!available(port));
            return port;
        }

        private boolean available(int port) {
            ServerSocket ss = null;
            DatagramSocket ds = null;
            try {
                ss = new ServerSocket(port);
                ss.setReuseAddress(true);
                ds = new DatagramSocket(port);
                ds.setReuseAddress(true);
                return true;
            } catch (IOException e) {
            } finally {
                if (ds != null) {
                    ds.close();
                }
                if (ss != null) {
                    try {
                        ss.close();
                    } catch (IOException e) {
                        /* should not be thrown */
                    }
                }
            }
            return false;
        }
    }
}
