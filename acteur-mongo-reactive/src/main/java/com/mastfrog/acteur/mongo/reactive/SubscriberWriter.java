/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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
package com.mastfrog.acteur.mongo.reactive;

import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import static java.lang.Integer.max;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 *
 * @author timb
 */
final class SubscriberWriter<T> implements ChannelFutureListener, Subscriber<T> {

    @SuppressWarnings("unchecked")
    private static final AtomicReferenceFieldUpdater<SubscriberWriter, SState> UPDATER = AtomicReferenceFieldUpdater.newUpdater(SubscriberWriter.class, SState.class, "state");
    private volatile SState state = new SState();

    private final Publisher<T> publisher;
    private final int batchSize;
    private final ApplicationControl ctrl;
    private final AtomicLinkedQueue<T> messages = new AtomicLinkedQueue<>();
    private final ConstantBuffers constant;
    private final boolean closeConnection;
    private final Codec codec;

    SubscriberWriter(Publisher<T> publisher, CursorControl cursorControl,
            ApplicationControl ctrl,
            HttpEvent evt, ConstantBuffers constant, Codec codec) {
        this.publisher = publisher;
        this.ctrl = ctrl;
        this.constant = constant;
        this.closeConnection = !evt.requestsConnectionStayOpen();
        this.codec = codec;
        this.batchSize = max(1, cursorControl.batchSize());
    }

    @Override
    public String toString() {
        return "SubscriberWriter(" + batchSize + "-"
                + state.toString().replace(' ', '-')
                + "-" + messages.size() + "-" + closeConnection
                + "-" + publisher.getClass().getSimpleName() + "-" + state + ")";
    }

    /*
    Okay, there is no way for this to be pretty.
    
    We have two asynchronous things going here - flushing HTTP content
    and receiving content to push from the cursor.  So:
    
    Once the headers are flushed, if no error and not cancelled,
    we subscribe to the Publisher, and write the initial JSON array '['
    (and possibly some messages if some have arrived).
    
    After that, we hold the ChannelFuture from the flush of the last content
    in the SState object.  Each call to next() will increment the counter in
    the sstate; when it, modulo batchSize is zero, we *then* attach a this
    object as a listener to the flushing of the previous batch of data.
    
    operationComplete will be called to flush the added messages, and we
    update the state with the new future from that flush.  And so on,
    until the cursor is exhausted (or the client disconnected or something
    like that).
    
     */
    private SState state() {
        return UPDATER.get(this);
    }

    private SState getAndUpdateState(UnaryOperator<SState> updater) {
        Obj<SState> newState = Obj.create();
        SState result = UPDATER.getAndUpdate(this, old -> {
            SState nue = updater.apply(old);
            newState.set(nue);
            return nue;
        });

        if (newState.get().isDiscontinued()) {
            messages.clear();
        }
        if (result.itemsWritten() && !newState.get().itemsWritten()) {
            throw new Error("Bad state change from \n" + result
                    + " to " + newState + " by " + updater);
        }

        return result;
    }

    @Override
    public synchronized void operationComplete(ChannelFuture f) throws Exception {
        f.removeListener(cancellationListener);
        if (f.cause() != null) {
            getAndUpdateState(old -> old.withErrored());
            ctrl.logFailure(f);
        } else if (f.isCancelled()) {
            getAndUpdateState(old -> old.withAborted());
        } else {
            SState st = getAndUpdateState(old -> {
                if (!old.isDiscontinued()) {
                    if (!f.channel().isOpen()) {
                        return old.withAborted();
                    }
                    return old.withFuture(f);
                }
                return old;
            });
            if (st.isDiscontinued()) {
                if (st.isErrored()) {
                    f.channel().close();
                } else if (closeConnection) {
                    f.addListener(ChannelFutureListener.CLOSE);
                }
                return;
            }
            boolean first = !st.future().isPresent();
            if (first) {
                publisher.subscribe(this);
            }
            onOperationComplete(f, first);
        }
    }

