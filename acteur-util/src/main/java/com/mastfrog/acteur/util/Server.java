/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur.util;

import com.google.inject.ImplementedBy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * A server which can be started, waited on and shutdown.
 *
 * @author Tim Boudreau
 */

public interface Server {
    
    /**
     * Get the port this server is running on
     * @return The port
     */
    int getPort();

    /**
     * Shut down, waiting for thread pools to terminate and connections to
     * be closed (unlike getCondition().signal()).
     * @param immediately If thread pools should be shut down without
     * processing any pending work
     * @param timeout How long to wait for shutdown (may nonetheless wait
     * longer since closing the channel does not afford a timeout)
     * @param unit A time unit
     * @throws InterruptedException
     */
    void shutdown(boolean immediately, long timeout, TimeUnit unit) throws InterruptedException;
    /**
     * Shut down and wait for termination
     * @param immediately If thread pools should be shut down without
     * processing pending work
     * @throws InterruptedException If something interrupts shutdown
     */
    void shutdown(boolean immediately) throws InterruptedException;

    /**
     * Start the server.
     * @return A Condition object which can be waited on for the server
     * to be shut down, and whose signal()/signalAll() methods will trigger
     * a shutdown
     * @throws IOException if something goes wrong
     */
    Condition start() throws IOException;
    Condition start(int port) throws IOException;
}
