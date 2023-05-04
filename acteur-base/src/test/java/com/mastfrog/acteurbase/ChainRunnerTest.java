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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.acteurbase.impl.A2;
import com.mastfrog.acteurbase.impl.Response;
import com.mastfrog.acteurbase.impl.ResponseImpl;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
    ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> chain;
    ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> rejectIt;
    ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> dontRespond;
    ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> plainChain;
    ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> errorChain;
    ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> remnantChain;
    Timer timer = new Timer();

    @Test(timeout = 10000)
    public void testChainRunner() throws Exception, Throwable {
        AtomicBoolean cancelled = new AtomicBoolean();
        ChainRunner cr = new ChainRunner(svc, scope);
        TestCallback chainWithDeferResults = new TestCallback();
        TestCallback dontRespondChainResults = new TestCallback();
        TestCallback plainChainResults = new TestCallback();
        TestCallback errorChainResults = new TestCallback();
        TestCallback rejectChainResults = new TestCallback();
        TestCallback remnantChainResults = new TestCallback();
        try (AutoCloseable cl = scope.enter()) {
            cr.submit(chain, chainWithDeferResults, cancelled);
            cr.submit(dontRespond, dontRespondChainResults, cancelled);
            cr.submit(plainChain, plainChainResults, cancelled);
            cr.submit(errorChain, errorChainResults, cancelled);
            cr.submit(rejectIt, rejectChainResults, cancelled);
            cr.submit(remnantChain, remnantChainResults, cancelled);
            cr.submit(remnantChain, remnantChainResults, cancelled); // Do this twice to ensure acteur counter reset
        }
        chainWithDeferResults.assertGotResponse().assertActeurClass(AddedA.class).throwIfError().assertNotRejected();
        dontRespondChainResults.assertNoResponse().throwIfError();
        plainChainResults.throwIfError().assertGotResponse().assertActeurClass(AddedA.class).assertNotRejected();
        errorChainResults.assertException(SpecialError.class);
        rejectChainResults.throwIfError().assertRejected();
        remnantChainResults.throwIfError().assertRejected();
    }

    @Test(timeout = 20000)
    public void testMultiChainRunner() throws Exception, Throwable {
        AtomicBoolean cancelled = new AtomicBoolean();
        List<ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?>> l = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> ch
                    = new NamedChain("Chain " + i, deps, AbstractActeur.class)
                            .add(FirstA.class).add(Rejecter.class).add(SecondWithoutTimeoutA.class).add(EndA.class);
            l.add(ch);
        }
        l.add(plainChain);
        ChainsRunner cr = new ChainsRunner(svc, scope, new ChainRunner(svc, scope));
        TestCallback callback = new TestCallback();
        cr.submit(l, callback, cancelled);
        callback.throwIfError().assertNotRejected().assertGotResponse();

        l.remove(l.size() - 1);
        callback = new TestCallback();
        cr.submit(l, callback, cancelled);
        callback.throwIfError().assertNoResponse();
    }

    static class NamedChain extends ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, NamedChain> {

        private final String name;

        public NamedChain(String name, Dependencies deps, Class<? super AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>> type) {
            super(deps, type);
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }

    @Before
    public void before() throws IOException {
//        svc = Executors.newCachedThreadPool();
        svc = new java.util.concurrent.ForkJoinPool(12);
        scope = new ReentrantScope();
        deps = new Dependencies(new M());
        ShutdownHookRegistry reg = deps.getInstance(ShutdownHookRegistry.class);
        reg.add(svc);
        reg.add(timer);
        chain = new NamedChain("Simple", deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondA.class).add(FinalA.class);

        rejectIt = new NamedChain("RejectIt", deps, AbstractActeur.class)
                .add(FirstA.class).add(Rejecter.class).add(SecondA.class).add(FinalA.class);

        dontRespond = new NamedChain("DontRespond", deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondWithoutTimeoutA.class);

        plainChain = new NamedChain("Plain", deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondWithoutTimeoutA.class).add(FinalA.class);

        errorChain = new NamedChain("ErrorChain", deps, AbstractActeur.class)
                .add(FirstA.class).add(SecondWithoutTimeoutA.class).add(ErrorA.class);

        remnantChain = new NamedChain("Remnant", deps, AbstractActeur.class)
                .add(FirstA.class).add(XA1.class).add(XA2.class);
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

        @Inject
        @SuppressWarnings("unchecked")
        FirstA(Chain chain) {
            assertNotNull(chain);
            assertTrue(chain instanceof NamedChain);
            NamedChain nc = (NamedChain) chain;
            if ("Remnant".equals(chain.toString())) {
                Supplier<Chain> supp = chain.remnantSupplier();
                Chain nue = supp.get();
                Set<Class<?>> s = new HashSet<>();
                for (Object o : nue) {
                    s.add(o.getClass());
                }
                assertTrue(s.contains(XA1.class));
                assertTrue(s.contains(XA2.class));
                assertEquals(2, s.size());
            }
            setState(new AS("hello"));
        }
    }

    static class SecondA extends A2 {

        @Inject
        @SuppressWarnings("deprecation")
        SecondA(String msg, Timer timer, Deferral defer) {
            response().add(Headers.CONTENT_ENCODING, "foo " + msg);
            setState(new AS(23));
            final Resumer resume = defer.defer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    resume.resume();
                }
            }, 1000);
        }
    }

    static class SecondWithoutTimeoutA extends A2 {

        @Inject
        SecondWithoutTimeoutA(String msg) {
            response().add(Headers.CONTENT_ENCODING, "foo " + msg);
            setState(new AS(23));
        }
    }

    static class FinalA extends A2 {

        @Inject
        @SuppressWarnings("unchecked")
        FinalA(String msg, Integer val, Chain chain) {
            response().add(Headers.ACCEPT, "3 ");
            setState(new AS(0.5F));
            chain.add(AddedA.class);
            chain.add(ErrorA.class); // will not be called because AddedA sets teh response code
        }
    }

    static class AddedA extends A2 {

        @Inject
        AddedA(Float f) {
            response().setStatus(HttpResponseStatus.CREATED);
            setState(new AS(false));
        }
    }

    static class EndA extends A2 {

        EndA() {
            response().setMessage("foo");
            response().setStatus(HttpResponseStatus.OK);
            setState(new AS(false));
        }
    }

    static class XA1 extends A2 {

        XA1() {
            setState(new AS(false));
        }
    }

    static class XA2 extends A2 {

        XA2() {
            setState(new AS(true));
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
