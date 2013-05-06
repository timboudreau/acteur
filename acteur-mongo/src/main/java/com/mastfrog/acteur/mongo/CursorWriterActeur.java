package com.mastfrog.acteur.mongo;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;

/**
 *
 * @author Tim Boudreau
 */
public final class CursorWriterActeur extends Acteur {
    
    @Inject
    CursorWriterActeur(CursorWriter writer) {
        setChunked(true);
        setState(new RespondWith(200));
        setResponseWriter(writer);
    }
}
