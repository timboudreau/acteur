/*
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur;

import com.google.common.collect.Sets;
import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.errors.ResponseException;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.PageAnnotationHandler;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.Checks;
import static com.mastfrog.util.Checks.notNull;
import com.mastfrog.util.Exceptions;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.thread.AutoCloseThreadLocal;
import com.mastfrog.util.thread.QuietAutoCloseable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import static java.util.Collections.singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Really an aggregation of Acteurs and a place to set header values; in recent
 * versions of Acteur it is rarely necessary to implement this - instead, simply
 * annotate your entry-point Acteur with &#064;HttpCall and one will be
 * generated for you under-the-hood, and use &#064;Precursors and
 * &#064;Concluders to specify Acteurs that should run before/after that one.
 * <p>
 * This class was central to the Acteur 1.0 API, but at this point is mainly an
 * implementation class. Page subclasses are still generated from annotations,
 * but are rarely used in application code at this point; they are still useful
 * in unit and integration tests where you may have multiple applications for
 * different tests.
 * <p>
 * To implement, simply subclass and add zero or more
 * <code><a href="Acteur.html">Acteur</a></code> classes or instances using the
 * <code>add()</code> method. Each Acteur is called in succession and can do one
 * or more of:
 * <ul>
 * <li>Abort responding to the request so that the next Page in the application
 * can have a chance to respond</li>
 * <li>Leave whether or not to respond up to the next Acteur in the chain, but
 * create some objects to inject into the next Acteur's constructor</li>
 * <li>Accept responsibility for this page responding to the request (other
 * pages will not be tried)</li>
 * <li>Respond to the request, ending processing of it, by setting the response
 * code and optionally adding a <code>ChannelFutureListener</code> which can
 * start writing the response body once the headers are sent</li>
 * </ul>
 * The point is to break the logic of responding to requests into small,
 * reusable chunks implemented as Acteurs.
 *
 * @author Tim Boudreau
 */
public abstract class Page {

    private static final AutoCloseThreadLocal<Page> CURRENT_PAGE = new AutoCloseThreadLocal<>();
    private final List<Object> acteurs = new ArrayList<>(10);
    volatile Application application;

    protected Page() {
    }

    public final void add(Acteur action) {
        acteurs.add(action);
    }

    /**
     * Get a description used in the generated web API help.
     *
     * @return A description
     */
    protected String getDescription() {
        Description desc = getClass().getAnnotation(Description.class);
        return desc != null ? desc.value() : getClass().getSimpleName();
    }

    /**
     * For the case of adding live page objects, if we want to figure out what
     * patterns they look for to optimize dispatch.
     *
     * @return null if nothing found, otherwise a set of patterns
     */
    Set<PathPatternInfo> findPathPatterns() {
        Set<PathPatternInfo> result = null;
        for (Object o : acteurs) {
            if (o instanceof ActeurFactory.ExactMatchPath) {
                String path = ((ActeurFactory.ExactMatchPath) o).path;
                boolean decode = ((ActeurFactory.ExactMatchPath) o).decode;
                if (result == null) {
                    result = new HashSet<>();
                }
                result.add(new PathPatternInfo(decode, false, singleton(path), true));
            } else if (o instanceof ActeurFactory.MatchPath) {
                Set<String> patterns = setOf(((ActeurFactory.MatchPath) o).regexen);
                boolean decode = ((ActeurFactory.MatchPath) o).decode;
                if (result == null) {
                    result = new HashSet<>();
                }
                result.add(new PathPatternInfo(decode, false, patterns, false));
            }
        }
        return result;
    }

    static class PathPatternInfo {

        final boolean decode;
        final boolean regex;
        final boolean knownExact;
        final Set<String> patterns;

        public PathPatternInfo(boolean decode, boolean regex, Set<String> patterns, boolean knownExact) {
            this.decode = decode;
            this.regex = regex;
            this.patterns = patterns;
            this.knownExact = knownExact;
        }
    }

