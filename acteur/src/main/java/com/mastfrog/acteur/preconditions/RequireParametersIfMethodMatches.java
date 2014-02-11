package com.mastfrog.acteur.preconditions;

import com.mastfrog.acteur.headers.Method;
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
@Description("If a particular HTTP method matches, require some set of parameters")
public @interface RequireParametersIfMethodMatches {
    public String[] value();
    public Method method();
}
