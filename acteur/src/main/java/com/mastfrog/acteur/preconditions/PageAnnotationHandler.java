package com.mastfrog.acteur.preconditions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Page;
import com.mastfrog.util.ConfigurationError;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Mechanism for pluggable handling of annotations - this way an application can
 * create its own annotations which imply that some acteurs are to be added to a
 * page, and actually add them.
 *
 * To use, implement your PageAnnotationHandler, and bind it as an eager
 * singleton so it is instantiated and registered on startup
 *
 * @author Tim Boudreau
 */
//@ImplementedBy(DefaultPageAnnotationHandler.class)
public abstract class PageAnnotationHandler {

    private final Set<Class<? extends Annotation>> types;

    @SuppressWarnings("unchecked")
    /**
     * Create a PageAnnotationHandler
     *
     * @param registry The registry, which this will automatically be registered
     * with
     * @param annotationTypes An array of annotation classes (while generic
     * arrays are illegal in java, an error will be thrown if a passed type is
     * not an annotation type)
     */
    protected PageAnnotationHandler(Registry registry, Class<?>... annotationTypes) {
        Set<Class<? extends Annotation>> tps = new HashSet<>();
        for (Class<?> type : annotationTypes) {
            if (!Annotation.class.isAssignableFrom(type)) {
                throw new ConfigurationError("Not an annotation type: " + type.getName());
            }
            Retention retention = type.getAnnotation(Retention.class);
            if (retention.value() != RetentionPolicy.RUNTIME) {
                throw new ConfigurationError("Attempting to use " + type.getName()
                        + " as a page annotation, but it does not have @Retention(RUNTIME)");
            }
            tps.add((Class<? extends Annotation>) type);
        }
        this.types = ImmutableSet.<Class<? extends Annotation>>copyOf(tps);
        registry.register(this);
    }

    public abstract <T extends Page> boolean processAnnotations(T page, List<? super Acteur> addTo);

    protected final Set<Class<? extends Annotation>> types() {
        return types;
    }

    /**
     * Registry of PageAnnotationHandlers
     */
    @Singleton
    public static final class Registry {

        private final List<PageAnnotationHandler> handlers = new LinkedList<>();
        private final Set<Class<? super Page>> annotatedPages = Sets.newConcurrentHashSet();

        public <T extends Page> boolean processAnnotations(T page, List<? super Acteur> addTo) {
            boolean result = false;
            for (PageAnnotationHandler handler : handlers) {
                result |= handler.processAnnotations(page, addTo);
            }
            return result;
        }

        @SuppressWarnings({"element-type-mismatch", "unchecked"})
        public <T extends Page> boolean hasAnnotations(T page) {
            Class<? super Page> c = (Class<? super Page>) page.getClass();
            if (annotatedPages.contains(page.getClass())) {
                return true;
            }
            for (Class<? extends Annotation> type : types()) {
                if (c.getAnnotation(type) != null) {
                    annotatedPages.add(c);
                    return true;
                }
            }
            return false;
        }

        public void register(PageAnnotationHandler handler) {
            handlers.add(handler);
        }

        private volatile Set<Class<? extends Annotation>> types;

        public Set<Class<? extends Annotation>> types() {
            if (types == null) {
                synchronized (this) {
                    if (types == null) {
                        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.<Class<? extends Annotation>>builder();
                        for (PageAnnotationHandler h : handlers) {
                            builder.addAll(h.types());
                        }
                        this.types = builder.build();
                    }
                }
            }
            return types;
        }
    }
}
