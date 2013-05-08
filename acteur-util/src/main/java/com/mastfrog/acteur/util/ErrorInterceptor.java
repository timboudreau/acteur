package com.mastfrog.acteur.util;

/**
 * Injectable interceptor for exceptions thrown.  Used in tests which want
 * to fail if server-side exceptions occur.
 *
 * @author Tim Boudreau
 */
public interface ErrorInterceptor {
    public void onError(Throwable err);
}
