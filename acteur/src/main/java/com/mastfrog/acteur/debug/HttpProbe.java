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

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.ActeurState;
import io.netty.handler.codec.http.HttpResponseStatus;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public abstract class HttpProbe implements Provider<Probe> {

    private final ProbeImpl probe = new ProbeImpl();
    private boolean enabled = true;

    protected void onBeforeProcessRequest(RequestID id, HttpEvent req) {
        // do nothing
    }

    protected void onBeforeRunPage(RequestID id, HttpEvent evt, Page page) {
        // do nothing
    }

    protected void onActeurWasRun(RequestID id, HttpEvent evt, Page page, Acteur acteur, ActeurState result) {
        // do nothing
    }

    protected void onFallthrough(RequestID id, HttpEvent evt) {
        // do nothing
    }

    protected void onBeforeSendResponse(RequestID id, HttpEvent httpEvent, Acteur acteur, HttpResponseStatus status, boolean hasListener, Object message) {
        // do nothing
    }

    protected void onInfo(String info, Object... objs) {
        // do nothing
    }

    protected void onThrown(RequestID id, HttpEvent evt, Throwable thrown) {
        // do nothing
    }

    protected void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected boolean isEnabled() {
        return enabled;
    }

    @Override
    public final Probe get() {
        return probe;
    }

    class ProbeImpl implements Probe {

        @Override
        public void onBeforeProcessRequest(RequestID id, Event<?> req) {
            if (req instanceof HttpEvent && isEnabled()) {
                HttpProbe.this.onBeforeProcessRequest(id, (HttpEvent) req);
            }
        }

        @Override
        public void onBeforeRunPage(RequestID id, Event<?> evt, Page page) {
            if (evt instanceof HttpEvent && isEnabled()) {
                HttpProbe.this.onBeforeRunPage(id, (HttpEvent) evt, page);
            }
        }

        @Override
        public void onActeurWasRun(RequestID id, Event<?> evt, Page page, Acteur acteur, ActeurState result) {
            if (evt instanceof HttpEvent && isEnabled()) {
                HttpProbe.this.onActeurWasRun(id, (HttpEvent) evt, page, acteur, result);
            }
        }

        @Override
        public void onFallthrough(RequestID id, Event<?> evt) {
            if (evt instanceof HttpEvent && isEnabled()) {
                HttpProbe.this.onFallthrough(id, (HttpEvent) evt);
            }
        }

        @Override
        public void onBeforeSendResponse(RequestID id, Event<?> event, Acteur acteur, HttpResponseStatus status, boolean hasListener, Object message) {
            if (event instanceof HttpEvent && isEnabled()) {
                HttpProbe.this.onBeforeSendResponse(id, (HttpEvent) event, acteur, status, hasListener, message);
            }
        }

        @Override
        public void onInfo(String info, Object... objs) {
            if (isEnabled()) {
                HttpProbe.this.onInfo(info, objs);
            }
        }

        @Override
        public void onThrown(RequestID id, Event<?> evt, Throwable thrown) {
            if (evt instanceof HttpEvent && isEnabled()) {
                HttpProbe.this.onThrown(id, (HttpEvent) evt, thrown);
            }
        }
    }
}
