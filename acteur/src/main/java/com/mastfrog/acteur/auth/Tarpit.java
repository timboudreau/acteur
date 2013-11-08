package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;

/**
 *
 * @author tim
 */
@ImplementedBy(TarpitImpl.class)
public interface Tarpit {
    public int add(HttpEvent evt);
    public int count(HttpEvent evt);
}
