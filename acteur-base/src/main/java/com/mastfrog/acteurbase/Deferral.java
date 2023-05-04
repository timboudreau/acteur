/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteurbase;

/**
 * Allows executing a chain of Acteurs to be interrupted for some long running
 * operation and resumed when the resumer's resume() method is called.
 *
 * @author Tim Boudreau
 */
public interface Deferral {

    /**
     * Defer execution until the passed resumer is called.  <i>Prefer the
     * overload that takes a <code>{@link DeferredCode}</code> in almost all
     * circumstances. If you call
     * <code>{@link Resumer#resume(java.lang.Object...)</code>
     * <i>synchronously in an Acteur constructor</i> or <i><b>even in a
     * background thread doing work that could <u>possibly</u> complete before
     * the constructor does</i></b> it can crash badly, trying to resume the
     * chain when it is not in-between steps.
     * <p>
     * The one use case for this method is where you have an object which you
     * <i>must</i> use synchronously (like a <code>ChannelFutureListener</code>
     * that is going to write a response), and you need to pass the
     * <code>{@link Resumer}</code> as an argument to its constructor for some
     * reason. Some dark corners of websocket support need this. Almost nothing
     * else does.
     * </p>
     */
    public Resumer defer();

    /**
     * Defer execution until the resumer's resume() method is called. The
     * DeferredCode is guaranteed to be executed <i>after</i> the method that
     * calls <code>defer()</code> has exited.
     *
     * @return A resumer which can restart exeution later
     * @throws IllegalStateException if defer has already been called without a
     * corresponding call to resume().
     */
    public Resumer defer(DeferredCode code);

    /**
     * Code that should run <i>after</i> the acteur currently being run has
     * exited. It is possible for code that launches a background thread to
     * complete and call resume() before the acteur constructor defer was called
     * from completes running. Using deferredCode guarantees that the code is
     * run after the method defer was called from exits.
     */
    @FunctionalInterface
    public interface DeferredCode {

        void run(Resumer resume) throws Exception;
    }

    public interface Resumer {

        /**
         * Resume execution. Throws an IllegalStateException if resume() has
         * already been called.
         *
         * @throws IllegalStateException if resume() has already been called.
         */
        public void resume(Object... addToContext);
    }
}
