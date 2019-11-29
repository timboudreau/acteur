/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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

import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_BASE_PATH;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import io.netty.handler.codec.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
abstract class PathFilters {

    abstract boolean isEarlyPageMatch(HttpRequest req);

    abstract PagePathAndMethodFilter normalPages();

    abstract void addEarlyPage(Page page);

    abstract void addEarlyPage(Class<? extends Page> page);

    abstract void addNormalPage(Page page);

    abstract void addNormalPage(Class<? extends Page> page);

    abstract PagePathAndMethodFilter earlyPages();

    private static class InitialPathFilters extends PathFilters {

        private final List<Object> earlyPages = new ArrayList<>(25);
        private final List<Object> normalPages = new ArrayList<>(25);

        @Override
        boolean isEarlyPageMatch(HttpRequest req) {
            throw new IllegalStateException("Not yet initialized");
        }

        @Override
        PagePathAndMethodFilter normalPages() {
            throw new IllegalStateException("Not yet initialized");
        }

        @Override
        void addEarlyPage(Page page) {
            earlyPages.add(page);
        }

        @Override
        void addEarlyPage(Class<? extends Page> page) {
            earlyPages.add(page);
        }

        @Override
        void addNormalPage(Page page) {
            normalPages.add(page);
        }

        @Override
        void addNormalPage(Class<? extends Page> page) {
            normalPages.add(page);
        }

        @Override
        PagePathAndMethodFilter earlyPages() {
            throw new IllegalStateException("Not yet initialized");
        }

        private PathFilters toInitializedInstance(String basePath) {
            PagePathAndMethodFilter normal = new PagePathAndMethodFilter(basePath);
            addToFilter(normalPages, normal);
            PagePathAndMethodFilter early = null;
            if (!earlyPages.isEmpty()) {
                early = new PagePathAndMethodFilter(basePath);
                addToFilter(earlyPages, early);
            }
            return new InitializedPathFilters(basePath, normal, early);
        }

        @SuppressWarnings("unchecked")
        private void addToFilter(List<Object> objects, PagePathAndMethodFilter to) {
            for (Object o : objects) {
                if (o instanceof Page) {
                    to.add((Page) o);
                } else if (o instanceof Class<?>) {
                    to.add((Class<? extends Page>) o);
                } else {
                    throw new AssertionError("Neither a page nor a Class<? extends Page>: " + o);
                }
            }
        }
    }

    static PathFilters create(Supplier<Dependencies> deps) {
        InitialPathFilters initial = new InitialPathFilters();
        return new DelegatingPathFilters(initial, deps);
    }

    private static class DelegatingPathFilters extends PathFilters {

        private final AtomicReference<PathFilters> filters = new AtomicReference<>();
        private final Supplier<Dependencies> supp;

        DelegatingPathFilters(PathFilters orig, Supplier<Dependencies> supp) {
            filters.set(orig);
            this.supp = supp;
        }

        PathFilters delegate() {
            PathFilters result = filters.get();
            if (result instanceof InitialPathFilters) {
                InitialPathFilters ipf = (InitialPathFilters) result;
                Dependencies deps = supp.get();
                if (deps != null) {
                    Settings settings = deps.getInstance(Settings.class);
                    if (settings == null) {
                        // possible but unlikely
                        result = ipf.toInitializedInstance(null);
                    } else {
                        String bp = settings.getString(SETTINGS_KEY_BASE_PATH);
                        if (PagePathAndMethodFilter.DEBUG) {
                            System.out.println("Initialize path filters with "
                                    + "base path: '" + bp + "'.\nNormal pages: \n"
                                    + ipf.normalPages + "\nEarly pages: \n"
                                    + ipf.earlyPages);
                        }
                        result = ipf.toInitializedInstance(bp);
                    }
                }
            }
            return result;
        }

        @Override
        boolean isEarlyPageMatch(HttpRequest req) {
            return delegate().isEarlyPageMatch(req);
        }

        @Override
        PagePathAndMethodFilter normalPages() {
            return delegate().normalPages();
        }

        @Override
        void addEarlyPage(Page page) {
            delegate().addEarlyPage(page);
        }

        @Override
        void addEarlyPage(Class<? extends Page> page) {
            delegate().addEarlyPage(page);
        }

        @Override
        void addNormalPage(Page page) {
            delegate().addNormalPage(page);
        }

        @Override
        void addNormalPage(Class<? extends Page> page) {
            delegate().addNormalPage(page);
        }

        @Override
        PagePathAndMethodFilter earlyPages() {
            return delegate().earlyPages();
        }

    }

    private static final class InitializedPathFilters extends PathFilters {

        private final String basePath;

        private final PagePathAndMethodFilter filter;
        private PagePathAndMethodFilter earlyPageMatcher;

        @Inject
        InitializedPathFilters(String basePath, PagePathAndMethodFilter filter, PagePathAndMethodFilter earlyPageMatcher) {
            this.basePath = basePath;
            this.filter = filter;
            this.earlyPageMatcher = earlyPageMatcher;
        }

        boolean isEarlyPageMatch(HttpRequest req) {
            boolean result = earlyPageMatcher != null && earlyPageMatcher.match(req);
            return result;
        }

        PagePathAndMethodFilter normalPages() {
            return filter;
        }

        void addEarlyPage(Page page) {
            earlyPages().add(page);
        }

        void addEarlyPage(Class<? extends Page> page) {
            earlyPages().add(page);
        }

        @Override
        PagePathAndMethodFilter earlyPages() {
            if (earlyPageMatcher == null) {
                earlyPageMatcher = new PagePathAndMethodFilter(basePath);
            }
            return earlyPageMatcher;
        }

        @Override
        void addNormalPage(Page page) {
            normalPages().add(page);
        }

        @Override
        void addNormalPage(Class<? extends Page> page) {
            normalPages().add(page);
        }
    }
}
