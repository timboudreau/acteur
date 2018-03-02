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
package com.mastfrog.acteur.annotations;

import com.mastfrog.acteur.ChunkHandler;
import com.mastfrog.acteur.preconditions.Description;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate an Acteur with this, to have it run <i>as soon as the headers are
 * received</i>, disabling chunk aggregation. Use this if you, say, want to
 * redirect an HTTP POST request with a large upload, where you want to direct
 * the client to send it elsewhere without transferiring the request body
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Description("Marks an HTTP call as needing to be processed <i>as soon as "
        + "its headers arrive</i>, before the request body has been received "
        + "(meaning the HTTP event's content will be null).  Used for cases such "
        + "as redirecting a POST request with a large payload without having the "
        + "payload buffered in memory, and cases where large uploads should be "
        + "saved to a file rather than pulled into memory.")
public @interface Early {

    /**
     * If set, the acteur chain will be paused after the acteur annotated with
     * &#064;Empty, and this chunk handler will be called with each chunk of
     * content; you can call its resume() method when you're ready to continue
     * preparing the response.
     *
     * @return A handler
     */
    Class<? extends ChunkHandler> value() default ChunkHandler.class;

    /**
     * If true, automatically send the 100-CONTINUE response after the chunk
     * handler is installed.
     *
     * @return Whether or not to send the header
     */
    boolean send100continue() default false;
}
