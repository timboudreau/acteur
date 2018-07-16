/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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
package com.mastfrog.util.builder;

import com.mastfrog.util.function.ThrowingRunnable;
import com.mastfrog.util.thread.AutoCloseThreadLocal;
import java.util.function.BiFunction;
import com.mastfrog.util.thread.QuietAutoCloseable;

/**
 * For ease of writing builders that allow static methods to be used, while
 * holding the local state in a threadlocal. E.g.
 * <pre>
 * path("/foo/bar", () -> { get(() -> return "Hello world"; });
 *
 * </pre>
 *
 * @author Tim Boudreau
 */
public final class ThreadLocalBuilderSupport<T> {

    private final AutoCloseThreadLocal<T> local = new AutoCloseThreadLocal<>();
    private BiFunction<T, T, T> coalescer;

    public ThreadLocalBuilderSupport() {

    }

    public ThreadLocalBuilderSupport(BiFunction<T, T, T> coalescer) {
        this.coalescer = coalescer;
    }

    public T get() {
        return local.get();
    }

    public QuietAutoCloseable with(T val) {
        T curr = local.get();
        if (coalescer == null) {
            throw new IllegalStateException("Already set to " + curr);
        } else {
            T nue = curr == null ? val : coalescer.apply(curr, val);
            QuietAutoCloseable outer = local.set(nue);
            return () -> {
                outer.close();
                local.set(curr);
            };
        }
    }

    public void with(T val, ThrowingRunnable tr) throws Exception {
        T curr = local.get();
        if (coalescer == null) {
            throw new IllegalStateException("Already set to " + curr);
        } else {
            T nue = curr == null ? val : coalescer.apply(curr, val);
            try (QuietAutoCloseable outer = local.set(nue)) {
                tr.run();
            }
        }
    }
}
