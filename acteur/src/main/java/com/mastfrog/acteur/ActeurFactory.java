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
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur.Delegate;
import com.mastfrog.acteur.ResponseHeaders.ETagProvider;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.url.Path;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Strings;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.joda.time.DateTime;

/**
 * Factory for standard Acteur implementations, mainly used to determine if a
 * request is valid (matches a URL, is using a supported HTTP method, etc.).
 *
 * @author Tim Boudreau
 */
public class ActeurFactory {

    private final Dependencies deps;
    private final Charset charset;

    @Inject
    ActeurFactory(Dependencies deps, Charset charset) {
        this.deps = deps;
        this.charset = charset;
    }

    /**
     * Reject the request if it is not one of the passed HTTP methods
     *
     * @param methods Methods
     * @return An Acteur that can be used in a page
     */
    public Acteur matchMethods(final Method... methods) {
        boolean asserts = false;
        assert asserts = true;
        String type = "";
        if (asserts) {
            type = "(" + new Exception().getStackTrace()[1].getClassName() + ")";
        }
        return matchMethods(false, type, methods);
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
        boolean asserts = false;
        assert asserts = true;
        String type = "";
        if (asserts) {
            type = "(" + new Exception().getStackTrace()[1].getClassName() + ")";
        }
        return matchMethods(notSupp, type, methods);
    }

