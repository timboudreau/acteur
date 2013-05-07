package com.mastfrog.acteur.resources;

import com.mastfrog.acteur.TestHarness;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.netty.http.client.StateType.Closed;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import org.joda.time.DateTime;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith(value = {TestHarness.Module.class},
        iterate = {
            ResourcesApp.ClasspathResourcesModule.class,
            ResourcesApp.FileResourcesModule.class,
            ResourcesApp.MergedResourcesModule.class
        })
public class StaticResourcesTest {

    private static String HELLO_CONTENT = "Test test test\nThis is a test\nIt is like a test too\n";

    @Test
    public void test(TestHarness har, StaticResources resources) throws Throwable {
        DateTime helloLastModified = har.get("hello.txt").go()
                .assertStateSeen(Closed)
                .assertHasContent()
                .assertStatus(OK)
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent(HELLO_CONTENT)
                .getHeader(Headers.LAST_MODIFIED);

        DateTime aLastModified = har.get("another.txt").go()
                .assertStateSeen(Closed)
                .assertStatus(OK)
                .assertHasContent()
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent("This is another file.  It has some data in it.\n")
                .getHeader(Headers.LAST_MODIFIED);

        assertNotNull(helloLastModified);
        assertNotNull(aLastModified);

        har.get("hello.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED);

        har.get("another.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .go().assertStatus(NOT_MODIFIED);

        if (resources instanceof ClasspathResources) {
            // should be server start time since that's all we know
            assertEquals(helloLastModified, aLastModified);
        } else {
            DateTime subLastModified = har.get("sub/subfile.txt").go()
                    .assertStateSeen(Closed)
                    .assertHasContent()
                    .assertStatus(OK)
                    .assertHasHeader(Headers.LAST_MODIFIED.name())
                    .assertHasHeader(Headers.ETAG.name())
                    .assertContent(ResourcesApp.stuff)
                    .getHeader(Headers.LAST_MODIFIED);

            har.get("sub", "subfile.txt")
                    .addHeader(Headers.IF_MODIFIED_SINCE, subLastModified)
                    .go().assertStatus(NOT_MODIFIED);
        }

        String etag = har.get("hello.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED)
                .getHeader(Headers.ETAG);

        String etag2 = har.get("hello.txt")
                .addHeader(Headers.IF_NONE_MATCH, etag)
                .go()
                .assertStatus(NOT_MODIFIED)
                .getHeader(Headers.ETAG);
        assertEquals(etag, etag2);
    }
}
