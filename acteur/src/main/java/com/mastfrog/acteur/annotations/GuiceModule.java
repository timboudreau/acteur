package com.mastfrog.acteur.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate Guice modules with this and GenericApplictionModule will install
 * them if they are on the classpath.  This module will automatically look up
 * any modules registered with &#064;GuiceModule and install them (unless their
 * exact type is in the exclusion list passed to GenericApplictionModule's
 * constructor).
 * <p/>
 * Such modules <i>must</i> have a no-arg constructor or a 1-arg constructor
 * where the argument type is Settings, or a 2-arg constructor in which one
 * argument is Settings and the other is ReentrantScope (since some applications
 * need to do special binding of things within Acteur's request scope).
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GuiceModule {
    public static final String META_INF_PATH = "META-INF/http/modules.list";
}
