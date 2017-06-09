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

import com.google.common.net.MediaType;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.util.HttpMethod;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.url.Path;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Strings;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.netbeans.validation.api.InvalidInputException;
import org.netbeans.validation.api.Problem;

/**
 * Factory for standard Acteur implementations, mainly used to determine if a
 * request is valid (matches a URL, is using a supported HTTP method, etc.).
 * Usage model: Ask for this in your {@link Page} constructor and use it to add
 * acteurs.
 * <i><b>Almost all methods on this class can be used via annotations, so using
 * this class directly is rare post Acteur 1.4</b></i>.
 *
 * @author Tim Boudreau
 */
@Singleton
public class ActeurFactory {

    @Inject
    private Dependencies deps;
    @Inject
    private Charset charset;
    @Inject
    private PatternAndGlobCache cache;

    @Inject
    private Provider<HttpEvent> event;
    /**
     * Reject the request if it is not one of the passed HTTP methods
     *
     * @param methods Methods
     * @return An Acteur that can be used in a page
     * @deprecated Use &#064Methods instead - it is self-documenting
     */
    @Deprecated
    public Acteur matchMethods(final Method... methods) {
        return matchMethods(false, methods);
    }

    /**
     * Reject the request if it is not an allowed HTTP method, optionally
     * including information in the response, or simply rejecting the request
     * and allowing the next page a crack at it.
     *
     * @param notSupp If true, respond with METHOD_NOT_ALLOWED and the ALLOW
     * header set
     * @param methods The http methods which are allowed
     * @return An Acteur
     */
    public Acteur matchMethods(final boolean notSupp, final Method... methods) {
        if (methods.length == 1) {
            return new MatchMethod(event, notSupp, charset, methods[0]);
        }
        return new MatchMethods(event, notSupp, charset, methods);
    }

    private static class MatchMethods extends Acteur {

        private final Provider<HttpEvent> deps;
        private final boolean notSupp;
        private final Charset charset;
        private final Method[] methods;

        MatchMethods(Provider<HttpEvent> deps, boolean notSupp, Charset charset, Method... methods) {
            this.deps = deps;
            this.notSupp = notSupp;
            this.charset = charset;
            this.methods = methods;
        }
        
        private boolean hasMethod(HttpMethod m) {
            for (Method mm : methods) {
                if (mm == m || mm.equals(m)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            HttpEvent event = deps.get();
            boolean hasMethod = hasMethod(event.method());
            add(Headers.ALLOW, methods);
            if (notSupp && !hasMethod) {
                add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                return new Acteur.RespondWith(new Err(HttpResponseStatus.METHOD_NOT_ALLOWED, "405 Method "
                        + event.method() + " not allowed.  Accepted methods are "
                        + Headers.ALLOW.toCharSequence(methods) + "\n"));
            }
            com.mastfrog.acteur.State result = hasMethod ? new Acteur.ConsumedState() : new Acteur.RejectedState();
            return result;
        }

        @Override
        public String toString() {
            return "Match Methods " + Arrays.asList(methods);
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Methods", methods);
        }
    }

    private static class MatchMethod extends Acteur {
        private final Provider<HttpEvent> deps;
        private final boolean notSupp;
        private final Charset charset;
        private final Method[] method;

        MatchMethod(Provider<HttpEvent> deps, boolean notSupp, Charset charset, Method... method) {
            this.deps = deps;
            this.notSupp = notSupp;
            this.charset = charset;
            this.method = method;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            HttpEvent event = deps.get();
            HttpMethod mth = event.method();
            boolean hasMethod = mth == method[0] || method[0].equals(event.method());
            add(Headers.ALLOW, method);
            if (notSupp && !hasMethod) {
                add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                return new Acteur.RespondWith(new Err(HttpResponseStatus.METHOD_NOT_ALLOWED, "405 Method "
                        + event.method() + " not allowed.  Accepted methods are "
                        + Headers.ALLOW.toString(method) + "\n"));
            }
            com.mastfrog.acteur.State result = hasMethod ? new Acteur.ConsumedState() : new Acteur.RejectedState();
            return result;
        }

        @Override
        public String toString() {
            return "Match Method " + Arrays.toString(method);
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Method", method);
        }
    }

    public Acteur exactPathLength(final int length) {
        Checks.nonNegative("length", length);
        return new Acteur() {
            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent event = ActeurFactory.this.event.get();
                if (event.path().getElements().length == length) {
                    return new RejectedState();
                } else {
                    return new ConsumedState();
                }
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Path-element-count", length);
            }
        };
    }

