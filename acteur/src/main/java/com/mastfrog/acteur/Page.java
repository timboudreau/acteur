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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.preconditions.BannedUrlParameters;
import com.mastfrog.acteur.preconditions.BasicAuth;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.InjectParametersAsInterface;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import com.mastfrog.acteur.preconditions.MaximumPathLength;
import com.mastfrog.acteur.preconditions.MaximumRequestBodyLength;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.MinimumRequestBodyLength;
import com.mastfrog.acteur.preconditions.PageAnnotationHandler;
import com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.preconditions.RequireAtLeastOneUrlParameterFrom;
import com.mastfrog.acteur.preconditions.RequireParametersIfMethodMatches;
import com.mastfrog.acteur.preconditions.RequiredUrlParameters;
import com.mastfrog.acteur.preconditions.UrlParametersMayNotBeCombined;
import com.mastfrog.acteur.preconditions.UrlParametersMayNotBeCombinedSets;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.thread.AutoCloseThreadLocal;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.handler.codec.http.HttpResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Really an aggregation of Acteurs and a place to set header values. To
 * implement, simply subclass and add zero or more
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
    protected final ResponseHeaders responseHeaders = new ResponseHeaders();
    private final List<Object> acteurs = new ArrayList<>(15);
    volatile Application application;

    protected Page() {
    }

    public final ResponseHeaders getResponseHeaders() {
        return responseHeaders;
    }

    public final void add(Acteur action) {
        acteurs.add(action);
    }

    protected String getDescription() {
        return getClass().getSimpleName();
    }

    Iterable<Object> contents() {
        return CollectionUtils.toIterable(this.acteurs.iterator());
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
            into.put(getDescription(), m);
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

    final Acteur getActeur(int ix) {
        return getActeur(ix, true);
    }

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
                    return Acteur.error(null, this, t, deps.getInstance(HttpEvent.class), logErrors);
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

    Acteurs getActeurs(ExecutorService exe, ReentrantScope scope) {
        return new ActeursImpl(exe, scope, this, getApplication().getDependencies().getInstance(Settings.class));
    }

    protected void decorateResponse(Event<?> event, Acteur acteur, HttpResponse response) {
        final ResponseHeaders properties = getResponseHeaders();

        List<HeaderValueType<?>> vary = new LinkedList<>();
        properties.getVaryHeaders(vary);
        if (!vary.isEmpty()) {
            Headers.write(Headers.VARY, vary.toArray(new HeaderValueType<?>[vary.size()]), response);
        }
        Headers.writeIfNotNull(Headers.LAST_MODIFIED, properties.getLastModified(), response);
        Headers.writeIfNotNull(Headers.TRANSFER_ENCODING, properties.getTransferEncoding(), response);
        Headers.writeIfNotNull(Headers.CONTENT_ENCODING, properties.getContentEncoding(), response);
        Headers.writeIfNotNull(Headers.ETAG, properties.getETag(), response);
        Headers.writeIfNotNull(Headers.EXPIRES, properties.getExpires(), response);
        CacheControl cacheControl = properties.getCacheControl();
        if (cacheControl != null && !cacheControl.isEmpty()) {
            Headers.write(Headers.CACHE_CONTROL, cacheControl, response);
        }
        Headers.writeIfNotNull(Headers.CONTENT_TYPE, properties.getContentType(), response);
        Headers.writeIfNotNull(Headers.CONTENT_LANGUAGE, properties.getContentLanguage(), response);
        Headers.writeIfNotNull(Headers.AGE, properties.getAge(), response);
        Duration maxAge = properties.getMaxAge();
        if (maxAge != null) {
            Headers.write(Headers.EXPIRES, new DateTime().plus(maxAge), response);
        }
        Headers.writeIfNotNull(Headers.CONTENT_LOCATION, properties.getContentLocation(), response);
        Headers.writeIfNotNull(Headers.LOCATION, properties.getLocation(), response);
        Headers.writeIfNotNull(Headers.CONTENT_LENGTH, properties.getContentLength(), response);
    }

    @SuppressWarnings("deprecation")
    private Iterator<Acteur> annotationActeurs() {
        List<Acteur> acteurs = new LinkedList<>();
        Class<?> c = getClass();
        PathRegex regex = c.getAnnotation(PathRegex.class);
        ActeurFactory a = null;
        if (regex != null) {
            ActeurFactory af = a = getApplication().getDependencies().getInstance(ActeurFactory.class);
            acteurs.add(af.matchPath(regex.value()));
        }
        Path path = c.getAnnotation(Path.class);
        if (path != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.globPathMatch(path.value()));
        }
        Methods m = c.getAnnotation(Methods.class);
        if (m != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.matchMethods(m.value()));
        }
        MaximumPathLength len = c.getAnnotation(MaximumPathLength.class);
        if (len != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.maximumPathLength(len.value()));
        }
        BannedUrlParameters banned = c.getAnnotation(BannedUrlParameters.class);
        if (banned != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.banParameters(banned.value()));
        }
        RequireAtLeastOneUrlParameterFrom atLeastOneOf = c.getAnnotation(RequireAtLeastOneUrlParameterFrom.class);
        if (atLeastOneOf != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.requireAtLeastOneParameter(banned.value()));
        }
        RequiredUrlParameters params = c.getAnnotation(RequiredUrlParameters.class);
        if (params != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            switch (params.combination()) {
                case ALL:
                    acteurs.add(af.requireParameters(params.value()));
                    break;
                case AT_LEAST_ONE:
                    acteurs.add(af.requireAtLeastOneParameter(params.value()));
                    break;
                default:
                    throw new AssertionError(params.combination());
            }
        }
        RequireParametersIfMethodMatches methodParams = c.getAnnotation(RequireParametersIfMethodMatches.class);
        if (methodParams != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.requireParametersIfMethodMatches(methodParams.method(), methodParams.value()));
        }
        ParametersMustBeNumbersIfPresent nums = c.getAnnotation(ParametersMustBeNumbersIfPresent.class);
        if (nums != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.parametersMustBeNumbersIfTheyArePresent(nums.allowDecimal(), nums.allowNegative(), nums.value()));
        }
        MinimumRequestBodyLength minLength = c.getAnnotation(MinimumRequestBodyLength.class);
        if (minLength != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.minimumBodyLength(minLength.value()));
        }
        MaximumRequestBodyLength maxLength = c.getAnnotation(MaximumRequestBodyLength.class);
        if (maxLength != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.maximumBodyLength(maxLength.value()));
        }
        UrlParametersMayNotBeCombined combos = c.getAnnotation(UrlParametersMayNotBeCombined.class);
        if (combos != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.parametersMayNotBeCombined(combos.value()));
        }
        UrlParametersMayNotBeCombinedSets comboSet = c.getAnnotation(UrlParametersMayNotBeCombinedSets.class);
        if (comboSet != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            for (UrlParametersMayNotBeCombined c1 : comboSet.value()) {
                acteurs.add(af.parametersMayNotBeCombined(c1.value()));
            }
        }
        InjectParametersAsInterface paramsIface = c.getAnnotation(InjectParametersAsInterface.class);
        if (paramsIface != null) {
            Class<?> type = paramsIface.value();
            if (!type.isInterface()) {
                throw new IllegalArgumentException("Not an interface: " + type);
            }
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.injectRequestParametersAs(type));
        }
        BasicAuth auth = c.getAnnotation(BasicAuth.class);
        if (auth != null) {
            acteurs.add(Acteur.wrap(AuthenticationActeur.class, application.getDependencies()));
        }
        PageAnnotationHandler handler = getApplication().getDependencies().getInstance(PageAnnotationHandler.class);
        handler.processAnnotations(this, acteurs);
        InjectRequestBodyAs as = c.getAnnotation(InjectRequestBodyAs.class);
        if (as != null) {
            ActeurFactory af = a != null ? a : (a = getApplication().getDependencies().getInstance(ActeurFactory.class));
            acteurs.add(af.injectRequestBodyAsJSON(as.value()));
        }
        return acteurs.iterator();
    }

    private static final Set<Class<? extends Annotation>> annotationTypes;
    private static final Set<Class<? super Page>> annotatedPages = Sets.newConcurrentHashSet();
    static {
        Set<Class<? extends Annotation>> set = new HashSet<>();
        annotationTypes = ImmutableSet.<Class<? extends Annotation>>builder()
                .add(Path.class)
                .add(PathRegex.class)
                .add(Methods.class)
                .add(BannedUrlParameters.class)
                .add(InjectRequestBodyAs.class)
                .add(InjectParametersAsInterface.class)
                .add(MaximumRequestBodyLength.class)
                .add(MaximumPathLength.class)
                .add(MinimumRequestBodyLength.class)
                .add(ParametersMustBeNumbersIfPresent.class)
                .add(RequireParametersIfMethodMatches.class)
                .add(RequireAtLeastOneUrlParameterFrom.class)
                .add(UrlParametersMayNotBeCombined.class)
                .add(UrlParametersMayNotBeCombinedSets.class)
                .build();
        
    }

    @SuppressWarnings("element-type-mismatch")
    private boolean hasAnnotations() {
        if (annotatedPages.contains(getClass())) {
            return true;
        }
        Class<?> c = getClass();
        for (Class<? extends Annotation> type : annotationTypes) {
            if (c.getAnnotation(type) != null) {
                annotatedPages.add((Class<? super Page>) getClass());
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<Acteur> iterator() {
        assert getApplication() != null : "Application is null - called outside request?";
        if (hasAnnotations()) {
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
