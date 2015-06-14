package com.mastfrog.acteur;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.netty.http.client.StateType.Closed;
import com.mastfrog.netty.http.test.harness.TestHarness;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith(CompApp.Module.class)
public class ComprehensiveTest {

    @Test(timeout = 20000)
    public void testGets(TestHarness harness) throws Exception, Throwable {
        System.out.println("A");
        harness.post("echo").log().setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(Duration.standardSeconds(30)).go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("Echo this back to me");

        harness.get("iter").log().addQueryPair("iters", "5").setTimeout(Duration.standardSeconds(30)).go()
                .assertContent(iter("Iteration", 5))
                .assertCode(200)
                .throwIfError();

        harness.get("iter").log().addQueryPair("iters", "7")
                .addQueryPair("msg", "Hello ").setTimeout(Duration.standardSeconds(30)).go()
                .assertCode(200)
                .assertContent(iter("Hello", 7))
                .throwIfError();

        System.out.println("B");
        harness.get("deferred").log().setTimeout(Duration.standardSeconds(10)).go()
                .throwIfError()
                .assertContent("I guess it's okay now")
                .assertStatus(OK);

        System.out.println("C");
        harness.get("nothing").log().setTimeout(Duration.standardSeconds(39)).go()
                .throwIfError()
                .assertStatus(HttpResponseStatus.PAYMENT_REQUIRED)
                .assertStateSeen(Closed);

        System.out.println("D");
        harness.get("branch").log().setTimeout(Duration.standardSeconds(50)).addQueryPair("a", "true").go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("A");

        System.out.println("E");
        harness.get("branch").log().setTimeout(Duration.standardSeconds(50)).go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("B");

        harness.get("unchunked").log().addQueryPair("iters", "7")
                .setTimeout(Duration.standardSeconds(20))
                .go()
                .assertCode(200)
                .assertContent(iter("Iteration", 7))
                .throwIfError()
                .await()
                ;

        System.out.println("A");
        harness.post("echo").log().setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(Duration.standardSeconds(30)).go()
                .assertStatus(OK)
                .assertContent("Echo this back to me");

        System.out.println("F");
        harness.get("fail").log().setTimeout(Duration.standardSeconds(50)).go()
                .assertStatus(CONFLICT)
                .assertContent("Hoober");

        harness.get("dyn").log().setTimeout(Duration.standardSeconds(10)).go()
                .assertStatus(OK)
                .assertContent("Dynamic acteur");
    }

    private String iter(String msg, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= count; i++) {
            sb.append(msg).append(" ").append(i).append("\n");
        }
        return sb.toString();
    }
}