    public Acteur minimumPathLength(final int length) {
        Checks.nonZero("length", length);
        Checks.nonNegative("length", length);
        return new Acteur() {
            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent event = ActeurFactory.this.event.get();
                if (event.path().getElements().length < length) {
                    return new RejectedState();
                } else {
                    return new ConsumedState();
                }
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Minimum Path Length", length);
            }
        };
    }

    public Acteur maximumPathLength(final int length) {
        Checks.nonZero("length", length);
        Checks.nonNegative("length", length);
        return new Acteur() {
            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent event = ActeurFactory.this.event.get();
                if (event.path().getElements().length > length) {
                    return new Acteur.RejectedState();
                } else {
                    return new Acteur.ConsumedState();
                }
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Maximum Path Length", length);
            }
        };
    }

    public Acteur redirect(String location) throws URISyntaxException {
        return redirect(location, HttpResponseStatus.SEE_OTHER);
    }

    public Acteur redirect(String location, HttpResponseStatus status) throws URISyntaxException {
        Checks.notNull("location", location);
        Checks.notNull("status", status);
        switch (status.code()) {
            case 300:
            case 301:
            case 302:
            case 303:
            case 305:
            case 307:
                break;
            default:
                throw new IllegalArgumentException(status + " is not a redirect");
        }
        return new Redirect(location, status);
    }

    private static final class Redirect extends Acteur {

        private final URI location;
        private final HttpResponseStatus status;

        private Redirect(String location, HttpResponseStatus status) throws URISyntaxException {
            this.location = new URI(location);
            this.status = status;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            add(Headers.LOCATION, location);
            return new RespondWith(status, "Redirecting to " + location);
        }

    }

    /**
     * Creates an Acteur which will read the request body, construct an object
     * from it, and include it for injection into later Acteurs in the chain.
     *
     * @param <T>
     * @param type The object type
     * @return An acteur
     */
    public <T> Acteur injectRequestBodyAsJSON(final Class<T> type) {
        return new InjectBody<>(deps, type);
    }

    @Description("Injects the body as a specific type")
    private static final class InjectBody<T> extends Acteur {

        private final Dependencies deps;
        private final Class<T> type;

        InjectBody(Dependencies deps, Class<T> type) {
            this.deps = deps;
            this.type = type;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            final ContentConverter converter = deps.getInstance(ContentConverter.class);
            HttpEvent evt = deps.getInstance(HttpEvent.class);
            try {
                MediaType mt = evt.header(Headers.CONTENT_TYPE);
                if (mt == null) {
                    mt = MediaType.ANY_TYPE;
                }
                try {
                    T obj = converter.readObject(evt.content(), mt, type);
                    return new Acteur.ConsumedLockedState(obj);
                } catch (InvalidInputException e) {
                    List<String> pblms = new LinkedList<>();
                    for (Problem p : e.getProblems()) {
                        pblms.add(p.getMessage());
                    }
                    return new Acteur.RespondWith(Err.badRequest("Invalid data").put("problems", pblms));
                }
            } catch (IOException ex) {
//                Logger.getLogger(ActeurFactory.class.getName()).log(Level.SEVERE, null, ex);
                return new Acteur.RespondWith(Err.badRequest("Bad or no JSON\n" + stackTrace(ex)));
            }
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Expects JSON Request Body", true);
        }
    }

    private static String stackTrace(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        t.printStackTrace(ps);
        return new String(baos.toByteArray());
    }

    /**
     * Create an acteur which will take the request parameters, turn them into
     * an implementation of some interface and include that in the set of
     * objects later Acteurs in the chain can request for injection.
     * <p/>
     * Note that you may need to include the type in your application's
     * <code>&#064;ImplicitBindings</code> annotation for Guice to allow your
     * type to be injected.
     * <p/>
     * The type must be an interface type, and its methods should correspond
     * exactly to the parameter names desired.
     */
    public <T> Acteur injectRequestParametersAs(final Class<T> type) {
        return new InjectParams<>(deps, type);
    }

    @Description("Inject request parameters as a type")
    static class InjectParams<T> extends Acteur {

        private final Dependencies deps;
        private final Class<T> type;

        InjectParams(Dependencies deps, Class<T> type) {
            this.deps = deps;
            this.type = type;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            HttpEvent evt = deps.getInstance(HttpEvent.class);
            ContentConverter converter = deps.getInstance(ContentConverter.class);
            try {
                T obj = converter.createObjectFor(evt.urlParametersAsMap(), type);
                if (obj != null) {
                    return new Acteur.ConsumedLockedState(obj);
                }
            } catch (InvalidInputException ex) {
                setState(new Acteur.RespondWith(Err.badRequest(ex.getProblems().toString())));
            }
            return new Acteur.RejectedState();
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("type", type.getName());
        }

        @Override
        public String toString() {
            return "Inject request parameters as " + type.getName();
        }
    }

    /**
     * Create an Acteur which simply always responds with the given HTTP status.
     *
     * @param status A status
     * @return An acteur
     */
    public Acteur responseCode(final HttpResponseStatus status) {
        @Description("Send a response code")
        class SendResponseCode extends Acteur {

            @Override
            public com.mastfrog.acteur.State getState() {
                return new Acteur.RespondWith(status);
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Responds With", status);
            }
        }
        return new SendResponseCode();
    }

    /**
     * Reject the request unless certain URL parameters are present
     *
     * @param names The parameter names
     * @return An acteur
     */
    public Acteur requireParameters(final String... names) {
        return new RequireParameters(event, charset, names);
    }

    static class RequireParameters extends Acteur {

        private final Provider<HttpEvent> deps;
        private final Charset charset;
        private final String[] names;

        RequireParameters(Provider<HttpEvent> deps, Charset charset, String... names) {
            this.deps = deps;
            this.charset = charset;
            this.names = names;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            HttpEvent event = deps.get();
            for (String nm : names) {
                String val = event.urlParameter(nm);
                if (val == null) {
                    add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                    return new Acteur.RespondWith(Err.badRequest("Missing URL parameter '" + nm + "'\n"));
                }
            }
            return new Acteur.ConsumedState();
        }

        @Override
        public String toString() {
            return "Require Parameters " + Arrays.asList(names);
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("requiredParameters", names);
        }
    }

    public Acteur parametersMayNotBeCombined(final String... names) {
        @Description("Requires that parameters not appear together in the URL")
        class RequireParametersNotBeCombined extends Acteur {

            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent event = ActeurFactory.this.event.get();
                String first = null;
                for (String nm : names) {
                    String val = event.urlParameter(nm);
                    if (val != null) {
                        if (first == null) {
                            first = nm;
                        } else {
                            add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                            return new Acteur.RespondWith(Err.badRequest(
                                    "Parameters may not contain both '"
                                    + first + "' and '" + nm + "'\n"));
                        }
                    }
                }
                return new Acteur.ConsumedState();
            }

            @Override
            public String toString() {
                return "Parameters may not be combined: "
                        + Strings.toString(Arrays.asList(names));
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("requiredParameters", names);
            }
        }
        return new RequireParametersNotBeCombined();
    }

    public Acteur parametersMustBeNumbersIfTheyArePresent(final boolean allowDecimal, final boolean allowNegative, final String... names) {
        @Description("Requires that parameters be numbers if they are present")
        class NumberParameters extends Acteur {

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("URL parameters must be numbers if present" + (allowNegative ? "(negative allowed) " : ("(must be non-negative) "))
                        + (allowDecimal ? "(decimal-allowed)" : "(must be integers)") + (""), names);
            }

            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent evt = event.get();
                for (String name : names) {
                    String p = evt.urlParameter(name);
                    if (p != null) {
                        boolean decimalSeen = false;
                        for (int i = 0; i < p.length(); i++) {
                            switch (p.charAt(i)) {
                                case '0':
                                case '1':
                                case '2':
                                case '3':
                                case '4':
                                case '5':
                                case '6':
                                case '7':
                                case '8':
                                case '9':
                                    break;
                                case '-':
                                    if (i == 0 && allowNegative) {
                                        break;
                                    }
                                //fall thru
                                case '.':
                                    if (!decimalSeen && allowDecimal) {
                                        decimalSeen = true;
                                        break;
                                    }
                                //fall thru
                                default:
                                    return new RespondWith(Err.badRequest(
                                            "Parameter " + name + " is not a legal number here: '" + p + "'\n"));
                            }
                        }
                    }
                }
                return new Acteur.ConsumedState();
            }
        }
        return new NumberParameters();
    }

    /**
     * Reject request which contain the passed parameters
     *
     * @param names A list of parameter names for the URL
     * @return An acteur
     */
    public Acteur banParameters(final String... names) {
        Arrays.sort(names);
        @Description("Requires that parameters not be present")
        class BanParameters extends Acteur {

            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent evt = event.get();
                for (Map.Entry<String, String> e : evt.urlParametersAsMap().entrySet()) {
                    if (Arrays.binarySearch(names, e.getKey()) >= 0) {
                        return new RespondWith(Err.badRequest(
                                e.getKey() + " not allowed in parameters\n"));
                    }
                }
                return new ConsumedState();
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Illegal Parameters", names);
            }
        }
        return new BanParameters();
    }

    /**
     * Reject the request if none of the passed parameter names are present
     *
     * @param names
     * @return
     */
    public Acteur requireAtLeastOneParameter(final String... names) {
        @Description("Requires that at least one specified parameter be present")
        class RequireAtLeastOneParameter extends Acteur {

            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent event = ActeurFactory.this.event.get();
                for (String nm : names) {
                    String val = event.urlParameter(nm);
                    if (val != null) {
                        return new ConsumedState();
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < names.length; i++) {
                    sb.append("'").append(names[i]).append("'");
                    if (i != names.length - 1) {
                        sb.append(", ");
                    }
                }
                return new RespondWith(Err.badRequest("Must have at least one of " + sb + " as parameters\n"));
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("At least one parameter required", names);
            }

            @Override
            public String toString() {
                return "Require Parameters " + Arrays.asList(names);
            }
        }
        return new RequireAtLeastOneParameter();
    }

    /**
     * Reject the request if HttpEvent.path().toString() does not match one
 of the passed regular expressions
     *
     * @param regexen Regexen
     * @return An acteur
     * @deprecated Use &#064PathRegex instead - it is self-documenting
     */
    @Deprecated
    public Acteur matchPath(final String... regexen) {
        if (regexen.length == 1) {
            String exactPath = cache.exactPathForRegex(regexen[0]);
            if (exactPath != null) {
                return new ExactMatchPath(event, exactPath);
            }
        }
        return new MatchPath(event, cache, regexen);
    }

    static class ExactMatchPath extends Acteur {

        private final String path;
        private final Provider<HttpEvent> deps;

        ExactMatchPath(Provider<HttpEvent> deps, String path) {
            this.path = path.length() > 1 && path.charAt(0) == '/' ? path.substring(1) : path;
            this.deps = deps;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            HttpEvent event = deps.get();
            if (path.equals(event.path().toString())) {
                return new ConsumedState();
            }
            return new RejectedState();
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Exactly match the URL path", path);
        }

        @Override
        public String toString() {
            return "Exactly match the URL path " + path;
        }
    }

    static final class MatchPath extends Acteur {

        private final Provider<HttpEvent> deps;
        private final PatternAndGlobCache cache;
        private final String[] regexen;

        MatchPath(Provider<HttpEvent> deps, PatternAndGlobCache cache, String... regexen) {
            if (regexen.length == 0) {
                throw new IllegalArgumentException("No regular expressions provided");
            }
            this.deps = deps;
            this.cache = cache;
            this.regexen = regexen;
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            HttpEvent event = deps.get();
            for (String regex : regexen) {
                Pattern p = cache.getPattern(regex);
                boolean matches = p.matcher(event.path().toString()).matches();
                if (matches) {
                    return new ConsumedState();
                }
            }
            return new RejectedState();
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("URL Patterns", regexen);
        }

        @Override
        public String toString() {
            return "Match path " + Arrays.asList(regexen);
        }
    }

    @Singleton
    static class PatternAndGlobCache {

        private final Map<String, Boolean> matchCache = new ConcurrentHashMap<>();
        private final Map<String, String> exactPathForRegex = new ConcurrentHashMap<>();
        private static final String INVALID = "::////";

        String exactPathForRegex(String regex) {
            String result = exactPathForRegex.get(regex);
            if (result != null) {
                if (!INVALID.equals(result)) {
                    return result;
                } else {
                    return null;
                }
            }
            StringBuilder sb = new StringBuilder();
            char[] chars = regex.toCharArray();
            boolean precedingWasBackslash = false;
            boolean endMarkerFound = false;
            boolean startMarkerFound = false;
            loop:
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                if (i == 0 && c == '^') {
                    continue;
                }
                if (i == chars.length - 1 && c == '$') {
                    endMarkerFound = true;
                    continue;
                }
                if (i == 0 && c == '^') {
                    startMarkerFound = true;
                }
                switch (c) {
                    case '\\':
                        if (i != chars.length - 1) {
                            precedingWasBackslash = true;
                        }
                        break;
                    case '*':
                        if (precedingWasBackslash) {
                            sb.append(c);
                            continue;
                        }
                    case '[':
                    case '+':
                    case '?':
                    case '^':
                    case '$':
                    case '&':
                        exactPathForRegex.put(regex, INVALID);
                        return null;
                    default :
                        sb.append(c);
                }
                precedingWasBackslash = c == '\\';
            }
            if (!endMarkerFound || !startMarkerFound) {
                        exactPathForRegex.put(regex, INVALID);
                        return null;
            }
            if (sb.length() > 0 && sb.charAt(0) == '/') {
                result = sb.substring(1);
            } else {
                result = sb.toString();
            }
            exactPathForRegex.put(regex, result);
            return result;
        }

        boolean isExactGlob(String s) {
            Boolean match = matchCache.get(s);
            if (match != null) {
                return match;
            }
            boolean result = true;
            for (char c : s.toCharArray()) {
                if ('*' == c) {
                    result = false;
                    break;
                }
            }
            matchCache.put(s, result);
            return result;
        }

        private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

        Pattern getPattern(String regex) {
            Pattern result = patternCache.get(regex);
            if (result == null) {
                result = Pattern.compile(regex);
                patternCache.put(regex, result);
            }
            return result;
        }
    }

    /**
     * Checks the IF_NONE_MATCH header and compares it with the value from the
     * current Page's getETag() method. If it matches, forces a NOT_MODIFIED
     * http response and ends processing of the chain of Acteurs.
     *
     * @return An acteur
     */
    public Acteur sendNotModifiedIfETagHeaderMatches() {
        return Acteur.wrap(CheckIfNoneMatchHeader.class, deps);
    }

    public Class<? extends Acteur> sendNotModifiedIfETagHeaderMatchesType() {
        return CheckIfNoneMatchHeader.class;
    }

    static String patternFromGlob(String pattern) {
        if (pattern.length() > 0 && pattern.charAt(0) == '/') {
            pattern = pattern.substring(1);
        }
        StringBuilder match = new StringBuilder("^\\/?");
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '$':
                case '.':
                case '{':
                case '}':
                case '[':
                case ']':
                case ')':
                case '(':
                case '^':
                case '/':
                    match.append("\\").append(c);
                    break;
                case '*':
                    match.append("[^\\/]*?");
                    break;
                case '?':
                    match.append("[^\\/]?");
                    break;
                default:
                    match.append(c);
            }
        }
        match.append("$");
        return match.toString();
    }

    public Acteur globPathMatch(String... patterns) {
        if (patterns.length == 1 && cache.isExactGlob(patterns[0])) {
            String pattern = patterns[0];
            if (pattern.length() > 0 && pattern.charAt(0) == '/') {
                pattern = pattern.substring(1);
            }
            return new ExactMatchPath(event, patterns[0]);
        }
        String[] rexen = new String[patterns.length];
        for (int i = 0; i < rexen.length; i++) {
            rexen[i] = patternFromGlob(patterns[i]);
        }
        return matchPath(rexen);
    }

    /**
     * Check the "If-Modified-Since" header and compares it to the current
     * Page's getLastModified (rounding milliseconds down). If the condition is
     * met, responds with NOT_MODIFIED.
     *
     * @return an Acteur
     */
    public Acteur sendNotModifiedIfIfModifiedSinceHeaderMatches() {
        return Acteur.wrap(CheckIfModifiedSinceHeader.class, deps);
    }

    public Class<? extends Acteur> sendNotModifiedIfIfModifiedSinceHeaderMatchesType() {
        return CheckIfModifiedSinceHeader.class;
    }

    /**
     * Check the "If-Unmodified-Since" header
     *
     * @return an Acteur
     */
    public Acteur preconditionFailedIfUnmodifiedSinceMatches() {
        return Acteur.wrap(CheckIfUnmodifiedSinceHeader.class, deps);
    }

    public Class<? extends Acteur> preconditionFailedIfUnmodifiedSinceMatchesType() {
        return CheckIfUnmodifiedSinceHeader.class;
    }

    public Acteur respondWith(int status) {
        return new ResponseCode(status);
    }

    public Acteur respondWith(int status, String msg) {
        return new ResponseCode(status, msg);
    }

    public Acteur respondWith(HttpResponseStatus status, String msg) {
        return new ResponseCode(status, msg);
    }

    public Acteur respondWith(HttpResponseStatus status) {
        return new ResponseCode(status);
    }

    private static final class ResponseCode extends Acteur {

        private final HttpResponseStatus code;
        private final String msg;

        ResponseCode(int code) {
            this(code, null);
        }

        ResponseCode(int code, String msg) {
            this(HttpResponseStatus.valueOf(code), msg);
        }

        ResponseCode(HttpResponseStatus code, String msg) {
            this.code = code;
            this.msg = msg;

        }

        ResponseCode(HttpResponseStatus code) {
            this(code, null);
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Always responds with", code.code() + " " + code.reasonPhrase());
        }

        @Override
        public com.mastfrog.acteur.State getState() {
            return msg == null ? new RespondWith(code) : new RespondWith(code, msg);
        }
    }

    public Acteur minimumBodyLength(final int length) {
        return new Acteur() {

            @Override
            public com.mastfrog.acteur.State getState() {
                try {
                    int val = event.get().content().readableBytes();
                    if (val < length) {
                        return new RespondWith(Err.badRequest("Request body must be > " + length + " characters"));
                    }
                    return new ConsumedState();
                } catch (IOException ex) {
                    return Exceptions.chuck(ex);
                }
            }
        };
    }

    public Acteur maximumBodyLength(final int length) {
        return new Acteur() {

            @Override
            public com.mastfrog.acteur.State getState() {
                try {
                    int val = event.get().content().readableBytes();
                    if (val > length) {
                        return new Acteur.RespondWith(new Err(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                                "Request body must be < " + length + " characters"));
                    }
                    return new Acteur.ConsumedState();
                } catch (IOException ex) {
                    return Exceptions.chuck(ex);
                }
            }
        };
    }

    public Acteur requireParametersIfMethodMatches(final Method method, final String... params) {
        Checks.notNull("method", method);
        Checks.notNull("params", params);
        Checks.notEmpty("params", Arrays.asList(params));
        class RequireParametersIfMethodMatches extends Acteur {

            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent evt = event.get();
                if (method.equals(evt.method())) {
                    if (!evt.urlParametersAsMap().keySet().containsAll(Arrays.asList(params))) {
                        return new RespondWith(Err.badRequest("Required parameters: "
                                + Arrays.asList(params)));
                    }
                }
                return new ConsumedState();
            }
        }
        return new RequireParametersIfMethodMatches();
    }

    public Acteur redirectEmptyPath(final String to) throws URISyntaxException {
        return redirectEmptyPath(Path.parse(to));
    }

    public Acteur redirectEmptyPath(final Path to) throws URISyntaxException {
        Checks.notNull("to", to);
        class MatchNothing extends Acteur {

            @Override
            public com.mastfrog.acteur.State getState() {
                HttpEvent evt = event.get();
                if (evt.path().toString().isEmpty()) {
                    PathFactory pf = deps.getInstance(PathFactory.class);
                    add(Headers.LOCATION, pf.toExternalPath(to).toURI());
                    return new RespondWith(SEE_OTHER);
                } else {
                    return new RejectedState();
                }
            }
        }
        return new MatchNothing();
    }

    public Acteur branch(final Class<? extends Acteur> ifTrue, final Class<? extends Acteur> ifFalse, final Test test) {
        class Brancher extends Acteur {

            @Override
            @SuppressWarnings("unchecked")
            public com.mastfrog.acteur.State getState() {
                boolean result = test.test(event.get());
                Chain<Acteur> chain = deps.getInstance(Chain.class);
                if (result) {
                    chain.add(ifTrue);
                } else {
                    chain.add(ifFalse);
                }
                return new ConsumedLockedState();
            }
        }
        return new Brancher();
    }

    /**
     * A test which can be performed on a request, for example, to decide about
     * branching
     */
    public interface Test {

        /**
         * Perform the test
         *
         * @param evt The request
         * @return The result of the test
         */
        public boolean test(HttpEvent evt);
    }
}
