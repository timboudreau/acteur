package com.mastfrog.acteur.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Handle with which a server can be interrogated about its state or shut down;
 * implements Condition for backward compatibility with early versions of this
 * library.  The signal() and signalAll() methods initiate a graceful shutdown
 * where running requests are concluded;  shutdown with an argument of true
 * for immediately will interrupt threads and trigger immediate shutdown.
 *
 * @author Tim Boudreau
 */
public interface ServerControl extends Condition {
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
    
    int getPort();
}
