package com.mastfrog.acteur;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If this annotation appears on an Application, it automatically makes a
 * Help page available.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Help {
    public static final String HELP_URL_PATTERN_SETTINGS_KEY = "helpUrlPattern";
    public static final String HELP_HTML_URL_PATTERN_SETTINGS_KEY = "helpHtmlUrlPattern";
    String value() default "";
}
