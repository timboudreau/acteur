package com.mastfrog.acteur.preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which causes a Page to return <code>400 Bad Request</code> if the
 * url parameters do not match what the annotation describes.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiredUrlParameters {

    String[] value();

    Combination combination() default Combination.ALL;

    public enum Combination {

        ALL,
        AT_LEAST_ONE
    }
}
