package com.mastfrog.acteur.resources;

import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.resources.ResourcesApp.FILES;
import static com.mastfrog.acteur.resources.ResourcesApp.STUFF;
import static com.mastfrog.acteur.resources.ResourcesApp.tmpdir;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.netty.http.client.StateType.FullContentReceived;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.Streams;
import com.mastfrog.util.time.TimeUtil;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
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
import org.junit.Test;
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

    private static String HELLO_CONTENT = "Test test test\nThis is a test\nIt is like a test too\n";

    @BeforeClass
    public static void setUpTempFiles() throws Exception {
        System.out.println("RESOURCES IN " + tmpdir);
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
                    System.out.println("Delete " + file);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(java.nio.file.Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
                    System.out.println("rmdir " + dir);
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("rmdir " + p);
            Files.deleteIfExists(p);
        }
    }

    @Test
    public void test(TestHarness har, StaticResources resources) throws Throwable {
        ZonedDateTime helloLastModified;
        ZonedDateTime aLastModified;
        helloLastModified = har.get("static/hello.txt").log().go()
                .assertHasContent()
                .assertStatus(OK)
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent(HELLO_CONTENT)
                .getHeader(Headers.LAST_MODIFIED);

        ZonedDateTime helloLastModified2 = har.get("static/hello.txt").go()
                .assertHasContent()
                .assertStatus(OK)
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent(HELLO_CONTENT)
                .getHeader(Headers.LAST_MODIFIED);

        aLastModified = har.get("static/another.txt").go()
                .await()
                .assertStatus(OK)
                .assertHasContent()
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent("This is another file.  It has some data in it.\n")
                .getHeader(Headers.LAST_MODIFIED);

        har.get("foo/bar").go().assertCode(404);

        assertNotNull(helloLastModified);
        assertNotNull(aLastModified);
        
        System.out.println("HELLO LAST MODIFIED: " + TimeUtil.toHttpHeaderFormat(helloLastModified));

        har.get("static/hello.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .log()
                .go()
                .assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, aLastModified)
                .go().assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .log()
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified.plus(Duration.ofDays(1)))
                .go().await().assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified.minus(Duration.ofDays(1)))
                .go().await().assertStatus(OK);

        if (resources instanceof ClasspathResources) {
            // should be server start time since that's all we know
            assertEquals(helloLastModified, aLastModified);
        } else {
            ZonedDateTime subLastModified = har.get("static/sub/subfile.txt").go()
                    .assertStateSeen(FullContentReceived)
                    .assertHasContent()
                    .assertStatus(OK)
                    .assertHasHeader(Headers.LAST_MODIFIED.name())
                    .assertHasHeader(Headers.ETAG.name())
                    .assertContent(ResourcesApp.STUFF)
                    .getHeader(Headers.LAST_MODIFIED);

            har.get("static/sub/subfile.txt")
                    .addHeader(Headers.IF_MODIFIED_SINCE, subLastModified)
                    .go().assertStatus(NOT_MODIFIED);
        }

        CharSequence etag = har.get("static/hello.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED)
                .getHeader(Headers.ETAG);

        CharSequence etag2 = har.get("static/hello.txt")
                .addHeader(Headers.IF_NONE_MATCH, etag)
                .go()
                .assertStatus(NOT_MODIFIED)
                .getHeader(Headers.ETAG);
        assertEquals(etag, etag2);

        har.get("static/hello.txt")
                .addHeader(Headers.IF_NONE_MATCH, "garbage")
                .go()
                .assertStatus(OK);
    }
}
