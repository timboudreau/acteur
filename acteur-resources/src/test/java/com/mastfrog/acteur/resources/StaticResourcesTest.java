package com.mastfrog.acteur.resources;

import com.mastfrog.acteur.util.Headers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.netty.http.client.StateType.Closed;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith(value = {TestHarnessModule.class},
        iterate = {
            ResourcesApp.ClasspathResourcesModule.class,
            ResourcesApp.FileResourcesModule.class,
            ResourcesApp.MergedResourcesModule.class
        })
public class StaticResourcesTest {

    private static String HELLO_CONTENT = "Test test test\nThis is a test\nIt is like a test too\n";

    @Test
    public void test(TestHarness har, StaticResources resources) throws Throwable {
        DateTime helloLastModified;
        DateTime aLastModified;
        helloLastModified = har.get("static/hello.txt").go()
                .assertHasContent()
                .assertStatus(OK)
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent(HELLO_CONTENT)
                .getHeader(Headers.LAST_MODIFIED);

        DateTime helloLastModified2 = har.get("static/hello.txt").go()
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

        har.get("static/hello.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, aLastModified)
                .go().assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .log()
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified.plus(Duration.standardDays(1)))
                .go().await().assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified.minus(Duration.standardDays(1)))
                .go().await().assertStatus(OK);

        if (resources instanceof ClasspathResources) {
            // should be server start time since that's all we know
            assertEquals(helloLastModified, aLastModified);
        } else {
            DateTime subLastModified = har.get("static/sub/subfile.txt").go()
                    .assertStateSeen(Closed)
                    .assertHasContent()
                    .assertStatus(OK)
                    .assertHasHeader(Headers.LAST_MODIFIED.name())
                    .assertHasHeader(Headers.ETAG.name())
                    .assertContent(ResourcesApp.stuff)
                    .getHeader(Headers.LAST_MODIFIED);

            har.get("static/sub/subfile.txt")
                    .addHeader(Headers.IF_MODIFIED_SINCE, subLastModified)
                    .go().assertStatus(NOT_MODIFIED);
        }

        String etag = har.get("static/hello.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED)
                .getHeader(Headers.ETAG);

        String etag2 = har.get("static/hello.txt")
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
