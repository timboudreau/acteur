/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.preconditions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Page;
import com.mastfrog.giulius.Ordered;
import com.mastfrog.util.preconditions.ConfigurationError;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Mechanism for pluggable handling of annotations - this way an application can
 * create its own annotations which imply that some acteurs are to be added to a
 * page, and actually add them.
 * <p>
 * To use, implement your PageAnnotationHandler, and bind it as an eager
 * singleton so it is instantiated and registered on startup.
 * <p>
 * Note:  If you need to guarantee your handlers run *after* the built in ones
 * (for example, your code assumes authentication has already happened and a User
 * object is available for injectio), annotate your implementation with &064;Ordered
 * with a value greater than 0.
 *
 * @author Tim Boudreau
 */
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
            if (retention == null) {
                throw new ConfigurationError("Not annotated with @Retention: " + type.getName());
            }
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

        @Inject
        public Registry() {

        }

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
            Collections.sort(handlers, new Ordered.OrderedObjectComparator());
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
