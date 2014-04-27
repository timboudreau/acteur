package com.mastfrog.acteur.preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifieds a glob-style URL path, such as /mything/* - a * can represent
 * anything but a / character.  Used for simpler matching than full-blown
 * regular expression evaluation (which it is translated to at runtime).
 *
 * Annotation which can appear on an Acteur with the &#064;HttpCall annotation
 * or on a Page with that annotation.
 *
 * @author Tim Boudreau 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Description("Glob-style URL Path")
public @interface Path {
    String[] value();
}
