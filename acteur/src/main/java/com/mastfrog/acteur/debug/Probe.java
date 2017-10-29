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
package com.mastfrog.acteur.debug;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.ActeurState;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Optionally may be bound to trace the activity in response to a request.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultProbe.class)
public interface Probe {

    default void onBeforeProcessRequest(RequestID id, Event<?> req) {

    }

    default void onBeforeRunPage(RequestID id, Event<?> evt, Page page) {

    }

    default void onActeurWasRun(RequestID id, Event<?> evt, Page page, Acteur acteur, ActeurState result) {

    }

    default void onFallthrough(RequestID id, Event<?> evt) {

    }

    default void onInfo(String info, Object... objs) {
        
    }

    default void onThrown(RequestID id, Event<?> evt, Throwable thrown) {
        
    }

    default void onBeforeSendResponse(RequestID id, Event<?> event, Acteur acteur, HttpResponseStatus status, boolean hasListener, Object message) {

    }
}
