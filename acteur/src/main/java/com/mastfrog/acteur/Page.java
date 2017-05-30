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

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.errors.ResponseException;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.PageAnnotationHandler;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.thread.AutoCloseThreadLocal;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.handler.codec.http.HttpResponse;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Really an aggregation of Acteurs and a place to set header values; in recent
 * versions of Acteur it is rarely necessary to implement this - instead, simply
 * annotate your entry-point Acteur with &#064;HttpCall and one will be
 * generated for you under-the-hood.
 *
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
public abstract class Page implements Iterable<Acteur> {

    private static final AutoCloseThreadLocal<Page> CURRENT_PAGE = new AutoCloseThreadLocal<>();
    private final List<Object> acteurs = new ArrayList<>(10);
    volatile Application application;

    protected Page() {
    }

    public final void add(Acteur action) {
        acteurs.add(action);
    }

    protected String getDescription() {
        Description desc = getClass().getAnnotation(Description.class);
        return desc != null ? desc.value() : getClass().getSimpleName();
    }

    Iterable<Object> contents() {
        List<Object> result = new ArrayList<>(annotations());
        result.addAll(this.acteurs);
        return result;
    }

    void describeYourself(Map<String, Object> into) {
        Map<String, Object> m = new HashMap<>();
        if (getClass().getAnnotation(Description.class) != null) {
            m.put("description", getClass().getAnnotation(Description.class).value());
        }
        int count = countActeurs();
        for (int i = 0; i < count; i++) {
            try {
                Acteur a = getActeur(i, false);
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

    protected final void add(Class<? extends Acteur> action) {
        if ((action.getModifiers() & Modifier.ABSTRACT) != 0) {
            if (action.getAnnotation(ImplementedBy.class) == null) {
                throw new IllegalArgumentException(action + " is abstract");
            }
        }
        if (action.isLocalClass()) {
            throw new IllegalArgumentException(action + " is not a top-level class");
        }
        if (!Acteur.class.isAssignableFrom(action)) {
            throw new IllegalArgumentException(action + " is not a subclass of "
                    + Acteur.class.getName());
        }
        assert Application.checkConstructor(action);
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

    final List<Object> getActeurs() {
        return Collections.unmodifiableList(acteurs);
    }

    List<Object> acteurs() {
        List<Acteur> annos = this.annotations();
        List<Object> l = new ArrayList<>(annos.size() + acteurs.size());
        l.addAll(this.annotations());
        l.addAll(acteurs);
        return l;
    }

    final Acteur getActeur(int ix) {
        return getActeur(ix, true);
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

    /**
     * Allows the page to modify the raw Netty HttpResponse after all Acteurs have
     * run.
     * 
     * @param event The event
     * @param acteur The acteur
     * @param response The response
     * @deprecated This was a bad idea, which can result in invalid responses and bypasses
     * important checking.  Simply use Acteur.add(Headers.WHATEVER, value) instead.
     */
    @Deprecated
    protected void decorateResponse(Event<?> event, Acteur acteur, HttpResponse response) {
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

    @Override
    @Deprecated
    public Iterator<Acteur> iterator() {
        assert getApplication() != null : "Application is null - called outside request?";
        PageAnnotationHandler.Registry registry
                = getApplication().getDependencies().getInstance(
                        PageAnnotationHandler.Registry.class);
        if (registry.hasAnnotations(this)) {
            return CollectionUtils.combine(annotationActeurs(), new I());
        } else {
            return new I();
        }
    }

    /**
     * An adaptor which instantiates the Acteurs of the page on demand and
     * returns them one by one
     */
    private final class I implements Iterator<Acteur> {

        int ix = 0;

        @Override
        public boolean hasNext() {
            return ix < countActeurs();
        }

        @Override
        public Acteur next() {
            return getActeur(ix++);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported");
        }
    }
}
