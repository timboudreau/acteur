package com.mastfrog.acteur.preconditions;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Page;
import java.util.List;

/**
 * Mechanism for pluggable handling of annotations - this way an application can
 * create its own annotations which imply that some acteurs are to be added
 * to a page, and actually add them.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultPageAnnotationHandler.class)
public interface PageAnnotationHandler {
    <T extends Page> boolean processAnnotations(T page, List<? super Acteur> addTo);
}
