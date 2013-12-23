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

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.util.RequestID;
import io.netty.channel.Channel;
import java.util.concurrent.CountDownLatch;

/**
 * Thing which instantiates pages and runs events
 *
 * @author Tim Boudreau
 */
@ImplementedBy(PagesImpl.class)
interface Pages {

    /**
     * Returns a CountDownLatch which can be used in tests to wait until a
     * request has been fully processed
     *
     * @param id The request unique id
     * @param event The event - the request itself
     * @param channel The channel
     * @return A latch which will count down when we're done
     */
    CountDownLatch onEvent(final RequestID id, final Event<?> event, final Channel channel);
}
