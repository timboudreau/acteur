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

import com.mastfrog.acteur.util.RequestID;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

/**
 * A trivial default logger implementation
 *
 * @author Tim Boudreau
 */
class DefaultRequestLogger implements RequestLogger {

    @Override
    public void onBeforeEvent(RequestID rid, Event event) {
        int reqNum = rid == null ? -1 : rid.getIndex();
        HttpMessage msg = event.getRequest();
        String uri = msg instanceof HttpRequest ? ((HttpRequest) msg).getUri() : "";
        System.out.println(reqNum + " " + event.getRemoteAddress() + " " + event.getMethod() + " " + event.getPath() + " " + uri);
    }

    @Override
    public void onRespond(RequestID rid, Event event, HttpResponseStatus status) {
        int reqNum = rid == null ? -1 : rid.getIndex();
        System.out.println(" " + " " + reqNum + " " + status + " " + FORMAT.print(rid == null ? Duration.ZERO.toPeriod() : rid.getDuration().toPeriod()));
    }
    private static final PeriodFormatter FORMAT =
            new PeriodFormatterBuilder().appendMinutes()
            .appendSeparatorIfFieldsBefore(":")
            .appendSecondsWithMillis().toFormatter();
}
