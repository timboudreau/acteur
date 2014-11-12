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
package com.mastfrog.acteur.base;

import com.mastfrog.util.collections.Converter;
import java.util.concurrent.Callable;

/**
 *
 * @author Tim Boudreau
 */
public class ActeurCallableConverter implements Converter<Callable<Object[]>, ActeurBase> {
    
    private final Converter<Callable<Object[]>, Callable<Object[]>> wrap;

    public ActeurCallableConverter(Converter<Callable<Object[]>, Callable<Object[]>> wrap) {
        this.wrap = wrap;
    }
    
    public ActeurCallableConverter() {
        wrap = null;
    }

    @Override
    public Callable<Object[]> convert(ActeurBase r) {
        Callable<Object[]>  callable = new ActeurCallable(r);
        if (wrap != null) {
            callable = wrap.convert(callable);
        }
        return callable;
    }

    @Override
    public ActeurBase unconvert(Callable<Object[]> t) {
        return ((ActeurCallable) t).acteur;
    }

    static final class ActeurCallable implements Callable<Object[]> {

        private final ActeurBase acteur;

        public ActeurCallable(ActeurBase acteur) {
            this.acteur = acteur;
        }

        @Override
        public Object[] call() throws Exception {
            StateBase state = acteur.getState();
            if (state == null) {
                NullPointerException npe = new NullPointerException(acteur + " returns null from getState(), which is not permitted");
                throw npe;
            }
            boolean done = state.isRejected();
            return done ? null : state.getContext();
        }
    }
}
