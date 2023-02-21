package com.mastfrog.acteur;

import com.mastfrog.giulius.annotations.Setting;
import static com.mastfrog.giulius.annotations.Setting.Tier.TERTIARY;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If this annotation appears on an Application, it automatically makes a Help
 * page available.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Help {

    @Setting(value = "Defines a regular expression used for the URL path for JSON help",
            defaultValue = "^help$", tier = TERTIARY)
    public static final String HELP_URL_PATTERN_SETTINGS_KEY = "helpUrlPattern";

    @Setting(value = "Defines a regular expression used to match the URL path for HTML help",
            defaultValue = "\"^help\\\\.html$\"", tier = TERTIARY)
    public static final String HELP_HTML_URL_PATTERN_SETTINGS_KEY = "helpHtmlUrlPattern";

    String value() default "";
}