    private Acteur matchMethods(final boolean notSupp, final String typeName, final Method... methods) {
        class MatchMethods extends Acteur {

            @Override
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                boolean hasMethod = Arrays.asList(methods).contains(event.getMethod());
                add(Headers.ALLOW, methods);
                if (notSupp && !hasMethod) {
                    add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                    return new RespondWith(HttpResponseStatus.METHOD_NOT_ALLOWED, "405 Method "
                            + event.getMethod() + " not allowed.  Accepted methods are "
                            + Headers.ALLOW.toString(methods) + " " + typeName + "\n");
                }
                State result = hasMethod ? new ConsumedState() : new RejectedState();
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
        return new MatchMethods();
    }

    public Acteur exactPathLength(final int length) {
        Checks.nonNegative("length", length);
        return new Acteur() {
            @Override
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                if (event.getPath().getElements().length == length) {
                    return new Acteur.RejectedState();
                } else {
                    return new Acteur.ConsumedState();
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
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                if (event.getPath().getElements().length < length) {
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
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                if (event.getPath().getElements().length > length) {
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

        public State getState() {
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
        class InjectBody extends Acteur {

            @Override
            public State getState() {
                HttpEvent evt = deps.getInstance(HttpEvent.class);
                try {
                    T obj = evt.getContentAsJSON(type);
                    return new ConsumedLockedState(obj);
                } catch (IOException ex) {
                    Logger.getLogger(ActeurFactory.class.getName()).log(Level.SEVERE, null, ex);
                    return new RespondWith(400, "Bad or no JSON\n" + stackTrace(ex));
                }
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Expects JSON Request Body", true);
            }
        }
        return new InjectBody();
    }

    private String stackTrace(Throwable t) {
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
        class InjectParams extends Acteur {

            @Override
            public State getState() {
                HttpEvent evt = deps.getInstance(HttpEvent.class);
                T obj = evt.getParametersAs(type);
                if (obj != null) {
                    return new ConsumedLockedState(obj);
                }
                return new RejectedState();
            }
        }
        return new InjectParams();
    }

    /**
     * Create an Acteur which simply always responds with the given HTTP status.
     *
     * @param status A status
     * @return An acteur
     */
    public Acteur responseCode(final HttpResponseStatus status) {
        return new Acteur() {
            @Override
            public State getState() {
                return new RespondWith(status);
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Responds With", status);
            }
        };
    }

    /**
     * Reject the request unless certain URL parameters are present
     *
     * @param names The parameter names
     * @return An acteur
     */
    public Acteur requireParameters(final String... names) {
        class RequireParameters extends Acteur {

            @Override
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                for (String nm : names) {
                    String val = event.getParameter(nm);
                    if (val == null) {
                        add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                        return new RespondWith(HttpResponseStatus.BAD_REQUEST, "Missing URL parameter '" + nm + "'\n");
                    }
                }
                return new ConsumedState();
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
        return new RequireParameters();
    }

    public Acteur parametersMayNotBeCombined(final String... names) {
        class RequireParameters extends Acteur {

            @Override
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                String first = null;
                for (String nm : names) {
                    String val = event.getParameter(nm);
                    if (val != null) {
                        if (first == null) {
                            first = nm;
                        } else {
                            add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(charset));
                            return new Acteur.RespondWith(HttpResponseStatus.BAD_REQUEST,
                                    "Parameters may not contain both '"
                                    + first + "' and '" + nm + "'\n");
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
        return new RequireParameters();
    }

    public Acteur parametersMustBeNumbersIfTheyArePresent(final boolean allowDecimal, final boolean allowNegative, final String... names) {
        class NumberParameters extends Acteur {

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("URL parameters must be numbers if present" + (allowNegative ? "(negative allowed) " : ("(must be non-negative) "))
                        + (allowDecimal ? "(decimal-allowed)" : "(must be integers)") + (""), names);
            }

            public State getState() {
                HttpEvent evt = deps.getInstance(HttpEvent.class);
                for (String name : names) {
                    String p = evt.getParameter(name);
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
                                case '.':
                                    if (!decimalSeen && allowDecimal) {
                                        decimalSeen = true;
                                        break;
                                    }
                                default:
                                    return new RespondWith(HttpResponseStatus.BAD_REQUEST,
                                            "Parameter " + name + " is not a number: '" + p + "'\n");
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
        class BanParameters extends Acteur {

            public State getState() {
                HttpEvent evt = deps.getInstance(HttpEvent.class);
                for (Map.Entry<String, String> e : evt.getParametersAsMap().entrySet()) {
                    if (Arrays.binarySearch(names, e.getKey()) >= 0) {
                        return new RespondWith(HttpResponseStatus.BAD_REQUEST,
                                e.getKey() + " not allowed in parameters\n");
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
        class RequireAtLeastOneParameter extends Acteur {

            @Override
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                for (String nm : names) {
                    String val = event.getParameter(nm);
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
                return new RespondWith(HttpResponseStatus.BAD_REQUEST, "Must have at least one of " + sb + " as parameters\n");
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
    private static Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    private static Pattern getPattern(String regex) {
        Pattern result = patternCache.get(regex);
        if (result == null) {
            result = Pattern.compile(regex);
            patternCache.put(regex, result);
        }
        return result;
    }

    /**
     * Reject the request if HttpEvent.getPath().toString() does not match one
     * of the passed regular expressions
     *
     * @param regexen Regexen
     * @return An acteur
     */
    public Acteur matchPath(final String... regexen) {
        class MatchPath extends Acteur {

            @Override
            public State getState() {
                HttpEvent event = deps.getInstance(HttpEvent.class);
                for (String regex : regexen) {
                    Pattern p = getPattern(regex);
                    boolean matches = p.matcher(event.getPath().toString()).matches();
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
        return new MatchPath();
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

        public ResponseCode(int code) {
            this(code, null);
        }

        public ResponseCode(int code, String msg) {
            this(HttpResponseStatus.valueOf(code), msg);
        }

        public ResponseCode(HttpResponseStatus code, String msg) {
            this.code = code;
            this.msg = msg;

        }

        public ResponseCode(HttpResponseStatus code) {
            this(code, null);
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Always responds with", code.code() + " " + code.reasonPhrase());
        }

        @Override
        public State getState() {
            return msg == null ? new RespondWith(code) : new RespondWith(code, msg);
        }
    }

    private static class CheckIfNoneMatchHeader extends Acteur {

        @Inject
        CheckIfNoneMatchHeader(HttpEvent event, Page page) throws Exception {
            Checks.notNull("event", event);
            Checks.notNull("page", page);
            String etag = event.getHeader(Headers.IF_NONE_MATCH);
            String pageEtag = page.getResponseHeaders().getETag();
            if (etag != null && pageEtag != null) {
                if (etag.equals(pageEtag)) {
                    setState(new RespondWith(HttpResponseStatus.NOT_MODIFIED));
                // XXX workaround for peculiar problem with FileResource =
                    // not modified responses are leaving a hanging connection
                    setResponseBodyWriter(ChannelFutureListener.CLOSE);
                    return;
                }
            }
            setState(new ConsumedState());
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Supports If-None-Match header", true);
        }
    }

    private static class CheckIfUnmodifiedSinceHeader extends Acteur {

        @Inject
        CheckIfUnmodifiedSinceHeader(HttpEvent event, Page page) {
            DateTime dt = event.getHeader(Headers.IF_UNMODIFIED_SINCE);
            if (dt != null) {
                DateTime pageLastModified = page.getResponseHeaders().getLastModified();
                if (pageLastModified != null) {
                    boolean modSince = pageLastModified.getMillis() > dt.getMillis();
                    if (modSince) {
                        setState(new RespondWith(HttpResponseStatus.PRECONDITION_FAILED));
                        return;
                    }
                }
            }
            setState(new ConsumedState());
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Supports If-Unmodified-Since", true);
        }
    }

    private static class CheckIfModifiedSinceHeader extends Acteur {

        @Inject
        CheckIfModifiedSinceHeader(HttpEvent event, Page page) {
            DateTime lastModifiedMustBeNewerThan = event.getHeader(Headers.IF_MODIFIED_SINCE);
            DateTime pageLastModified = page.getResponseHeaders().getLastModified();

            boolean notModified = lastModifiedMustBeNewerThan != null && pageLastModified != null;
            if (notModified) {
                pageLastModified = pageLastModified.withMillisOfSecond(0);
                notModified = pageLastModified.getMillis() <= lastModifiedMustBeNewerThan.getMillis();
            }

            if (notModified) {
                setResponseCode(HttpResponseStatus.NOT_MODIFIED);
                setState(new RespondWith(HttpResponseStatus.NOT_MODIFIED));
                // XXX workaround for peculiar problem with FileResource =
                // not modified responses are leaving a hanging connection
                setResponseBodyWriter(ChannelFutureListener.CLOSE);
                return;
            }
            setState(new ConsumedState());
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Supports If-Modified-Since header", true);
        }
    }

    /**
     * Compute the etag on demand, and send a not modified header if the one in
     * the request matches the one provided by the passed ETagComputer.
     *
     * @param computer The thing that computes an ETag
     * @return An acteur
     */
    public Acteur sendNotModifiedIfETagHeaderMatches(final ETagComputer computer) {
        class A extends Acteur implements ETagProvider {

            @Override
            public State getState() {
                Page page = deps.getInstance(Page.class);
                page.getResponseHeaders().setETagProvider(this);
                CheckIfNoneMatchHeader h = deps.getInstance(CheckIfNoneMatchHeader.class);
                State result = h.getState();
                getResponse().merge(h.getResponse());
                return result;
            }

            @Override
            public String getETag() {
                try {
                    return computer.getETag();
                } catch (Exception ex) {
                    return Exceptions.<String>chuck(ex);
                }
            }

            @Override
            public void describeYourself(Map<String, Object> into) {
                into.put("Supports If-None-Match", true);
            }
        }
        return new A();
    }

    public Acteur requireParametersIfMethodMatches(final Method method, final String... params) {
        Checks.notNull("method", method);
        Checks.notNull("params", params);
        Checks.notEmpty("params", Arrays.asList(params));
        class RequireParametersIfMethodMatches extends Acteur {
            public State getState() {
                HttpEvent evt = deps.getInstance(HttpEvent.class);
                if (method.equals(evt.getMethod())) {
                    if (!evt.getParametersAsMap().keySet().containsAll(Arrays.asList(params))) {
                        setState(new RespondWith(BAD_REQUEST, "Required parameters: "
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
            public State getState() {
                HttpEvent evt = deps.getInstance(HttpEvent.class);
                if (evt.getPath().toString().isEmpty()) {
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
        class Brancher extends Acteur implements Delegate {
            private Acteur delegate;
            @Override
            public State getState() {
                return getDelegate().getState();
            }

            @Override
            ResponseImpl getResponse() {
                return getDelegate().getResponse();
            }

            @Override
            public Acteur getDelegate() {
                if (delegate != null) {
                    return delegate;
                }
                boolean result = test.test(deps.getInstance(HttpEvent.class));
                if (result) {
                    return delegate = deps.getInstance(ifTrue);
                } else {
                    return delegate = deps.getInstance(ifFalse);
                }
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
         * @param evt The request
         * @return The result of the test
         */
        public boolean test(HttpEvent evt);
    }

    /**
     * Lazily computes a page's (or something's) etag
     */
    public interface ETagComputer {

        /**
         * Compute the etag for whatever context we are called in
         *
         * @return
         * @throws Exception if something goes wrong
         */
        public String getETag() throws Exception;
    }
}
