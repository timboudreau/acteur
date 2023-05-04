/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.acteur;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.output.ArrayFormatting;
import com.mastfrog.acteur.output.ObjectStreamActeur;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import static com.mastfrog.mime.MimeType.JSON_UTF_8;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.mastfrog.util.file.FileUtils;
import static com.mastfrog.util.preconditions.Exceptions.chuck;
import com.mastfrog.util.strings.RandomStrings;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({ObjectStreamActeurTest.M.class, HttpTestHarnessModule.class, SilentRequestLogger.class})
public class ObjectStreamActeurTest {

    @Test
    @Timeout(30)
    public void testStream(HttpHarness harn) throws IOException {
        Thing[] l = harn.get("stream").asserting(a -> {
            a.assertOk().assertHasBody()
                    // Because we only know if we're going over the batch size once
                    // we *have* an object that will push the batch over it, the
                    // actual maximum chunk size is, worst case, the batch size
                    // PLUS the size of the item in the stream with the largest
                    // serialized representation
                    .assertChunk("Test chunk size roughly within range", chunk
                            -> {
                        return chunk.remaining() <= 532;
                    })
                    .assertBody(b -> {
                        return !b.isEmpty() && b.startsWith("[{\"");
                    });
        }).assertAllSucceeded().get(Thing[].class);
        assertEquals(THINGS, asList(l), "Payload does not match");
        harn.rethrowServerErrors();
    }

    @Test
    @Timeout(30)
    public void testStreamWithAlternateFormatting(HttpHarness harn) throws IOException {
        HttpResponse<String> resp = harn.get("weirdstream").asserting(a -> {
            a.assertCreated().assertHasBody()
                    .assertBody(b -> {
                        return !b.isEmpty() && b.startsWith(">>{\"");
                    });
        }).assertAllSucceeded().get();

        String body = resp.body();
        assertEquals(weirdFormattingString(), body, "Payload does not match");
        harn.rethrowServerErrors();
    }

    @Test
    @Timeout(30)
    public void testDirectoryListing(HttpHarness harn, DirectoryOfStuff stuff, ObjectMapper mapper) throws IOException {
        HttpResponse<String> resp = harn.get("dir").asserting(a -> {
            a.assertOk().assertPayload(bytes -> {
                try {
                    java.nio.file.Path[] paths = mapper.readValue(bytes, java.nio.file.Path[].class);
                    Set<java.nio.file.Path> got = new LinkedHashSet<>(asList(paths));
                    assertEquals(stuff.files(), got, "Set of paths does not match");
                } catch (IOException ex) {
                    return chuck(ex);
                }
                return true;
            });
        }).assertAllSucceeded().get();
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(A.class));
        }
    }

    @SuppressWarnings("deprecation")
    @ImplicitBindings({Stream.class})
    static class A extends Application {

        A() {
            add(P.class);
            add(Q.class);
            add(R.class);
        }
    }

    static class P extends Page {

        @Inject
        P(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^stream$"));
            add(S.class);
            add(ObjectStreamActeur.class);
        }
    }

    static class Q extends Page {

        @Inject
        Q(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^weirdstream$"));
            add(T.class);
            add(ObjectStreamActeur.class);
        }
    }

    static class R extends Page {

        @Inject
        R(ActeurFactory f) {
            add(f.matchMethods(GET));
            add(f.matchPath("^dir$"));
            add(DirLister.class);
            add(ObjectStreamActeur.class);
        }
    }

    static class S extends Acteur {

        S() {
            next(THINGS.stream(), ArrayFormatting.JSON);
        }
    }

    static class T extends Acteur {

        T() {
            next(THINGS.stream(),
                    ArrayFormatting.JSON
                            .withOpeningDelimiter(">>")
                            .withClosingDelimiter("<<")
                            .withInterItemDelimiter("")
                            .withSuccessStatus(HttpResponseStatus.CREATED)
                            .withBatchBytes(111)
            );
        }
    }

    static class DirLister extends Acteur {

        @Inject
        DirLister(DirectoryOfStuff stuff) throws IOException {
            add(CONTENT_TYPE, JSON_UTF_8);
            next(stuff.listing(), ArrayFormatting.JSON);
        }
    }

    static String weirdFormattingString() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder sb = new StringBuilder();
        String s = mapper.writeValueAsString(THINGS).replaceAll("\\s+", "");
        s = s.substring(1, s.length() - 1).replaceAll("\\}.\\{", "}{");
        return ">>" + s + "<<";
    }

    @Singleton
    static final class DirectoryOfStuff implements ThrowingRunnable {

        final java.nio.file.Path dir;
        final Set<java.nio.file.Path> files = new LinkedHashSet<>();

        @Inject
        DirectoryOfStuff(ShutdownHookRegistry reg) throws IOException {
            try {
                this.dir = FileUtils.newTempDir(ObjectStreamActeurTest.class.getSimpleName());
                for (int i = 0; i < 40; i++) {
                    java.nio.file.Path p = dir.resolve("file-" + i + ".txt");
                    Files.createFile(p);
                    files.add(p);
                }
            } finally {
                reg.addThrowing(this);
            }
        }

        public Set<java.nio.file.Path> files() {
            return unmodifiableSet(files);
        }

        @Override
        public void run() throws Exception {
            if (dir != null) {
                FileUtils.deltree(dir);
            }
        }

        public Stream<java.nio.file.Path> listing() throws IOException {
            return Files.list(dir);
        }

    }

    static final class Thing {

        public final int num;
        public final String val;

        @JsonCreator
         Thing(@JsonProperty("num") int num, @JsonProperty("val") String val) {
            this.num = num;
            this.val = val;
        }

        @Override
        public String toString() {
            return "{ \"num\" : " + num + ", \"val : \"" + val + "\"}";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 43 * hash + this.num;
            hash = 43 * hash + Objects.hashCode(this.val);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Thing other = (Thing) obj;
            if (this.num != other.num) {
                return false;
            }
            return Objects.equals(this.val, other.val);
        }
    }

    static final List<Thing> THINGS = new ArrayList<>();

    static {
        Random rnd = new Random(12_345);
        RandomStrings rs = new RandomStrings();
        for (int i = 0; i < 384; i++) {
            String s = rs.get(7);
            THINGS.add(new Thing(i, s));
        }
    }

}
