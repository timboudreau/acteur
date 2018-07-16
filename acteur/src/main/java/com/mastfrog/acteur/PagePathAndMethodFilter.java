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
import com.mastfrog.acteur.Page.PathPatternInfo;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.AlignedText;
import io.netty.handler.codec.http.HttpRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
class PagePathAndMethodFilter {

    private final PathPatterns pp = new PathPatterns();
    private final Map<Method, ByMethod> all = new EnumMap<>(Method.class);
    Set<MethodPath> matchesCache = Sets.newConcurrentHashSet();
    Set<MethodPath> nonMatchesCache = Sets.newConcurrentHashSet();
    private final List<Object> unknowns = new ArrayList<>(5);

    boolean isEmpty() {
        return all.isEmpty() && unknowns.isEmpty();
    }

    @Override
    public String toString() {
        List<Method> mths = new ArrayList<>(all.keySet());
        Collections.sort(mths, (Method o1, Method o2) -> o1.name().compareTo(o2.name()));
        StringBuilder sb = new StringBuilder();
        for (Method m : mths) {
            ByMethod by = all.get(m);
            for (Map.Entry<String, List<Object>> e : by.pageForExacts.entrySet()) {
                sb.append('\n').append(m.name()).append('\t');
                sb.append(e.getKey()).append('\t');
                sb.append(by.decodeExacts.contains(e.getKey())).append('\t');
                for (Object o : e.getValue()) {
                    if (o instanceof Class<?>) {
                        sb.append(((Class<?>) o).getSimpleName()).append('\n');
                    } else {
                        String s = o.getClass().getSimpleName();
                        sb.append(s).append('\t');
                    }
                }
            }
            for (Map.Entry<Pattern, List<Object>> e : by.pagePatterns.entrySet()) {
                sb.append('\n').append(m.name()).append('\t');
                sb.append(e.getKey().pattern()).append('\t');
                sb.append(by.decodePatterns.contains(e.getKey())).append('\t');
                for (Object o : e.getValue()) {
                    if (o instanceof Class<?>) {
                        sb.append(((Class<?>) o).getSimpleName()).append('\n');
                    } else {
                        String s = o.getClass().getSimpleName();
                        sb.append(s).append('\t');
                    }
                }
            }
        }
        for (Object o : unknowns) {
            sb.append('\n').append('*').append('\t').append('*').append('\t').append('?').append('\t');
            sb.append(o instanceof Class<?> ? ((Class<?>) o).getSimpleName() : o.getClass().getSimpleName());
        }
        sb.append('\n');
        return AlignedText.formatTabbed(sb);
    }

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

    void addHelp(String helpPattern) {
        ByMethod by = all.get(GET);
        if (by == null) {
            by = new ByMethod();
            all.put(GET, by);
        }
        by.add(HelpPage.class, helpPattern);
    }

    public List<Object> listFor(HttpRequest req) {
        MethodPath mp = new MethodPath(req);
        List<Object> checkFirst = unknowns;
        if (nonMatchesCache.contains(mp)) {
            return checkFirst;
        }
        ByMethod bm = all.get(mp.method);
        if (bm == null) {
            return checkFirst;
        }
        List<List<Object>> matches = bm.matchingLists(trimLeadingAndTrailingSlashes(trimQuery(req.uri())));
        matches.add(0, checkFirst);
        return CollectionUtils.combinedList(matches);
    }

    void add(Page page) {
        Class<? extends Page> type = page.getClass();
        Methods methods = type.getAnnotation(Methods.class);
        Iterable<Method> mths = null;
        if (methods != null) {
            mths = CollectionUtils.toIterable(methods.value());
        } else {
            mths = page.findMethods();
        }
        boolean added = false;
        if (mths != null) {
            for (Method mth : mths) {
                ByMethod by = all.get(mth);
                if (by == null) {
                    by = new ByMethod();
                    all.put(mth, by);
                }
                added |= by.add(type, page);
            }
        }
        if (!added && !unknowns.contains(page)) {
            unknowns.add(page);
        }
    }

    void add(Class<? extends Page> type) {
        Methods methods = type.getAnnotation(Methods.class);
        boolean added = false;
        if (methods != null) {
            for (Method mth : methods.value()) {
                ByMethod by = all.get(mth);
                if (by == null) {
                    by = new ByMethod();
                    all.put(mth, by);
                }
                added |= by.add(type);
            }
        }
        if (!added && !unknowns.contains(type)) {
            unknowns.add(type);
        }
    }

    void addUnknown(Page pg) {
        unknowns.add(pg);
    }

    private final class ByMethod {

        private final Set<Pattern> patterns = new HashSet<>();
        private final Set<String> exacts = new HashSet<>();
        private final Map<String, List<Object>> pageForExacts = new HashMap<>();
        private final Map<Pattern, List<Object>> pagePatterns = new HashMap<>();
        private final Set<Pattern> decodePatterns = new HashSet<>();
        private final Set<String> decodeExacts = new HashSet<>();

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

        List<Object> matches(String trimmedUri) {
            return CollectionUtils.combinedList(matchingLists(trimmedUri));
        }

