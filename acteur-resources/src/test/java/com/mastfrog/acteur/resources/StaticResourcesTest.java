/*
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur.resources;

import com.mastfrog.acteur.headers.ByteRanges;
import static com.mastfrog.acteur.headers.Headers.ACCEPT_RANGES;
import static com.mastfrog.acteur.headers.Headers.CONTENT_RANGE;
import static com.mastfrog.acteur.headers.Headers.ETAG;
import static com.mastfrog.acteur.headers.Headers.IF_MODIFIED_SINCE;
import static com.mastfrog.acteur.headers.Headers.IF_NONE_MATCH;
import static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED;
import static com.mastfrog.acteur.headers.Headers.RANGE;
import com.mastfrog.acteur.resources.ResourcesApp.Compression;
import static com.mastfrog.acteur.resources.ResourcesApp.FILES;
import com.mastfrog.acteur.resources.ResourcesApp.NoCompression;
import static com.mastfrog.acteur.resources.ResourcesApp.STUFF;
import static com.mastfrog.acteur.resources.ResourcesApp.tmpdir;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.netty.http.client.StateType.FullContentReceived;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.Streams;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.PARTIAL_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.ZonedDateTime;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith(value = {TestHarnessModule.class},
        iterate = {
            ResourcesApp.DynFileResourcesModule.class,
            ResourcesApp.ClasspathResourcesModule.class,
            ResourcesApp.FileResourcesModule.class,
            ResourcesApp.FileResourcesModule2.class,
            ResourcesApp.MergedResourcesModule.class
        })
public class StaticResourcesTest {

    private static final String HELLO_CONTENT = "Test test test\nThis is a test\nIt is like a test too\n";

    @BeforeClass
    public static void setUpTempFiles() throws Exception {
        for (String file : FILES) {
            File f = new File(tmpdir, file);
            assertTrue(f.createNewFile());
            try (InputStream in = ResourcesApp.class.getResourceAsStream(file)) {
                assertNotNull(file, in);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    Streams.copy(in, out, 128);
                }
            }
        }
        File sub = new File(tmpdir, "sub");
        assertTrue(sub.mkdirs());
        File subfile = new File(sub, "subfile.txt");
        assertTrue(subfile.createNewFile());
        Streams.writeString(STUFF, subfile);
    }

    @AfterClass
    public static void cleanUpTempFiles() throws Exception {
        File f = ResourcesApp.tmpdir;
        if (f != null && f.exists() && f.isDirectory()) {
            java.nio.file.Path p = f.toPath();
            Files.walkFileTree(p, new FileVisitor<java.nio.file.Path>() {
                @Override
                public FileVisitResult preVisitDirectory(java.nio.file.Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(java.nio.file.Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            Files.deleteIfExists(p);
        }
    }

    @TestWith(iterate = {Compression.class, NoCompression.class})
    public void test(TestHarness har, StaticResources resources) throws Throwable {
        ZonedDateTime helloLastModified;
        ZonedDateTime aLastModified;
        helloLastModified = har.get("static/hello.txt")
                .setTimeout(Duration.ofMinutes(1))
                .go()
                .assertStatus(OK)
                .assertHasHeader(LAST_MODIFIED.name())
                .assertHasHeader(ETAG.name())
                .assertContent(HELLO_CONTENT)
                .getHeader(LAST_MODIFIED);

        har.get("static/hello.txt")
                .setTimeout(Duration.ofMinutes(1))
                .go()
                .assertHasContent()
                .assertStatus(OK)
                .assertHasHeader(LAST_MODIFIED.name())
                .assertHasHeader(ETAG.name())
                .assertContent(HELLO_CONTENT)
                .getHeader(LAST_MODIFIED);

        aLastModified = har.get("static/another.txt")
                .setTimeout(Duration.ofMinutes(1))
                .go()
                .await()
                .assertStatus(OK)
                .assertHasContent()
                .assertHasHeader(LAST_MODIFIED.name())
                .assertHasHeader(ETAG.name())
                .assertContent("This is another file.  It has some data in it.\n")
                .getHeader(LAST_MODIFIED);

        har.get("foo/bar").go().assertCode(404);

        assertNotNull(helloLastModified);
        assertNotNull(aLastModified);

        har.get("static/hello.txt")
                .addHeader(IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .addHeader(IF_MODIFIED_SINCE, aLastModified)
                .go().assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .addHeader(IF_MODIFIED_SINCE, helloLastModified.plus(Duration.ofDays(1)))
                .go().await().assertStatus(NOT_MODIFIED);

        CallResult cb = har.get("static/another.txt")
                .addHeader(IF_MODIFIED_SINCE, helloLastModified.minus(Duration.ofDays(1)))
                .go().await().assertStatus(OK);

        if (resources instanceof DynamicFileResources || cb.getHeader(ACCEPT_RANGES) != null) {

            har.get("static/another.txt")
                    .addHeader(RANGE, ByteRanges.of(1, 3))
                    .go()
                    .await()
                    .assertStatus(PARTIAL_CONTENT)
                    .assertHasHeader(CONTENT_RANGE)
                    .assertContent("his");

            har.get("static/another.txt")
                    .addHeader(RANGE, new ByteRanges(4))
                    .setTimeout(Duration.ofMinutes(1))
                    .go()
                    .await()
                    .assertStatus(PARTIAL_CONTENT)
                    .assertHasHeader(CONTENT_RANGE)
                    .assertContent(" is another file.  It has some data in it.\n");

            har.get("static/another.txt")
                    .addHeader(RANGE, ByteRanges.of(0, 3))
                    .go().await()
                    .assertStatus(PARTIAL_CONTENT)
                    .assertHasHeader(CONTENT_RANGE)
                    .assertContent("This");

            har.get("static/another.txt")
                    .addHeader(RANGE, ByteRanges.of(5, 14))
                    .go().await()
                    .assertStatus(PARTIAL_CONTENT)
                    .assertHasHeader(CONTENT_RANGE)
                    .assertContent("is another");

            har.get("static/another.txt")
                    .addHeader(RANGE, ByteRanges.of(0, 0))
                    .go().await()
                    .assertStatus(PARTIAL_CONTENT)
                    .assertHasHeader(CONTENT_RANGE)
                    .assertContent("T");

            har.get("static/another.txt")
                    .addHeader(RANGE.toStringHeader(), "bytes=12-1")
                    .go().await()
                    .assertStatus(BAD_REQUEST);

            har.get("static/another.txt")
                    .addHeader(RANGE.toStringHeader(), "bytes=3000-3003")
                    .go().await()
                    .assertStatus(REQUESTED_RANGE_NOT_SATISFIABLE);


            ByteRanges compound = ByteRanges.builder().add(5, 15).add(25, 30).build();
            har.get("static/another.txt")
                    .addHeader(RANGE, compound)
                    .go().await().assertStatus(NOT_IMPLEMENTED);
        }
        if (resources instanceof ClasspathResources) {
            // should be server start time since that's all we know
            assertEquals(helloLastModified, aLastModified);
        } else {
            ZonedDateTime subLastModified = har.get("static/sub/subfile.txt")
                    .setTimeout(Duration.ofMinutes(1))
                    .go()
                    .await()
                    .assertStateSeen(FullContentReceived)
                    .assertHasContent()
                    .assertStatus(OK)
                    .assertHasHeader(LAST_MODIFIED.name())
                    .assertHasHeader(ETAG.name())
                    .assertContent(ResourcesApp.STUFF)
                    .getHeader(LAST_MODIFIED);

            har.get("static/sub/subfile.txt")
                    .addHeader(IF_MODIFIED_SINCE, subLastModified)
                    .go().assertStatus(NOT_MODIFIED);
        }

        CharSequence etag = har.get("static/hello.txt")
                .addHeader(IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED)
                .getHeader(ETAG);

        CharSequence etag2 = har.get("static/hello.txt")
                .addHeader(IF_NONE_MATCH, etag)
                .go()
                .assertStatus(NOT_MODIFIED)
                .getHeader(ETAG);
        assertEquals(GuiceRunner.currentMethodName(), etag, etag2);

        har.get("static/hello.txt")
                .addHeader(IF_NONE_MATCH, "garbage")
                .go()
                .assertStatus(OK);
    }
}
