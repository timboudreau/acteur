package com.mastfrog.acteur.annotations;

import com.mastfrog.acteur.Acteur;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Precursors {
    Class<? extends Acteur>[] value();
}
