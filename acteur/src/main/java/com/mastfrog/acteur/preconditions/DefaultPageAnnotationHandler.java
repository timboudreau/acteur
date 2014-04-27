package com.mastfrog.acteur.preconditions;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Page;
import java.util.List;

/**
 * Dummy implementation so all applications are not required to bind
 * PageAnnotationHandler
 *
 * @author Tim Boudreau
 */
final class DefaultPageAnnotationHandler implements PageAnnotationHandler {

    @Override
    public <T extends Page> boolean processAnnotations(T page, List<? super Acteur> addTo) {
        //do nothing
        return false;
    }

}
