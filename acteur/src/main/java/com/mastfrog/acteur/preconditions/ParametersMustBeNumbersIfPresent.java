package com.mastfrog.acteur.preconditions;

/**
 *
 * @author Tim Boudreau
 */
public @interface ParametersMustBeNumbersIfPresent {
    String[] value();
    boolean allowDecimal() default false;
    boolean allowNegative() default true;
}
