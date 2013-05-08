package com.mastfrog.acteur;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.netty.http.client.StateType.Closed;
import com.mastfrog.netty.http.test.harness.TestHarness;
import io.netty.handler.codec.http.HttpResponseStatus;
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
public class Comprehensive {

    @Test
    public void testGets(TestHarness harness) throws Exception, Throwable {
        harness.get("iter").addQueryPair("iters", "5").go()
                .assertContent(iter("Iteration", 5))
                .assertCode(200)
                .throwIfError();

        harness.get("iter").addQueryPair("iters", "7")
                .addQueryPair("msg", "Hello ").go()
                .assertCode(200)
                .assertContent(iter("Hello", 7))
                .throwIfError();

        harness.get("unchunked").addQueryPair("iters", "7").go()
                .assertCode(200)
                .assertContent(iter("Iteration", 7))
                .throwIfError();

        harness.post("echo").setBody("Echo this back to me", PLAIN_TEXT_UTF_8)
                .setTimeout(Duration.standardSeconds(30)).go()
                .throwIfError()
                .assertStatus(OK)
                .assertContent("Echo this back to me");

        harness.get("deferred").setTimeout(Duration.standardSeconds(10)).go()
                .throwIfError()
                .assertContent("I guess it's okay now")
                .assertStatus(OK);
        
        harness.get("nothing").setTimeout(Duration.standardSeconds(39)).go()
                .throwIfError()
                .await()
                .assertStatus(HttpResponseStatus.PAYMENT_REQUIRED)
                .assertStateSeen(Closed);
                
    }

    private String iter(String msg, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= count; i++) {
            sb.append(msg).append(" ").append(i).append("\n");
        }
        return sb.toString();
    }
}
