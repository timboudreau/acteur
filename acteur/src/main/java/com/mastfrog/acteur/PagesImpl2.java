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

import com.google.common.io.Closeables;
import com.mastfrog.acteur.base.ChainRunner;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.util.collections.Converter;
import com.mastfrog.util.thread.QuietAutoCloseable;
import io.netty.channel.Channel;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
class PagesImpl2 implements Pages {

    private final Application application;

    @Inject
    PagesImpl2(Application application) {
        this.application = application;

    }

    @Override
    public CountDownLatch onEvent(RequestID id, Event<?> event, Channel channel) {
        CountDownLatch latch = new CountDownLatch(1);
        Closables closables = new Closables(channel, application.control());
        ReentrantScope scope = application.getRequestScope();
        ExecutorService svc = application.getWorkerThreadPool();
        AtomicBoolean done = new AtomicBoolean();
        ChainRunner<Page> runner = new ChainRunner<Page>(application, svc, scope, null, new C(done));

        return latch;
    }

    private static class SurroundingLogic implements Callable<Object[]> {

        private final PageCallable run;
        private final Application application;
        private final Closeables closeables;

        public SurroundingLogic(PageCallable run, Application app, Closeables closeables) {
            this.run = run;
            this.application = app;
            this.closeables = closeables;
        }

        @Override
        public Object[] call() throws Exception {
            try (QuietAutoCloseable c = Page.set(run.page)) {
                try (QuietAutoCloseable c1 = application.getRequestScope().enter(run.page, closeables)) {
                    run.page.setApplication(application);
                    return run.call();
                }
            }
        }
    }

    private static final class C implements Converter<Callable<Object[]>, Page> {

        private final AtomicBoolean done;

        public C(AtomicBoolean done) {
            this.done = done;
        }

        @Override
        public Callable<Object[]> convert(Page r) {
            return new PageCallable(r, done);
        }

        @Override
        public Page unconvert(Callable<Object[]> t) {
            return ((PageCallable) t).page;
        }
    }

    private static final class PageCallable implements Callable<Object[]> {

        private final Page page;
        private final AtomicBoolean done;

        public PageCallable(Page page, AtomicBoolean done) {
            this.page = page;
            this.done = done;
        }

        @Override
        public Object[] call() throws Exception {

            if (done.get()) {
                return null;
            }
        }

    }

}
