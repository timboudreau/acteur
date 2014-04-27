package com.mastfrog.acteur.preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Make the request body available as the passed type.  Note that this type
 * must also be in the application's &#064;ImplicitBindings to be available to
 * Guice.
 * Annotation which can appear on an Acteur with the &#064;HttpCall annotation
 * or on a Page with that annotation.
 *
 * @author Tim Boudreau 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InjectRequestBodyAs {
    Class<?> value();
}