        private List<List<Object>> matchingLists(String trimmedUri) {
            List<List<Object>> res = new ArrayList<>(4);
            List<Object> a = pageForExacts.get(trimmedUri);
            String decodedUri = null;
            if (a != null) {
                res.add(a);
            } else if (Strings.contains('%', trimmedUri)) {
                try {
                    decodedUri = URLDecoder.decode(trimmedUri, "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    return Exceptions.chuck(ex);
                }
                a = pageForExacts.get(decodedUri);
                if (a != null) {
                    res.add(a);
                }
            }
            for (Pattern p : patterns) {
                String toTest = trimmedUri;
                if (decodePatterns.contains(p)) {
                    if (decodedUri == null) {
                        try {
                            decodedUri = URLDecoder.decode(trimmedUri, "UTF-8");
                        } catch (UnsupportedEncodingException ex) {
                            return Exceptions.chuck(ex);
                        }
                    }
                    toTest = decodedUri;
                }
                boolean isMatch = p.matcher(toTest).matches();
                if (isMatch) {
                    List<Object> l = pagePatterns.get(p);
                    if (l != null) {
                        res.add(l);
                    }
                }
            }
            return res;
        }

        Iterator<Object> allMatching(String trimmedUri) {
            List<List<Object>> res = matchingLists(trimmedUri);
            return CollectionUtils.combine(CollectionUtils.transform(res, l -> l.iterator()));
        }

        boolean match(String trimmedUri) {
            if (exacts.contains(trimmedUri)) {
                return true;
            }
            String decodedUri = null;
            if (Strings.contains('%', trimmedUri) || Strings.contains('+', trimmedUri)) {
                try {
                    decodedUri = URLDecoder.decode(trimmedUri, "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    return Exceptions.chuck(ex);
                }
                if (exacts.contains(decodedUri)) {
                    return true;
                }
            }
            for (Pattern p : patterns) {
                boolean isMatch = p.matcher(trimmedUri).matches();
                if (isMatch) {
                    return true;
                }
                if (decodedUri != null) {
                    isMatch = p.matcher(decodedUri).matches();
                    if (isMatch) {
                        return true;
                    }
                }
            }
            return false;
        }

        void add(Class<? extends Page> page, String regex) {
            String exact = pp.exactPathForRegex(regex);
            if (exact != null) {
                exacts.add(exact);
            } else {
                patterns.add(pp.getPattern(regex));
            }
        }

        boolean add(Class<? extends Page> page) {
            return add(page, (Page) null);
        }

        boolean add(Class<? extends Page> page, Page instance) {
            boolean pathFound = false;
            Path pth = page.getAnnotation(Path.class);
            if (pth != null) {
                pathFound = true;
                for (String pat : pth.value()) {
                    String pt = trimLeadingAndTrailingSlashes(pat);
                    if (exacts.contains(pt)) {
                        List<Object> l = pageForExacts.get(pt);
                        l.add(page);
                        if (pth.decode()) {
                            decodeExacts.add(pt);
                        }
                    }
                    if (pp.isExactGlob(pat)) {
                        exacts.add(pt);
                        if (pth.decode()) {
                            decodeExacts.add(pt);
                        }
                        List<Object> l = pageForExacts.get(pt);
                        if (l == null) {
                            l = new ArrayList<>(5);
                            pageForExacts.put(pt, l);
                        }
                        l.add(instance == null ? page : instance);
                    } else {
                        Pattern pattern = pp.getPattern(PathPatterns.patternFromGlob(pat));
                        patterns.add(pattern);
                        if (pth.decode()) {
                            decodePatterns.add(pattern);
                        }
                        List<Object> l = pagePatterns.get(pattern);
                        if (l == null) {
                            l = new ArrayList<>();
                            pagePatterns.put(pattern, l);
                        }
                        l.add(instance == null ? page : instance);
                    }
                }
            }
            PathRegex rx = page.getAnnotation(PathRegex.class);
            if (rx != null) {
                pathFound = true;
                for (String regex : rx.value()) {
                    String exact = pp.exactPathForRegex(regex);
                    if (exact != null) {
                        exacts.add(exact);
                        if (rx.decode()) {
                            decodeExacts.add(exact);
                        }
                        List<Object> l = pageForExacts.get(exact);
                        if (l == null) {
                            l = new ArrayList<>(5);
                            pageForExacts.put(exact, l);
                        }
                        l.add(instance == null ? page : instance);
                    } else {
                        Pattern pattern = pp.getPattern(regex);
                        addPattern(pattern, instance == null ? page : instance, rx.decode());
                    }
                }
            }
            if (!pathFound && instance != null) {
                Set<PathPatternInfo> pths = instance.findPathPatterns();
                for (PathPatternInfo ppi : pths) {
                    boolean decode = ppi.decode;
                    if (ppi.knownExact) {
                        for (String pat : ppi.patterns) {
                            addExact(pat, instance, decode);
                            pathFound = true;
                        }
                    } else {
                        for (String pat : ppi.patterns) {
                            String exact = pp.exactPathForRegex(pat);
                            if (exact != null) {
                                pathFound = true;
                                addExact(exact, instance, decode);
                            } else {
                                Pattern pattern = pp.getPattern(pat);
                                addPattern(pattern, instance, decode);
                                pathFound = true;
                            }
                        }
                    }
                }
            }
            return pathFound;
        }

        void addPattern(Pattern pattern, Object instance, boolean decode) {
            patterns.add(pattern);
            if (decode) {
                decodePatterns.add(pattern);
            }
            List<Object> l = pagePatterns.get(pattern);
            if (l == null) {
                l = new ArrayList<>();
                pagePatterns.put(pattern, l);
            }
            l.add(instance);
        }

        void addExact(String pat, Object instance, boolean decode) {
            exacts.add(pat);
            if (decode) {
                decodeExacts.add(pat);
            }
            List<Object> l = pageForExacts.get(pat);
            if (l == null) {
                l = new ArrayList<>(5);
                pageForExacts.put(pat, l);
            }
            l.add(instance);
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
