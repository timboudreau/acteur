/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.acteur.mongo.async;

import com.mastfrog.util.Exceptions;
import com.mastfrog.util.function.ThrowingConsumer;
import com.mastfrog.util.function.ThrowingRunnable;
import com.mastfrog.util.thread.Callback;
import com.mongodb.async.SingleResultCallback;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility class to allow tests to wait for async calls, which collects and
 * throws any exceptions thrown during async operations.
 */
class TestSupport implements Function<Throwable, Boolean> {

    private final AtomicReference<Throwable> thrown = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    /**
     * Pass a throwable which may be null, causing it to be recorded and thrown
     * on exit.
     *
     * @param thrwbl A throwable or null
     * @return True if done
     */
    @Override
    public Boolean apply(Throwable thrwbl) {
        if (thrwbl != null) {
            thrwbl.printStackTrace();
            Throwable t = thrown.get();
            if (t != null) {
                t.addSuppressed(thrwbl);
            } else {
                thrown.set(thrwbl);
            }
            done();
            return true;
        }
        return false;
    }

    /**
     * Wait for this TestSupport to complete.
     *
     * @param timeout The timeout
     * @param unit The time unit
     */
    public void await(long timeout, TimeUnit unit) {
        if (doneCalled.get()) {
            System.err.println("done() already called, not waiting.");
            return;
        }
        try {
            latch.await(timeout, unit);
        } catch (InterruptedException ex) {
            Exceptions.chuck(ex);
        }
        Throwable t = thrown.get();
        if (t != null) {
            Exceptions.chuck(t);
        }
        if (!doneCalled.get()) {
            Exceptions.chuck(new TimeoutException("Timed out; tested code may not have all run."));
        }
    }

    /**
     * Wait for a CompletableFuture to finish.
     */
    public <T> CompletableFuture<T> whenDone(CompletableFuture<T> f) {
        return whenDone(f, null);
    }

    /**
     * Wait for a CompletableFuture to finish.
     */
    public <T> CompletableFuture<T> whenDone(CompletableFuture<T> f, ThrowingConsumer<T> cons) {
        return f.whenComplete((t, thrown) -> {
            apply(thrown);
            if (thrown == null) {
                try {
                    if (cons != null) {
                        cons.apply(t);
                    }
                } catch (Exception ex) {
                    apply(thrown);
                }
            }
        });
    }

    /**
     * Create a new TestSupport which will manage async calls and collect
     * exceptions.
     *
     * @param cons A consumer to receive the instance
     */
    public static void await(Consumer<TestSupport> cons) {
        TestSupport te = new TestSupport();
        cons.accept(te);
        te.await();
    }

    /**
     * Create a new TestSupport which will manage async calls and collect
     * exceptions, which will only block for some timeout.
     *
     * @param cons A consumer to receive the instance
     */
    public static void await(long length, TimeUnit unit, Consumer<TestSupport> cons) {
        TestSupport te = new TestSupport();
        cons.accept(te);
        te.await(length, unit);
    }

    public void await() {
        await(1, TimeUnit.MINUTES);
    }

    final AtomicBoolean doneCalled = new AtomicBoolean();

    /**
     * Call this method when all async operations have completed successfully.
     */
    public void done() {
        doneCalled.set(true);
        latch.countDown();
    }

    /**
     * Run some async code, triggering exit if an exception is thrown.
     *
     * @param th A runnable
     */
    public void run(ThrowingRunnable th) {
        if (doneCalled.get()) {
            throw new IllegalStateException("Already done");
        }
        try {
            th.run();
        } catch (Throwable throwed) {
            apply(throwed);
        }
    }

    /**
     * Create a SingleResultCallback whose exception is handled by this
     * TestSupport but will pass success results to the passed consumer.
     *
     * @param <T>
     * @param receiver
     * @return
     */
    public <T> SingleResultCallback<T> callback(ThrowingConsumer<T> receiver) {
        return (T t, Throwable thrwbl) -> {
            if (apply(thrwbl)) {
                return;
            }
            run(() -> {
                receiver.apply(t);
            });
        };
    }

    /**
     * Create a Mastfrog callback instance where the exception parameter is
     * handled by this.
     */
    public <T> Callback<T> cb(ThrowingConsumer<T> receiver) {
        return (Throwable thrwbl, T t) -> {
            if (apply(thrwbl)) {
                return;
            }
            run(() -> {
                receiver.apply(t);
            });
        };
    }
}