    private void onOperationComplete(ChannelFuture f, boolean first) throws Exception {
        List<T> results = messages.drain();
        int maxComponents = (results.size() * 2) + 5;
        CompositeByteBuf buf = f.channel().alloc().compositeBuffer(maxComponents);
        if (first) {
            addBuf(buf, constant.open());
        }
        if (!results.isEmpty() || state.cursorDone()) {
            SState oldState = getAndUpdateState(old -> old.withItemsWritten());
            if (oldState.itemsWritten() && !results.isEmpty()) {
                addBuf(buf, constant.comma());
            }
            for (Iterator<?> it = results.iterator(); it.hasNext();) {
                Object next = it.next();
                if (next instanceof ByteBuf) {
                    addBuf(buf, (ByteBuf) next);
                } else {
                    addBuf(buf, f.channel().alloc().buffer().writeBytes(
                            codec.writeValueAsBytes(next)));
                }
                if (it.hasNext()) {
                    addBuf(buf, constant.comma());
                }
            }
        } else if (!first) {
            // We got a flush callback, but there is nothing to write
            return;
        }
        if (state().cursorDone() && messages.isEmpty()) {
            addBuf(buf, constant.close());
            ChannelFuture nue = f.channel().writeAndFlush(new DefaultLastHttpContent(buf));
            getAndUpdateState(old -> old.withDone());
            if (closeConnection) {
                nue.addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ChannelFuture newFut = f.channel().writeAndFlush(new DefaultHttpContent(buf));
            newFut.addListener(cancellationListener);
            getAndUpdateState(old -> old.withFuture(newFut));
        }
    }

    private final L cancellationListener = new L();

    class L implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture fut) throws Exception {
            if (fut.isCancelled()) {
                getAndUpdateState(old -> old.withAborted());
            } else if (fut.cause() != null) {
                getAndUpdateState(old -> old.withErrored());
                ctrl.logFailure(fut);
            }
        }

    }

    @Override
    public void onSubscribe(Subscription s) {
        getAndUpdateState(old
                -> old.withSubscription(s, batchSize));
    }

    @Override
    public void onNext(T t) {
        messages.push(t);
        getAndUpdateState(old -> {
            return old.incrementInvocations(batchSize, this::flushBatch);
        });
    }

    private void flushBatch() {
        state().withFuture(f -> {
            f.addListener(this);
        });
    }

    @Override
    public void onError(Throwable thrwbl) {
        getAndUpdateState(SState::withErrored);
    }

    @Override
    public void onComplete() {
        state().future().ifPresent(fut -> {
            try {
                this.operationComplete(fut);
            } catch (Exception ex) {
                ctrl.internalOnError(ex);
            }
        });
        getAndUpdateState(old -> old.withCursorDone());
        flushBatch();
    }

    private void addBuf(CompositeByteBuf buf, ByteBuf toAdd) {
        int writerIndex = buf.writerIndex();
        buf.addComponent(toAdd.retain());
        buf.writerIndex(writerIndex + toAdd.readableBytes());
    }

    /**
     * Holds a few things that need to be frequently updated together - several
     * bits of state to say what we have or haven't done, and whether we can do
     * anything more, a count of how many calls to next() there have been so we
     * can trigger a flush when we hit the batch size threshold, and the last
     * channel future we've received, which we attach listeners to to trigger
     * another flush, so we can't wind up with two threads concurrently writing
     * messages to the same socket.
     *
     */
    private static class SState {

        private final Subscription subscription;
        private final ChannelFuture lastFuture;
        private final int invocations;
        private final byte states;

        SState() {
            this(0, null, 0, null);
        }

        SState(int states, Subscription subscription, int invocations, ChannelFuture lastFuture) {
            this.subscription = subscription;
            this.invocations = invocations;
            this.lastFuture = lastFuture;
            this.states = (byte) states;
        }

        boolean withFuture(ThrowingConsumer<ChannelFuture> con) {
            boolean result = lastFuture != null;
            if (result) {
                con.toNonThrowing().accept(lastFuture);
            }
            return result;
        }

        Optional<ChannelFuture> future() {
            return Optional.ofNullable(lastFuture);
        }

        SState withSubscription(Subscription sub, long batchSize) {
            if (!isDiscontinued()) {
                sub.request(batchSize);
            } else {
                sub.cancel();
                sub = null;
            }
            return new SState(states, sub, invocations, lastFuture);
        }

        SState withFuture(ChannelFuture lastFuture) {
            return new SState(states, subscription, invocations, lastFuture);
        }

        boolean isErrored() {
            return (states & 1) != 0;
        }

        SState withErrored() {
            if (subscription != null) {
                subscription.cancel();
            }
            if (lastFuture != null && lastFuture.channel().isOpen()) {
                lastFuture.channel().close();
            }
            return new SState(states | 1, null, invocations, null);
        }

        boolean isDiscontinued() {
            return (states & 0b111) != 0;
        }

        boolean isAborted() {
            return (states & (1 << 1)) != 0;
        }

        SState withAborted() {
            if (subscription != null) {
                subscription.cancel();
            }
            return new SState(states | (1 << 1), null, invocations, null);
        }

        boolean isDone() {
            return (states & (1 << 2)) != 0;
        }

        SState withDone() {
            if (subscription != null) {
                subscription.cancel();
            }
            return new SState(states | (1 << 2), null, invocations, null);
        }

        boolean itemsWritten() {
            return (states & (1 << 3)) != 0;
        }

        SState withItemsWritten() {
            if (itemsWritten()) {
                return this;
            }
            return new SState(states | (1 << 3), subscription, invocations, lastFuture);
        }

        boolean unsubscribed() {
            return (states & (1 << 4)) != 0;
        }

        SState withUnsubscribed() {
            if (unsubscribed()) {
                return this;
            }
            if (subscription != null) {
                subscription.cancel();
            }
            return new SState(states | (1 << 4), null, invocations, lastFuture);
        }

        boolean subscribed() {
            return (states & (1 << 5)) != 0;
        }

        SState withCursorDone() {
            return new SState(states | (1 << 6), null, invocations, lastFuture);
        }

        boolean cursorDone() {
            return (states & (1 << 6)) != 0;
        }

        @Override
        public String toString() {
            List<String> parts = new ArrayList<>();
            if (subscribed()) {
                parts.add("subscribed");
            }
            if (unsubscribed()) {
                parts.add("unsubscribed");
            }
            if (itemsWritten()) {
                parts.add("itemsWritten");
            }
            if (isAborted()) {
                parts.add("aborted");
            }
            if (isErrored()) {
                parts.add("errored");
            }
            if (isDone()) {
                parts.add("done");
            }
            if (lastFuture != null) {
                parts.add("future");
            }
            if (subscription != null) {
                parts.add("subscription");
            }
            if (cursorDone()) {
                parts.add("cursorDone");
            }
            parts.add(Integer.toString(invocations));

            return Strings.join(' ', parts);
        }

        SState incrementInvocations(long batchSize, Runnable onMatch) {
            int nue = invocations + 1;
            if (lastFuture != null && (nue % batchSize == 0)) {
                lastFuture.channel().eventLoop().submit(onMatch);
                if (subscription != null) {
                    subscription.request(batchSize);
                }
            }
            return new SState(states, subscription, nue, lastFuture);
        }
    }
}
