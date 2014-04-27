package com.mastfrog.acteur.preconditions;

import com.mastfrog.acteur.headers.Method;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which can appear on an Acteur with the &#064;HttpCall annotation
 * or on a Page with that annotation.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Description("If a particular HTTP method matches, require some set of parameters")
public @interface RequireParametersIfMethodMatches {
    public String[] value();
    public Method method();
}
