package com.mastfrog.acteur.generic.app;

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
public @interface HttpCall {
    public static final String META_INF_PATH = "META-INF/http/pages.list";
    int order() default 0;
}
