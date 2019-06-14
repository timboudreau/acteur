package com.mastfrog.acteur;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.mastfrog.acteur.headers.Headers.CONNECTION;
import static com.mastfrog.acteur.util.Connection.close;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.netty.http.test.harness.TestHarness;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith({CompApp.Module.class, SilentRequestLogger.class})
public class ComprehensiveTest {

    private static final int TIMEOUT_SECONDS = 120;
    private static final int TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1000;
    private final Duration TIMEOUT = Duration.ofSeconds(TIMEOUT_SECONDS);

    @Test(timeout = TIMEOUT_MILLIS)
    public void testEcho(TestHarness harness) throws Throwable {
        harness.post("echo").setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(TIMEOUT).go()
                .assertStatus(OK)
                .assertContent("Echo this back to me");
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testIter(TestHarness harness) throws Throwable {
        harness.get("iter").addQueryPair("iters", "5").setTimeout(TIMEOUT).go()
                .assertContent(iter("Iteration", 5))
                .assertCode(200);
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testIter2(TestHarness harness) throws Throwable {
        harness.get("iter").addQueryPair("iters", "7")
                .addQueryPair("msg", "Hello ").setTimeout(TIMEOUT).go()
                .assertCode(200)
                .assertContent(iter("Hello", 7));
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testDeferred(TestHarness harness) throws Throwable {
        harness.get("deferred").setTimeout(TIMEOUT).go()
                .assertContent("I guess it's okay now")
                .assertStatus(OK);
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testNothing(TestHarness harness) throws Throwable {
        harness.get("nothing")
                .addHeader(CONNECTION, close)
                .setTimeout(TIMEOUT)
                .go()
                .assertStatus(HttpResponseStatus.PAYMENT_REQUIRED)
                .assertStateSeen(StateType.ContentReceived);
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testBranch1(TestHarness harness) throws Throwable {
        harness.get("branch").setTimeout(TIMEOUT).addQueryPair("a", "true").go()
                .assertStatus(OK)                    .assertContent("A");
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testBranch2(TestHarness harness) throws Throwable {
        harness.get("branch").setTimeout(TIMEOUT).go()
                .assertStatus(OK)
                .assertContent("B");
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testEcho2(TestHarness harness) throws Throwable {
        harness.post("echo").setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(TIMEOUT).go()
                .assertStatus(OK)
                .await()
                .assertContent("Echo this back to me");

    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testDynamicActeur(TestHarness harness) throws Throwable {
        harness.get("dyn").setTimeout(TIMEOUT).go()
                .assertStatus(OK)
                .assertContent("Dynamic acteur");

    }

    // @Test(timeout = TIMEOUT_MILLIS)
    public void testUnchunked(TestHarness harness) throws Exception, Throwable {
        harness.get("unchunked").addQueryPair("iters", "7")
                .setTimeout(TIMEOUT)
                .go()
                .assertCode(200)
                .assertContent(iter("Iteration", 7))
                .await();
    }

    private String iter(String msg, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= count; i++) {
            sb.append(msg).append(" ").append(i).append("\n");
        }
        return sb.toString();
    }
}
