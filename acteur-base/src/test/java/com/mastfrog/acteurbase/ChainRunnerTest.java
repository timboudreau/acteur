/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteurbase;

import com.mastfrog.acteurbase.impl.A2;
import com.mastfrog.acteurbase.impl.Response;
import com.mastfrog.acteurbase.impl.ResponseImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.guicy.scope.ReentrantScope;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ChainRunnerTest {

    ReentrantScope scope;
    Dependencies deps;
    ExecutorService svc;
    AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>> chain;
    AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>> rejectIt;
    AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>> dontRespond;
    AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>> plainChain;
    AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>> errorChain;
    Timer timer = new Timer();

    @Test(timeout = 10000)
    public void testChainRunner() throws Exception, Throwable {
        ChainRunner cr = new ChainRunner(svc, scope);
        TestCallback chainWithDeferResults = new TestCallback();
        TestCallback dontRespondChainResults = new TestCallback();
        TestCallback plainChainResults = new TestCallback();
        TestCallback errorChainResults = new TestCallback();
        TestCallback rejectChainResults = new TestCallback();
        try (AutoCloseable cl = scope.enter()) {
            cr.run(chain, chainWithDeferResults);
            cr.run(dontRespond, dontRespondChainResults);
            cr.run(plainChain, plainChainResults);
            cr.run(errorChain, errorChainResults);
            cr.run(rejectIt, rejectChainResults);
        }
        chainWithDeferResults.assertGotResponse().assertActeurClass(AddedA.class).throwIfError().assertNotRejected();
        dontRespondChainResults.assertNoResponse().throwIfError();
        plainChainResults.throwIfError().assertGotResponse().assertActeurClass(AddedA.class).assertNotRejected();
        errorChainResults.assertException(SpecialError.class);
        rejectChainResults.throwIfError().assertRejected();
    }

    @Test(timeout = 10000)
    public void testMultiChainRunner() throws Exception, Throwable {
        List<AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>>> l = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            AbstractChain<AbstractActeur<Response, ResponseImpl,AbstractActeur.State<Response, ResponseImpl>>> ch = new AbstractChain<AbstractActeur<Response, ResponseImpl,AbstractActeur.State<Response, ResponseImpl>>>(deps, AbstractActeur.class)
                    .add(FirstA.class).add(Rejecter.class).add(SecondA.class).add(FinalA.class);
            l.add(ch);
        }
        l.add(plainChain);
        ChainsRunner cr = new ChainsRunner(svc, scope, new ChainRunner(svc, scope));
        TestCallback callback = new TestCallback();
        cr.run(l, callback);
        callback.throwIfError().assertNotRejected().assertGotResponse();

        l.remove(l.size() - 1);
        callback = new TestCallback();
        cr.run(l, callback);
        callback.throwIfError().assertNoResponse();
    }

    @Before
    public void before() throws IOException {
        svc = Executors.newCachedThreadPool();
//        svc = new java.util.concurrent.ForkJoinPool(12);
        scope = new ReentrantScope();
        deps = new Dependencies(new M());
        ShutdownHookRegistry reg = deps.getInstance(ShutdownHookRegistry.class);
        reg.add(svc);
        reg.add(timer);
        chain = new AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>>(deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondA.class).add(FinalA.class);

        rejectIt = new AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>>(deps, AbstractActeur.class)
                .add(FirstA.class).add(Rejecter.class).add(SecondA.class).add(FinalA.class);

        dontRespond = new AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>>(deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondWithoutTimeoutA.class);

        plainChain = new AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>>(deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondWithoutTimeoutA.class).add(FinalA.class);

        errorChain = new AbstractChain<AbstractActeur<Response, ResponseImpl, AbstractActeur.State<Response, ResponseImpl>>>(deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondWithoutTimeoutA.class).add(ErrorA.class);
    }

    @After
    public void after() {
        deps.shutdown();
    }

    class M extends AbstractModule {

        @Override
        protected void configure() {
            scope.bindTypes(binder(), Deferral.class, Chain.class, String.class, Integer.class, Byte.class, StringBuilder.class, Short.class, Float.class, Double.class);
            bind(ExecutorService.class).toInstance(svc);
            bind(ReentrantScope.class).toInstance(scope);
            bind(Timer.class).toInstance(timer);
        }
    }

    static class Rejecter extends A2 {

        Rejecter() {
            super.reject();
        }
    }

    static class FirstA extends A2 {

        FirstA() {
            System.out.println("firstA " + Thread.currentThread());
            setState(new State<Response, ResponseImpl>("hello"));
        }
    }

    static class SecondA extends A2 {

        @Inject
        SecondA(String msg, Timer timer, Deferral defer) {
            System.out.println("secondA " + msg + " " + Thread.currentThread());
            response().add(Headers.CONTENT_ENCODING, "foo " + msg);
            setState(new AbstractActeur.State<Response, ResponseImpl>(23));
            System.out.println("defer");
            final Resumer resume = defer.defer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    System.out.println("timer task run");
                    resume.resume();
                }
            }, 1000);
        }
    }

    static class SecondWithoutTimeoutA extends A2 {

        @Inject
        SecondWithoutTimeoutA(String msg) {
            System.out.println("secondA " + msg + " " + Thread.currentThread());
            response().add(Headers.CONTENT_ENCODING, "foo " + msg);
            setState(new AbstractActeur.State<Response, ResponseImpl>(23));
        }
    }

    static class FinalA extends A2 {

        @Inject
        @SuppressWarnings("unchecked")
        FinalA(String msg, Integer val, Chain chain) {
            System.out.println("finalA " + val + " " + Thread.currentThread());
            response().add(Headers.ACCEPT, "3 ");
            setState(new AbstractActeur.State<Response, ResponseImpl>(0.5F));
            chain.add(AddedA.class);
            chain.add(ErrorA.class); // will not be called because AddedA sets teh response code
        }
    }

    static class AddedA extends A2 {

        @Inject
        AddedA(Float f) {
            System.out.println("AddedA " + f + " " + Thread.currentThread());
            response().setStatus(HttpResponseStatus.CREATED);
            setState(new AbstractActeur.State<Response, ResponseImpl>(false));
        }
    }

    static class ErrorA extends A2 {

        ErrorA() throws SpecialError {
            throw new SpecialError("Ha ha.");
        }
    }

    static class SpecialError extends Exception {

        SpecialError(String s) {
            super(s);
        }
    }
}
