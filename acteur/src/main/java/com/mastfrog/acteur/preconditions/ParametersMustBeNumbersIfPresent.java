package com.mastfrog.acteur.preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
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
