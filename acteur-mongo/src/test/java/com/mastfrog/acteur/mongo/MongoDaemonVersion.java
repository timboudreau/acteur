/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.mongo;

import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.READ;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and caches the local mongod version. Used to ensure we don't pass
 * command-line arguments that do not exist in newer versions.
 *
 * @author Tim Boudreau
 */
public final class MongoDaemonVersion {

    static final Pattern VER_PATTERN_1 = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)");
    static final Pattern VER_PATTERN_2 = Pattern.compile("\"version\":\\s\"(\\d+)\\d(\\d+)\\.(\\d+).*?\"");
    private static final MongoDaemonVersion INSTANCE = new MongoDaemonVersion();
    private MongoVersion version;

    private final Path binary;

    private MongoDaemonVersion(Path binary) {
        this.binary = binary;
    }

    private MongoDaemonVersion() {
        this(null);
    }

    public static MongoDaemonVersion systemPath() {
        return INSTANCE;
    }

    public static MongoDaemonVersion forBinary(Path binary) {
        if (!Files.exists(binary) || !Files.isExecutable(binary)) {
            return INSTANCE;
        }
        return new MongoDaemonVersion(binary);
    }

    public boolean purge() {
        Path cf = cacheFile();
        if (Files.exists(cf)) {
            try {
                Files.delete(cf);
                return true;
            } catch (IOException ex) {
                Logger.getLogger(MongoDaemonVersion.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return false;
    }

    private Path cacheFile() {
        if (binary == null) {
            return Paths.get(System.getProperty("java.io.tmpdir"))
                    .resolve("local.mongod.version");
        } else {
            String nm = binary.toString().replace(File.separatorChar, '_');
            return Paths.get(System.getProperty("java.io.tmpdir"))
                    .resolve("local.mongod.version-" + nm);
        }
    }

    public synchronized MongoVersion version() {
        if (version != null && !version.isNone()) {
            return version;
        }
        Path cacheFile = cacheFile();
        System.out.println("USE CACHE FILE " + cacheFile);

        MongoVersion result = readVersionCacheFile(cacheFile);
        if (result != null) {
            System.out.println("use cached version");
            return version = result;
        }

        String cmd = binary == null ? "mongod" : binary.toString();
        ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
        pb.redirectOutput(cacheFile.toFile());
        pb.directory(cacheFile.getParent().toFile());
        Process proc;
        try {
            proc = pb.start();
            while (proc.isAlive()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(MongoHarness.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return version = readVersionCacheFile(cacheFile);
        } catch (IOException ex) {
            Logger.getLogger(MongoHarness.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Fall through");
        return MongoVersion.NONE;
    }

    private static MongoVersion readVersionCacheFile(Path cacheFile) {
        if (Files.exists(cacheFile)) {
            try ( InputStream in = Files.newInputStream(cacheFile, READ)) {
                String s = Streams.readAsciiString(in);
                String[] lines = Strings.split('\n', s.trim());
                for (String ln : lines) {
                    Matcher m = VER_PATTERN_1.matcher(ln);
                    boolean found = m.find();
                    if (!found) {
                        m = VER_PATTERN_2.matcher(ln);
                        found = m.find();
                    }
                    if (found) {
                        return new MongoVersion(Integer.parseInt(m.group(1)),
                                Integer.parseInt(m.group(2)),
                                Integer.parseInt(m.group(3)));
                    }
                }
            } catch (IOException | NumberFormatException ex) {
                Logger.getLogger(MongoHarness.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    public static class MongoVersion implements Comparable<MongoVersion> {

        private final int major;
        private final int minor;
        private final int dot;

        public static final MongoVersion NONE = new MongoVersion(-1, -1, -1);

        public MongoVersion(int major, int minor, int dot) {
            this.major = major;
            this.minor = minor;
            this.dot = dot;
        }

        public boolean isNone() {
            return equals(NONE);
        }

        public int majorVersion() {
            return major;
        }

        public int minorVersion() {
            return minor;
        }

        public int dotVersion() {
            return dot;
        }

        @Override
        public String toString() {
            if (isNone()) {
                return "-UNKNOWN-VERSION-";
            }
            return major + "." + minor + "." + dot;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || o.getClass() != MongoVersion.class) {
                return false;
            }
            MongoVersion v = (MongoVersion) o;
            return major == v.major && minor == v.minor && dot == v.dot;
        }

        @Override
        public int hashCode() {
            return (71 * major) + (67 * minor) + (3 * dot);
        }

        @Override
        public int compareTo(MongoVersion o) {
            int result = Integer.compare(major, o.major);
            if (result == 0) {
                result = Integer.compare(minor, o.minor);
            }
            if (result == 0) {
                result = Integer.compare(dot, o.dot);
            }
            return result;
        }
    }

    public static void main(String[] args) {
        MongoVersion ver = MongoDaemonVersion.systemPath().version();

        System.out.println("GOT VERSION " + ver);
    }

}
