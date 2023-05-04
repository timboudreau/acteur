/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur;

import com.google.inject.Singleton;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.util.time.TimeUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;
import javax.inject.Inject;

/**
 * A trivial default logger implementation
 *
 * @author Tim Boudreau
 */
@Singleton
class DefaultRequestLogger implements RequestLogger {

    @Inject
    DefaultRequestLogger() {
        // constructor for Graal's native-image code to detect
    }

    @Override
    public void onBeforeEvent(RequestID rid, Event<?> event) {
    }

    @Override
    public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
        int reqNum = rid == null ? -1 : rid.getIndex();
        StringBuilder sb = new StringBuilder(120)
                .append(reqNum).append('\t')
                .append(rid == null ? TimeUtil.format(Duration.ZERO) : TimeUtil.format(rid.getDuration()))
                .append('\t').append(event.remoteAddress())
                .append("\t").append(status)
                .append("\t").append(event);
        if (event instanceof HttpEvent) {
            CharSequence referrer = ((HttpEvent) event).header(Headers.REFERRER);
            if (referrer != null) {
                sb.append('\t').append(referrer);
            }
            CharSequence userAgent = ((HttpEvent) event).header(Headers.USER_AGENT);
            if (userAgent != null) {
                sb.append('\t').append(userAgent);
            }
        }
        System.out.println(sb);
    }
}
