/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.util.Strings;
import io.netty.handler.codec.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
class EarlyPages {

    private final PathPatterns pp = new PathPatterns();
    private final Map<Method, ByMethod> all = new HashMap<>();
    Set<MethodPath> matchesCache = Sets.newConcurrentHashSet();
    Set<MethodPath> nonMatchesCache = Sets.newConcurrentHashSet();

    public boolean match(HttpRequest req) {
        MethodPath mp = new MethodPath(req);
        if (nonMatchesCache.contains(mp)) {
            return false;
        }
        if (matchesCache.contains(mp)) {
            return true;
        }
        ByMethod bm = all.get(mp.method);
        if (bm == null) {
            nonMatchesCache.add(mp);
            return false;
        }
        if (bm.match(mp.path)) {
            matchesCache.add(mp);
            return true;
        } else {
            nonMatchesCache.add(mp);
            return false;
        }
    }

    void add(Class<? extends Page> type) {
        Methods methods = type.getAnnotation(Methods.class);
        if (methods != null) {
            for (Method mth : methods.value()) {
                ByMethod by = all.get(mth);
                if (by == null) {
                    by = new ByMethod();
                    all.put(mth, by);
                }
                by.add(type);
            }
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Method, ByMethod> e : all.entrySet()) {
            sb.append(e.getKey()).append(":\n");
            sb.append(e.getValue());
        }
        return sb.toString();
    }

    private final class ByMethod {

        private final Set<Pattern> patterns = new HashSet<>();
        private final Set<String> exacts = new HashSet<>();

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("  exact: ").append(Strings.join(", ", exacts));
            List<String> l = new ArrayList<>();
            for (Pattern p : patterns) {
                l.add(p.pattern());
            }
            sb.append("  patns: ").append(Strings.join(", ", l));
            return sb.append('\n').toString();
        }

        boolean match(String trimmedUri) {
            if (exacts.contains(trimmedUri)) {
                return true;
            }
            for (Pattern p : patterns) {
                boolean isMatch = p.matcher(trimmedUri).matches();
                if (isMatch) {
                    return true;
                }
            }
            return false;
        }

        void add(Class<? extends Page> page) {
            Path pth = page.getAnnotation(Path.class);
            if (pth != null) {
                for (String pat : pth.value()) {
                    if (pp.isExactGlob(pat)) {
                        exacts.add(trimLeadingAndTrailingSlashes(pat));
                    } else {
                        patterns.add(pp.getPattern(PathPatterns.patternFromGlob(pat)));
                    }
                }
            }
            PathRegex rx = page.getAnnotation(PathRegex.class);
            if (rx != null) {
                for (String regex : rx.value()) {
                    String exact = pp.exactPathForRegex(regex);
                    if (exact != null) {
                        exacts.add(exact);
                    } else {
                        patterns.add(pp.getPattern(regex));
                    }
                }
            }
        }
    }

    private static String trimLeadingAndTrailingSlashes(String pat) {
        if (pat.length() > 1) {
            if (pat.charAt(0) == '/') {
                pat = pat.substring(1);
            }
            if (pat.length() > 1 && pat.charAt(pat.length() - 1) == '/') {
                pat = pat.substring(0, pat.length() - 1);
            }
        }
        return pat;
    }

    private static String trimQuery(String uri) {
        int ix = uri.indexOf('?');
        if (ix >= 0) {
            uri = uri.substring(0, ix);
        }
        return uri;
    }

    private static final class MethodPath {

        private final Method method;
        private final String path;

        MethodPath(Method method, String path) {
            this.method = method;
            this.path = path;
        }

        MethodPath(HttpRequest req) {
            path = trimLeadingAndTrailingSlashes(trimQuery(req.uri()));
            method = Method.get(req);
        }

        @Override
        public int hashCode() {
            return path.hashCode() + (71 * (method.ordinal()));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            return obj instanceof MethodPath && ((MethodPath) obj).method == method
                    && ((MethodPath) obj).path.equals(path);
        }

        public String toString() {
            return method.name() + ":" + path;
        }
    }
}
