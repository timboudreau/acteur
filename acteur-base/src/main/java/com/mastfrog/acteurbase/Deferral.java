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
     * Defer execution
     *
     * @return A resumer which can restart exeution later
     * @throws IllegalStateException if defer has already been called without
     * a corresponding call to resume().
     */
    public Resumer defer();
    public Resumer defer(DeferredCode code);
    
    /**
     * Code that should run <i>after</i> the acteur currently being run has
     * exited.  It is possible for code that launches a background thread
     * to complete and call resume() before the acteur constructor defer was
     * called from completes running.  Using deferredCode guarantees that the
     * code is run after the method defer was called from exits.
     */
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
