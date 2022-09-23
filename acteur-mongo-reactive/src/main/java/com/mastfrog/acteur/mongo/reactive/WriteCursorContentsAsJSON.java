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
package com.mastfrog.acteur.mongo.reactive;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.errors.Err;
import static com.mastfrog.acteur.headers.Headers.CONTENT_ENCODING;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import com.mongodb.reactivestreams.client.FindPublisher;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.GONE;
import java.util.function.BiConsumer;
import org.reactivestreams.Publisher;

/**
 *
 * @author Tim Boudreau
 */
@Description("Write out the contents of a cursor as JSON to the response, in small batches so the "
        + "entire result never needs to be held in memory.")
public class WriteCursorContentsAsJSON extends Acteur {

    @Inject
    @SuppressWarnings("unchecked")
    WriteCursorContentsAsJSON(Deferral def, CursorControl ctrl,
            Chain<Acteur, ? extends Chain<Acteur, ?>> chain,
            Closables clos, FindPublisher<?> find, Subscribers subscribers) {
        find(find, ctrl, def, chain, clos, subscribers);
    }

    @SuppressWarnings("unchecked")
    private <T> Publisher<T> find(Publisher<T> result, CursorControl ctrl, Deferral def, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, final Closables clos, Subscribers subscribers) {
        Publisher<T> target = ctrl._apply(result);

        def.defer((Resumer resumer) -> {
            if (ctrl.isFindOne()) {
                subscribers.first(target, new SingleResultResume<>(resumer));
            } else {
                resumer.resume(new CursorResult<>(result, null));
            }
        });
        if (ctrl.isFindOne()) {
            chain.add(SendSingleResult.class);
        } else {
            chain.add(SendCursorResult.class);
        }
        setChunked(true);
        next(result);
        return result;
    }

    static final class SingleResultResume<T> implements BiConsumer<T, Throwable> {

        private final Resumer resumer;

        public SingleResultResume(Resumer resumer) {
            this.resumer = resumer;
        }

        @Override
        public void accept(T t, Throwable thrwbl) {
            resumer.resume(new SingleResult(t, thrwbl));
        }
    }

    static final class SingleResult {

        final Object result;
        final Throwable thrown;

        public SingleResult(Object result, Throwable thrown) {
            this.result = result;
            this.thrown = thrown;
        }
    }

    @Description("Sends a single JSON object as a non-array result")
    static class SendSingleResult extends Acteur {

        @Inject
        SendSingleResult(SingleResult result, ApplicationControl ctrl) {
            if (result.thrown != null) {
                reply(Err.of(result.thrown));
                ctrl.internalOnError(result.thrown);
            } else {
                if (result.result == null) {
                    reply(GONE, "No such object");
                } else {
                    ok(result.result);
                }
            }
        }
    }

    static final class CursorResult<T> {

        final Publisher<T> cursor;
        final Throwable thrwbl;

        public CursorResult(Publisher<T> t, Throwable thrwbl) {
            this.cursor = t;
            this.thrwbl = thrwbl;
        }
    }

    @Description("Streams the contents of a cursor / FindIterable to the "
            + "response as a JSON array")
    static class SendCursorResult extends Acteur {

        @Inject
        @SuppressWarnings("unchecked")
        SendCursorResult(CursorResult res, HttpEvent evt, ApplicationControl ctrl, CursorWriterFactory fact) {
            if (res.thrwbl != null) {
                reply(Err.of(res.thrwbl));
            } else {
                setChunked(true);
                add(CONTENT_ENCODING, "identity");
                reply(HttpResponseStatus.OK);
                setResponseBodyWriter((ChannelFutureListener) fact.create(res.cursor));
            }
        }
    }
}
