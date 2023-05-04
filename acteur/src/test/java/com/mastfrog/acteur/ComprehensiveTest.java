package com.mastfrog.acteur;

import com.fasterxml.jackson.databind.ObjectMapper;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import static com.mastfrog.mime.MimeType.PLAIN_TEXT_UTF_8;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.collections.StringObjectMap;
import static io.netty.handler.codec.http.HttpResponseStatus.PAYMENT_REQUIRED;
import static io.netty.util.CharsetUtil.UTF_8;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author tim
 */
@TestWith({HttpTestHarnessModule.class, CompApp.Module.class, SilentRequestLogger.class})
public class ComprehensiveTest {

    private static final int TIMEOUT_SECONDS = 180;

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testPlainHelp(HttpHarness harn) throws Throwable {
        HttpResponse<String> resp = harn.get("help")
                .asserting(a -> a.assertOk()
                .assertHeader("content-type", "application/json;charset=utf-8"))
                .assertAllSucceeded().get();

        Map<String, Object> m = new ObjectMapper().readValue(resp.body(), StringObjectMap.class);
        assertEquals(setOf("CORSResource",
                "HelpPage",
                "IterPage",
                "Unchunked",
                "Echo",
                "DeferredOutput",
                "Branch",
                "NoContentPage",
                "DynPage"), m.keySet());
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testHtmlHelp(HttpHarness harn) throws Throwable {
        HttpResponse<String> resp = harn.get("help?html=true")
                .asserting(a -> a.assertOk()
                .assertHeader("content-type", "text/html;charset=utf-8"))
                .assertAllSucceeded().get();
        String body = resp.body();
        assertTrue(body.contains("<p>App that does lots of stuff"));
        setOf("CORSResource",
                "HelpPage",
                "IterPage",
                "Unchunked",
                "Echo",
                "DeferredOutput",
                "Branch",
                "NoContentPage",
                "DynPage").forEach(item -> {
                    assertTrue(body.contains(item));
                });
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testEcho(HttpHarness harness) throws Throwable {
        harness.post("echo", "Echo this back to me", UTF_8)
                .setHeader(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .asserting(asserts -> asserts
                .assertOk()
                .assertBody("Echo this back to me")
                ).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testIter(HttpHarness harness) throws Throwable {
        harness.get("iter?iters=5").asserting(
                a -> a.assertOk().assertBody(test("Iteration", 5)))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testIter2(HttpHarness harness) throws Throwable {
        harness.get("iter?iters=7&msg=Hello%20").asserting(asserts
                -> asserts.assertOk().assertBody(test("Hello", 7))
        ).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testDeferred(HttpHarness harness) throws Throwable {
        harness.get("deferred").asserting(asserts
                -> asserts.assertBody("I guess it's okay now").assertOk()
        ).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testNothing(HttpHarness harness) throws Throwable {
        harness.get("nothing")
                .asserting(a -> a.assertStatus(PAYMENT_REQUIRED.code())
                .assertBody(b -> b != null)).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testBranch1(HttpHarness harness) throws Throwable {
        harness.get("branch?a=true").asserting(a -> a.assertOk().assertBody("A"))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testBranch2(HttpHarness harness) throws Throwable {
        harness.get("branch").asserting(a -> a.assertOk().assertBody("B")).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testEcho2(HttpHarness harness) throws Throwable {
        harness.post("echo", "Echo this back to me", UTF_8)
                .asserting(a -> a.assertOk().assertBody("Echo this back to me"))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testDynamicActeur(HttpHarness harness) throws Throwable {
        harness.get("dyn").asserting(a -> a.assertOk().assertBody("Dynamic acteur"))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testUnchunked(HttpHarness harness) throws Throwable {
        harness.get("unchunked?iters=7")
                .asserting(a -> a.assertOk().assertBody(test("Iteration", 7)))
                .assertAllSucceeded();
    }

    static Predicate<String> test(String msg, int count) {
        return s -> Objects.equals(s, iter(msg, count));
    }

    private static String iter(String msg, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= count; i++) {
            sb.append(msg).append(" ").append(i);
            sb.append('\n');
        }
        return sb.toString();
    }
}
