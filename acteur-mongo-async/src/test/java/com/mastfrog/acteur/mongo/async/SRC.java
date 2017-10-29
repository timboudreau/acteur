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

import com.mongodb.async.SingleResultCallback;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.Assert;

/**
 *
 * @author Tim Boudreau
 */
final class SRC<T> implements SingleResultCallback<T> {

    volatile T obj;
    volatile Throwable thrown;
    private volatile CountDownLatch latch = new CountDownLatch(1);

    public static <T> T run(Consumer<SingleResultCallback<T>> cons) throws Throwable {
        SRC<T> result = new SRC<T>();
        cons.accept(result);
        return result.assertNotThrown();
    }

    public T assertNotThrown() throws Throwable {
        latch.await(10, TimeUnit.SECONDS);
        if (thrown != null) {
            throw thrown;
        }
        T ret = obj;
        obj = null;
        latch = new CountDownLatch(1);
        return ret;
    }

    public T assertHasResult() throws Throwable {
        T val = assertNotThrown();
        Assert.assertNotNull(val);
        return val;
    }

    @Override
    public void onResult(T t, Throwable thrwbl) {
        obj = t;
        thrown = thrwbl;
        latch.countDown();
    }

}
