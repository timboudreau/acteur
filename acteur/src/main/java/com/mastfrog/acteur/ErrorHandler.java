package com.mastfrog.acteur;

/**
 * Injectable interceptor for exceptions thrown.  Used in tests which want
 * to fail if server-side exceptions occur.
 *
 * @author Tim Boudreau
 */
public interface ErrorHandler {
    public void onError(Throwable err);
}