    Set<Method> findMethods() {
        for (Object o : acteurs) {
            if (o instanceof ActeurFactory.MatchMethods) {
                return setOf(((ActeurFactory.MatchMethods) o).methods());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    void describeYourself(Map<String, Object> into) {
        Map<String, Object> m = new HashMap<>();
        if (getClass().getAnnotation(Description.class) != null) {
            m.put("description", getClass().getAnnotation(Description.class).value());
        }
        List<Object> acteurs = this.acteurs(application.isDefaultCorsHandlingEnabled());
        for (Object o : acteurs) {
            try {
                Acteur a = o instanceof Acteur ? (Acteur) o
                        : application.getDependencies().getInstance(((Class<? extends Acteur>) o));
                a.describeYourself(m);
            } catch (Exception e) {
                //ok
            }
        }
        if (!m.isEmpty()) {
            into.put(getClass().getSimpleName(), m);
        }
    }

    static QuietAutoCloseable set(Page page) {
        Checks.notNull("page", page);
        QuietAutoCloseable result = CURRENT_PAGE.set(page);
        assert CURRENT_PAGE.get() != null;
        return result;
    }

    static Page get() {
        return CURRENT_PAGE.get();
    }

    static void clear() {
        CURRENT_PAGE.clear();
    }

    private static final Set<Class<?>> CHECKED = Sets.newIdentityHashSet();
    private static final Set<Class<?>> FAILED = Sets.newIdentityHashSet();

    protected final void add(Class<? extends Acteur> action) {
        if (!CHECKED.contains(notNull("acteur", action))) {
            CHECKED.add(action);
            if ((action.getModifiers() & Modifier.ABSTRACT) != 0) {
                if (action.getAnnotation(ImplementedBy.class) == null) {
                    FAILED.add(action);
                    throw new IllegalArgumentException(action + " is abstract");
                }
            }
            if (action.isLocalClass()) {
                FAILED.add(action);
                throw new IllegalArgumentException(action + " is not a top-level class");
            }
            if (!Acteur.class.isAssignableFrom(action)) {
                FAILED.add(action);
                throw new IllegalArgumentException(action + " is not a subclass of "
                        + Acteur.class.getName());
            }
            assert Application.checkConstructor(action);
        }
        if (FAILED.contains(action)) {
            throw new IllegalArgumentException("Not a usable acteur class - " + action 
                    + " see previous error");
        }
        acteurs.add(action);
    }

    final Application getApplication() {
        return application;
    }

    final void setApplication(Application app) {
        this.application = app;
    }

    final int countActeurs() {
        return acteurs.size();
    }

    List<Object> acteurs(boolean corsByDefault) {
        List<Acteur> annos = this.annotations();
        List<Object> l = new ArrayList<>(annos.size() + acteurs.size() + (corsByDefault ? 1 : 0));
        l.addAll(this.annotations());
        if (corsByDefault) {
            l.add(CORSResource.CorsHeaders.class);
        }
        l.addAll(acteurs);
        return l;
    }

    @SuppressWarnings("unchecked")
    final Acteur getActeur(int ix, boolean logErrors) {
        try (QuietAutoCloseable ac = Page.set(this)) {
            Application app = getApplication();
            if (app == null) {
                throw new NullPointerException("Application is null - being called out of scope?");
            }
            Object o = acteurs.get(ix);
            if (o instanceof Class<?>) {
                Dependencies deps = app.getDependencies();
                try {
                    Class<? extends Acteur> c = (Class<? extends Acteur>) o;
                    return deps.getInstance(c);
                } catch (ThreadDeath | OutOfMemoryError e) {
                    return Exceptions.chuck(e);
                } catch (final Exception t) {
                    return Acteur.error(null, this, t, deps.getInstance(HttpEvent.class), logErrors && !(t instanceof ResponseException));
                } catch (final Error t) {
                    return Acteur.error(null, this, t, deps.getInstance(HttpEvent.class), logErrors);
                }
            } else if (o instanceof Acteur) {
                return (Acteur) o;
            } else {
                throw new AssertionError("?: " + o + " in " + this);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Iterator<Acteur> annotationActeurs() {
        return annotations().iterator();
    }

    private List<Acteur> annotations() {
        PageAnnotationHandler.Registry handler = getApplication().getDependencies().getInstance(PageAnnotationHandler.Registry.class);
        List<Acteur> results = new LinkedList<>();
        handler.processAnnotations(this, results);
        return results;
    }
}
