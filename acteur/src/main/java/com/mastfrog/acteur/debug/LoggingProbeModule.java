/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.ActeurState;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.text.MessageFormat;

/**
 * Guice module which binds {@link Probe} to an implementation that
 * simply writes all activity to <code>System.out</code>.
 *
 * @author Tim Boudreau
 */
public final class LoggingProbeModule implements Probe, Module {

    @Override
    public void onBeforeProcessRequest(RequestID id, Event<?> req) {
        System.out.println("onBeforeProcessRequest " + id + " " + req);
    }

    @Override
    public void onBeforeRunPage(RequestID id, Event<?> evt, Page page) {
        System.out.println("onBeforeRunPage " + id + " " + evt + " " + page);
    }

    @Override
    public void onActeurWasRun(RequestID id, Event<?> evt, Page page, Acteur acteur, ActeurState result) {
        System.out.println("onActeurWasRun " + id + " " + evt + " " + page + " " + acteur.getClass().getSimpleName() + " " + acteur + " " + result);
    }

    @Override
    public void onFallthrough(RequestID id, Event<?> evt) {
        System.out.println("onFallthrough " + id + " " + evt);
    }

    @Override
    public void onInfo(String info, Object... objs) {
        System.out.println("onInfo " + MessageFormat.format(info, objs));
    }

    @Override
    public void onThrown(RequestID id, Event<?> evt, Throwable thrown) {
        System.out.println("onThrown " + id + " evt " + thrown);
        thrown.printStackTrace(System.out);
    }

    @Override
    public void onBeforeSendResponse(RequestID id, Event<?> event, Acteur acteur, HttpResponseStatus status, boolean hasListener, Object message) {
        System.out.println("onBeforeSendResponse " + id + " " + event + " " + acteur.getClass().getSimpleName() + " " + acteur + " " + status + " " + (hasListener ? "listener" : "no-listener") + " msg " + message);
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(Probe.class).toInstance(this);
    }

}
