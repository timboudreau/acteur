package com.mastfrog.acteur;

import com.google.common.base.Predicates;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import static com.mastfrog.mime.MimeType.PLAIN_TEXT_UTF_8;
import static io.netty.handler.codec.http.HttpResponseStatus.PAYMENT_REQUIRED;
import static io.netty.util.CharsetUtil.UTF_8;
import java.util.function.Predicate;
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
    public void testEcho(HttpHarness harness) throws Throwable {
        harness.post("echo", "Echo this back to me", UTF_8)
                .setHeader(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .applyingAssertions(asserts -> asserts
                .assertOk()
                .assertBody("Echo this back to me")
                ).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testIter(HttpHarness harness) throws Throwable {
        harness.get("iter?iters=5").applyingAssertions(
                a -> a.assertOk().assertBody(test("Iteration", 5)))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testIter2(HttpHarness harness) throws Throwable {
        harness.get("iter?iters=7&msg=Hello%20").applyingAssertions(asserts
                -> asserts.assertOk().assertBody(test("Hello", 7))
        ).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testDeferred(HttpHarness harness) throws Throwable {
        harness.get("deferred").applyingAssertions(asserts
                -> asserts.assertBody("I guess it's okay now").assertOk()
        ).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testNothing(HttpHarness harness) throws Throwable {
        harness.get("nothing")
                .applyingAssertions(a -> a.assertResponseCode(PAYMENT_REQUIRED.code())
                .assertBody(b -> b != null)).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testBranch1(HttpHarness harness) throws Throwable {
        harness.get("branch?a=true").applyingAssertions(a -> a.assertOk().assertBody("A"))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testBranch2(HttpHarness harness) throws Throwable {
        harness.get("branch").applyingAssertions(a -> a.assertOk().assertBody("B")).assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testEcho2(HttpHarness harness) throws Throwable {
        harness.post("echo", "Echo this back to me", UTF_8)
                .applyingAssertions(a -> a.assertOk().assertBody("Echo this back to me"))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testDynamicActeur(HttpHarness harness) throws Throwable {
        harness.get("dyn").applyingAssertions(a -> a.assertOk().assertBody("Dynamic acteur"))
                .assertAllSucceeded();
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    public void testUnchunked(HttpHarness harness) throws Exception, Throwable {
        harness.get("unchunked?iters=7")
                .applyingAssertions(a -> a.assertOk().assertBody(test("Iteration", 7)))
                .assertAllSucceeded();
    }

    static Predicate<String> test(String msg, int count) {
        return Predicates.equalTo(iter(msg, count));
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
