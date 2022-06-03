package com.mastfrog.acteur.annos;

import com.mastfrog.acteur.Acteur;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which is <i>only useful on Acteurs with the HttpCall annotation
 * to trigger page source generation</i>, which specifies some more Acteurs
 * to run before the annotated one.
 * 
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Precursors {
    Class<? extends Acteur>[] value();
}
