package com.mastfrog.acteur.preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which can appear on an Acteur with the &#064;HttpCall annotation
 * or on a Page with that annotation.  Specifies that the passed url parameters
 * must be numbers (with the expressed constraints) if present or the
 * request should be rejected with BAD REQUEST.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Description("Parameters must be numbers if present")
public @interface ParametersMustBeNumbersIfPresent {
    String[] value();
    boolean allowDecimal() default false;
    boolean allowNegative() default true;
}
