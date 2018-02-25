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
package com.mastfrog.acteur;

import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.preconditions.Authenticated;
import com.mastfrog.acteur.preconditions.AuthenticatedIf;
import com.mastfrog.acteur.preconditions.BannedUrlParameters;
import com.mastfrog.acteur.preconditions.CORS;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import com.mastfrog.acteur.preconditions.InjectUrlParametersAs;
import com.mastfrog.acteur.preconditions.MaximumPathLength;
import com.mastfrog.acteur.preconditions.MaximumRequestBodyLength;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.MinimumRequestBodyLength;
import com.mastfrog.acteur.preconditions.PageAnnotationHandler;
import com.mastfrog.acteur.preconditions.PageAnnotationHandler.Registry;
import com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.preconditions.RequireAtLeastOneUrlParameterFrom;
import com.mastfrog.acteur.preconditions.RequireParametersIfMethodMatches;
import com.mastfrog.acteur.preconditions.RequiredUrlParameters;
import static com.mastfrog.acteur.preconditions.RequiredUrlParameters.Combination.AT_LEAST_ONE;
import com.mastfrog.acteur.preconditions.UrlParametersMayNotBeCombined;
import com.mastfrog.acteur.preconditions.UrlParametersMayNotBeCombinedSets;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.Ordered;
import com.mastfrog.settings.Settings;
import java.util.List;
import javax.inject.Inject;

/**
 * Processes the page annotations in com.mastfrog.acteur.preconditions and adds
 * acteurs to the list appropriately. Do not use directly.
 *
 * @author Tim Boudreau
 */
@Ordered(0)
public final class BuiltInPageAnnotationHandler extends PageAnnotationHandler {

    private final Dependencies deps;
    private final ActeurFactory af;
    private final Settings settings;

    @SuppressWarnings("deprecation")
    private static final Class<?>[] TYPES = new Class<?>[]{Authenticated.class, AuthenticatedIf.class,
        Path.class, Methods.class, MaximumPathLength.class, BannedUrlParameters.class,
        RequireAtLeastOneUrlParameterFrom.class, RequiredUrlParameters.class,
        RequireParametersIfMethodMatches.class, ParametersMustBeNumbersIfPresent.class,
        MinimumRequestBodyLength.class, MaximumRequestBodyLength.class,
        UrlParametersMayNotBeCombined.class, UrlParametersMayNotBeCombinedSets.class, CORS.class,
        InjectUrlParametersAs.class, com.mastfrog.acteur.preconditions.BasicAuth.class, InjectRequestBodyAs.class
    };

    @Inject
    BuiltInPageAnnotationHandler(Registry registry, Dependencies deps, ActeurFactory af, Settings settings) {
        super(registry, TYPES);
        this.deps = deps;
        this.af = af;
        this.settings = settings;
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T extends Page> boolean processAnnotations(T page, List<? super Acteur> acteurs) {
        Class<?> c = page.getClass();
        PathRegex regex = c.getAnnotation(PathRegex.class);
        int oldSize = acteurs.size();
        if (regex != null) {
            acteurs.add(af.matchPath(regex.decode(), regex.value()));
        }
        Path path = c.getAnnotation(Path.class);
        if (path != null) {
            acteurs.add(af.globPathMatch(path.value()));
        }
        Methods m = c.getAnnotation(Methods.class);
        if (m != null) {
            acteurs.add(af.matchMethods(m.value()));
        }
        MaximumPathLength len = c.getAnnotation(MaximumPathLength.class);
        if (len != null) {
            acteurs.add(af.maximumPathLength(len.value()));
        }
        BannedUrlParameters banned = c.getAnnotation(BannedUrlParameters.class);
        if (banned != null) {
            acteurs.add(af.banParameters(banned.value()));
        }
        RequireAtLeastOneUrlParameterFrom atLeastOneOf = c.getAnnotation(RequireAtLeastOneUrlParameterFrom.class);
        if (atLeastOneOf != null) {
            acteurs.add(af.requireAtLeastOneParameter(atLeastOneOf.value()));
        }
        RequiredUrlParameters params = c.getAnnotation(RequiredUrlParameters.class);
        if (params != null) {
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
            acteurs.add(af.requireParametersIfMethodMatches(methodParams.method(), methodParams.value()));
        }
        ParametersMustBeNumbersIfPresent nums = c.getAnnotation(ParametersMustBeNumbersIfPresent.class);
        if (nums != null) {
            acteurs.add(af.parametersMustBeNumbersIfTheyArePresent(nums.allowDecimal(), nums.allowNegative(), nums.value()));
        }
        MinimumRequestBodyLength minLength = c.getAnnotation(MinimumRequestBodyLength.class);
        if (minLength != null) {
            acteurs.add(af.minimumBodyLength(minLength.value()));
        }
        MaximumRequestBodyLength maxLength = c.getAnnotation(MaximumRequestBodyLength.class);
        if (maxLength != null) {
            acteurs.add(af.maximumBodyLength(maxLength.value()));
        }
        UrlParametersMayNotBeCombined combos = c.getAnnotation(UrlParametersMayNotBeCombined.class);
        if (combos != null) {
            acteurs.add(af.parametersMayNotBeCombined(combos.value()));
        }
        UrlParametersMayNotBeCombinedSets comboSet = c.getAnnotation(UrlParametersMayNotBeCombinedSets.class);
        if (comboSet != null) {
            for (UrlParametersMayNotBeCombined c1 : comboSet.value()) {
                acteurs.add(af.parametersMayNotBeCombined(c1.value()));
            }
        }
        InjectUrlParametersAs paramsIface = c.getAnnotation(InjectUrlParametersAs.class);
        if (paramsIface != null) {
            Class<?> type = paramsIface.value();
            acteurs.add(af.injectRequestParametersAs(type));
        }
        CORS cors = c.getAnnotation(CORS.class);
        if (cors != null) {
            acteurs.add(Acteur.wrap(CORSResource.CorsHeaders.class, deps));
        }
        boolean hasAuth = false;
        @SuppressWarnings("deprecation")
        com.mastfrog.acteur.preconditions.BasicAuth auth = c.getAnnotation(com.mastfrog.acteur.preconditions.BasicAuth.class);
        if (auth != null) {
            acteurs.add(Acteur.wrap(AuthenticationActeur.class, deps));
        }
        Authenticated auth2 = c.getAnnotation(Authenticated.class);
        if (!hasAuth && auth2 != null) {
            acteurs.add(Acteur.wrap(AuthenticationActeur.class, deps));
        }
        AuthenticatedIf authIf = c.getAnnotation(AuthenticatedIf.class);
        if (!hasAuth && authIf != null) {
            if (settings.getBoolean(authIf.setting(), false)) {
                acteurs.add(Acteur.wrap(AuthenticationActeur.class, deps));
            }
        }
        InjectRequestBodyAs as = c.getAnnotation(InjectRequestBodyAs.class);
        if (as != null) {
            acteurs.add(af.injectRequestBodyAsJSON(as.value()));
        }
        return oldSize != acteurs.size();
    }
}
