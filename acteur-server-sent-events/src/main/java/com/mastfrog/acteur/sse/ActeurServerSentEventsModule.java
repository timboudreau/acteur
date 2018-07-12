/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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
package com.mastfrog.acteur.sse;

import com.google.inject.Module;
import com.google.inject.Binder;
import com.mastfrog.giulius.scope.ReentrantScope;

/**
 * Module that installs support for server sent events, and channel-specific
 * event sinks for sending messages to clients.  You will need to use
 * SseActeur as in the &#064;Concluders for at least one http endpoint,
 * and pass an EventChannelName in the context for the name of the channel
 * that connection will become subscribed to.
 *
 * @author Tim Boudreau
 */
public class ActeurServerSentEventsModule implements Module {

    private final ReentrantScope scope;

    public ActeurServerSentEventsModule(ReentrantScope scope) {
        this.scope = scope;
    }

    @Override
    public void configure(Binder binder) {
        scope.bindTypesAllowingNulls(binder, EventChannelName.class);
    }

}
