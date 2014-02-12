package com.mastfrog.acteur.annotations;

import com.mastfrog.acteur.Acteur;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which is <i>only useful on Acteurs with the HttpCall annotation to
 * trigger page source generation</i>, which specifies some more Acteurs to run
 * after the annotated one.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Concluders {

    Class<? extends Acteur>[] value();
}
