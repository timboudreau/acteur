package com.mastfrog.acteur;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.netty.http.client.StateType.Closed;
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
@TestWith(CompApp.Module.class)
public class ComprehensiveTest {

    private static final int TIMEOUT_SECONDS = 120;
    private static final int TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1000;
    private final Duration TIMEOUT = Duration.ofSeconds(TIMEOUT_SECONDS);


    @Test(timeout = TIMEOUT_MILLIS)
    public void testEcho(TestHarness harness) throws Throwable {
        harness.post("echo").log().setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(TIMEOUT).go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("Echo this back to me");
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testIter(TestHarness harness) throws Throwable {
        harness.get("iter").log().addQueryPair("iters", "5").setTimeout(TIMEOUT).go()
                .assertContent(iter("Iteration", 5))
                .assertCode(200)
                .throwIfError();
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testIter2(TestHarness harness) throws Throwable {
        harness.get("iter").log().addQueryPair("iters", "7")
                .addQueryPair("msg", "Hello ").setTimeout(TIMEOUT).go()
                .assertCode(200)
                .assertContent(iter("Hello", 7))
                .throwIfError();
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testDeferred(TestHarness harness) throws Throwable {
        harness.get("deferred").log().setTimeout(TIMEOUT).go()
                .throwIfError()
                .assertContent("I guess it's okay now")
                .assertStatus(OK);
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testNothing(TestHarness harness) throws Throwable {
        harness.get("nothing").log().setTimeout(TIMEOUT).go()
                .throwIfError()
                .assertStatus(HttpResponseStatus.PAYMENT_REQUIRED)
                .assertStateSeen(Closed);
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testBranch1(TestHarness harness) throws Throwable {
        harness.get("branch").log().setTimeout(TIMEOUT).addQueryPair("a", "true").go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("A");
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testBranch2(TestHarness harness) throws Throwable {
        harness.get("branch").log().setTimeout(TIMEOUT).go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("B");
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testEcho2(TestHarness harness) throws Throwable {
        harness.post("echo").log().setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(TIMEOUT).go()
                .assertStatus(OK)
                .assertContent("Echo this back to me");

    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testDynamicActeur(TestHarness harness) throws Throwable {
        System.out.println("J");
        harness.get("dyn").log().setTimeout(TIMEOUT).go()
                .assertStatus(OK)
                .assertContent("Dynamic acteur");

    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void testUnchunked(TestHarness harness) throws Exception, Throwable {
        harness.get("unchunked").log().addQueryPair("iters", "7")
                .setTimeout(TIMEOUT)
                .go()
                .assertCode(200)
                .assertContent(iter("Iteration", 7))
                .throwIfError()
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
