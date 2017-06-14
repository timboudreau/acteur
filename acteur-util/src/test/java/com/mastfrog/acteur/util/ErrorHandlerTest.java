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
package com.mastfrog.acteur.util;

import com.google.inject.AbstractModule;
import com.mastfrog.acteur.util.ErrorHandler.Ordinal;
import com.mastfrog.acteur.util.ErrorHandlerTest.MM;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(MM.class)
public class ErrorHandlerTest {
    
    @Test
    public void testOrderingAndInterception(ErrorHandlers handlers, InterceptOne one, InterceptTwo two, InterceptThree three, InterceptFour four) {
        handlers.onError(new IOException());
        assertTrue(three.intercepted);
        assertFalse(one.intercepted);
        assertFalse(two.intercepted);
        assertFalse(four.intercepted);
        assertEquals(2, three.interceptAt);
        index = 0;
        three.intercepted = false;
        handlers.onError(new IllegalArgumentException(new OutOfMemoryError("X")));
        assertTrue(four.intercepted);
        assertFalse(one.intercepted);
        assertFalse(two.intercepted);
        assertFalse(three.intercepted);
        assertEquals(3, four.interceptAt);
    }
    
    static class MM extends AbstractModule {

        @Override
        protected void configure() {
            bind(InterceptFour.class).asEagerSingleton();
            bind(InterceptThree.class).asEagerSingleton();
            bind(InterceptOne.class).asEagerSingleton();
            bind(InterceptTwo.class).asEagerSingleton();
        }
        
    }
    
    static int index = 0;
    @Singleton
    @Ordinal(1)
    static class InterceptOne extends ErrorHandler {
        int interceptAt;
        boolean intercepted;
        @Inject
        public InterceptOne(Registry handlers) {
            super(handlers);
        }

        @Override
        protected void handle(Throwable err, Consumer<Throwable> next) {
            interceptAt = index++;
            if (err instanceof IllegalStateException) {
                intercepted = true;
                System.out.println("Intercept " + (index-1) + err.getClass().getName());
            } else {
                next.accept(err);
            }
        }
    }
    
    @Singleton
    @Ordinal(2)
    static class InterceptTwo extends ErrorHandler {
        int interceptAt;
        boolean intercepted;
        @Inject
        public InterceptTwo(ErrorHandler.Registry handlers) {
            super(handlers);
        }

        @Override
        protected void handle(Throwable err, Consumer<Throwable> next) {
            interceptAt = index++;
            if (err instanceof NumberFormatException) {
                intercepted = true;
                System.out.println("Intercept " + (index-1) + err.getClass().getName());
            } else {
                next.accept(err);
            }
        }
    }
    @Singleton
    @Ordinal(3)
    static class InterceptThree extends ErrorHandler {
        int interceptAt;
        boolean intercepted;
        @Inject
        public InterceptThree(ErrorHandler.Registry handlers) {
            super(handlers);
        }

        @Override
        protected void handle(Throwable err, Consumer<Throwable> next) {
            interceptAt = index++;
            if (err instanceof IOException) {
                intercepted = true;
                System.out.println("Intercept " + (index-1) + err.getClass().getName());
            } else {
                next.accept(err);
            }
        }
    }
    
    @Singleton
    @Ordinal(10)
    static class InterceptFour extends ErrorHandler.Typed<OutOfMemoryError> {
        int interceptAt;
        boolean intercepted;
        @Inject
        public InterceptFour(ErrorHandler.Registry handlers) {
            super(handlers, OutOfMemoryError.class, true);
        }

        @Override
        protected boolean doHandle(OutOfMemoryError err) {
            assertNotNull(err);
            interceptAt = index++;
            intercepted = true;
            System.out.println("Intercept wrapped oome at " + (index - 1) + err.getClass().getName());
            return true;
        }
    }
}
