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

import com.mastfrog.acteurbase.ActeurState;
import com.mastfrog.acteurbase.impl.Response;
import com.mastfrog.acteurbase.impl.ResponseImpl;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;

/**
 *
 * @author Tim Boudreau
 */
class TestCallback implements ChainCallback<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ActeurState<Response, ResponseImpl>, ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?>, Response, ResponseImpl> {

    private final CountDownLatch latch;
    private Throwable ex;
    private Boolean done;
    private ActeurState<Response, ResponseImpl> state;
    List<ResponseImpl> responses;
    private Boolean rejected;

    TestCallback() {
        this.latch = new CountDownLatch(1);
    }

    TestCallback await() throws InterruptedException {
        latch.await(5, TimeUnit.SECONDS);
        return this;
    }

    @Override
    public synchronized void onDone(ActeurState<Response, ResponseImpl> state, List<ResponseImpl> responses) {
        System.out.println("OnDone " + state);
        System.out.println("RESPONSES: " + responses);
        rejected = state.isRejected();
        this.responses = responses;
        this.state = state;
        done = true;
        latch.countDown();
    }

    @Override
    public synchronized void onRejected(ActeurState<Response, ResponseImpl> state) {
        System.out.println("on rejected " + state);
        rejected = state.isRejected();
        this.state = state;
        done = false;
        latch.countDown();
    }

    @Override
    public synchronized void onNoResponse() {
        System.out.println("onNoResponse");
        done = false;
        latch.countDown();
    }

    @Override
    public synchronized void onFailure(Throwable ex) {
        this.ex = ex;
        latch.countDown();
    }

    TestCallback assertActeurClass(Class<? extends AbstractActeur> type) throws Throwable {
        await();
        ActeurState<Response, ResponseImpl> state;
        synchronized (this) {
            state = this.state;
        }
        Assert.assertNotNull(state);
        Assert.assertNotNull(state.acteur);
        Assert.assertTrue("Found " + state.acteur, type.isInstance(state.acteur));
        return this;
    }

    TestCallback assertNoResponse() throws Throwable {
        await();
        synchronized (this) {
            Assert.assertEquals(Boolean.FALSE, done);
        }
        return this;
    }

    TestCallback assertGotResponse() throws Throwable {
        await();
        synchronized (this) {
            Assert.assertEquals(Boolean.TRUE, done);
        }
        return this;
    }

    TestCallback assertRejected() throws Throwable {
        await();
        synchronized (this) {
            Assert.assertEquals(Boolean.TRUE, rejected);
        }
        return this;
    }

    TestCallback assertNotRejected() throws Throwable {
        await();
        synchronized (this) {
            Assert.assertEquals(Boolean.FALSE, rejected);
        }
        return this;
    }

    TestCallback assertException(Class<? extends Throwable> type) throws Throwable {
        await();
        synchronized (this) {
            Assert.assertNotNull(ex);
            if (!type.isInstance(ex)) {
                throw ex;
            }
            Assert.assertTrue("Expected " + type.getName() + " but was " + ex.getClass().getName(), type.isInstance(ex));
        }
        return this;
    }

    TestCallback throwIfError() throws Throwable {
        await();
        synchronized (this) {
            if (ex != null) {
                throw ex;
            }
        }
        return this;
    }

    @Override
    public void onBeforeRunOne(ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> chain) {
        System.out.println("On before run one " + chain);
    }

    @Override
    public void onAfterRunOne(ArrayChain<AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>>, ?> chain, AbstractActeur<Response, ResponseImpl, ActeurState<Response, ResponseImpl>> acteur) {
        System.out.println("On after run one " + acteur);
    }
}
