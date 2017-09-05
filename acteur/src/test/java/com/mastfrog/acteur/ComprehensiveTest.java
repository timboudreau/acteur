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

    @Test(timeout = 18000)
    public void testEcho(TestHarness harness) throws Throwable {
        harness.post("echo").log().setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(Duration.ofSeconds(60)).go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("Echo this back to me");
    }

    @Test(timeout = 18000)
    public void testIter(TestHarness harness) throws Throwable {
        harness.get("iter").log().addQueryPair("iters", "5").setTimeout(Duration.ofSeconds(60)).go()
                .assertContent(iter("Iteration", 5))
                .assertCode(200)
                .throwIfError();
    }

    @Test(timeout = 18000)
    public void testIter2(TestHarness harness) throws Throwable {
        harness.get("iter").log().addQueryPair("iters", "7")
                .addQueryPair("msg", "Hello ").setTimeout(Duration.ofSeconds(60)).go()
                .assertCode(200)
                .assertContent(iter("Hello", 7))
                .throwIfError();
    }

    @Test(timeout = 18000)
    public void testDeferred(TestHarness harness) throws Throwable {
        harness.get("deferred").log().setTimeout(Duration.ofSeconds(60)).go()
                .throwIfError()
                .assertContent("I guess it's okay now")
                .assertStatus(OK);
    }

    @Test(timeout = 18000)
    public void testNothing(TestHarness harness) throws Throwable {
        harness.get("nothing").log().setTimeout(Duration.ofSeconds(60)).go()
                .throwIfError()
                .assertStatus(HttpResponseStatus.PAYMENT_REQUIRED)
                .assertStateSeen(Closed);
    }

    @Test(timeout = 18000)
    public void testBranch1(TestHarness harness) throws Throwable {
        harness.get("branch").log().setTimeout(Duration.ofSeconds(60)).addQueryPair("a", "true").go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("A");
    }

    @Test(timeout = 18000)
    public void testBranch2(TestHarness harness) throws Throwable {
        harness.get("branch").log().setTimeout(Duration.ofSeconds(60)).go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("B");
    }

    @Test(timeout = 18000)
    public void testEcho2(TestHarness harness) throws Throwable {
        harness.post("echo").log().setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(Duration.ofSeconds(60)).go()
                .assertStatus(OK)
                .assertContent("Echo this back to me");

    }

    @Test(timeout = 18000)
    public void testDynamicActeur(TestHarness harness) throws Throwable {
        System.out.println("J");
        harness.get("dyn").log().setTimeout(Duration.ofSeconds(60)).go()
                .assertStatus(OK)
                .assertContent("Dynamic acteur");

    }

    @Test(timeout = 18000)
    public void testUnchunked(TestHarness harness) throws Exception, Throwable {
        harness.get("unchunked").log().addQueryPair("iters", "7")
                .setTimeout(Duration.ofSeconds(60))
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
