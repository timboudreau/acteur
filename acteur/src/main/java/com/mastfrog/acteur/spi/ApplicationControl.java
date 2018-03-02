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
package com.mastfrog.acteur.spi;

import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.errors.ResponseException;
import io.netty.channel.Channel;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/**
 * SPI interface which the request handling infrastructure uses to dispatch
 * events to and otherwise manage the application.
 *
 * @author Tim Boudreau
 */
public interface ApplicationControl {

    void enableDefaultCorsHandling();

    CountDownLatch onEvent(final Event<?> event, final Channel channel);

    void internalOnError(Throwable err);

    default void logErrors(CompletionStage<?> stage) {
        stage.whenComplete((ignored, thrown) -> {
            if (thrown != null) {
                while (thrown instanceof CompletionException && thrown.getCause() != null) {
                    thrown = thrown.getCause();
                }
                if (thrown instanceof ResponseException) {
                    return;
                }
                internalOnError(thrown);
            }
        });
    }
}
