/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.mongo.reactive;

import com.mastfrog.giulius.mongodb.reactive.MongoHarness;
import com.mastfrog.giulius.mongodb.reactive.util.AbstractSubscriberContextTest;
import com.mastfrog.giulius.mongodb.reactive.util.SubscriberContext;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.thread.QuietAutoCloseable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({ActeurAsyncTest.M.class, TestHarnessModule.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class SubscriberContextImplTest extends AbstractSubscriberContextTest {

    private static final ThreadLocal<SubscriberContext> CTX = new ThreadLocal<>();

    private static final ThreadLocal<ReentrantScope> SCOPE = new ThreadLocal<>();

    private void withContext(ReentrantScope scope, SubscriberContext ctx, Runnable r) {
        SubscriberContext old = CTX.get();
        ReentrantScope oldScope = SCOPE.get();
        SCOPE.set(scope);
        CTX.set(ctx);
        try ( QuietAutoCloseable qac = scope.enter("Hello")) {
            try {
                r.run();
            } finally {
                CTX.set(old);
                SCOPE.set(oldScope);
            }
        }
    }

    @Override
    public Runnable additionalRunnable() {
        ReentrantScope sc = SCOPE.get();
        assertNotNull(sc);
        return () -> {
            assertEquals("Hello", sc.provider(String.class, () -> "Uh oh").get());
        };
    }

    @Override
    public SubscriberContext createContext(UN un) {
        SubscriberContext ctx = CTX.get();
        assertNotNull("Not in context", ctx);
        return ctx;
    }

    @Test
    public void testRunnablesRun(ReentrantScope scope, SubscriberContext ctx) {
        withContext(scope, ctx, super::doTestRunnablesRun);
    }

    @Test
    public void testConsumersRun(ReentrantScope scope, SubscriberContext ctx) {
        withContext(scope, ctx, super::doTestConsumersRun);
    }

    @Test
    public void testBiConsumersRun(ReentrantScope scope, SubscriberContext ctx) {
        withContext(scope, ctx, super::doTestBiConsumersRun);
    }

    @Override
    public boolean checksUncaughtHandler() {
        return false;
    }
}
