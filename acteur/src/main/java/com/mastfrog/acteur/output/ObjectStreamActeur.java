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
package com.mastfrog.acteur.output;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Closables;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Built in acteur which will render a Stream put into request scope, using
 * whatever Codec and ArrayFormatting are in scope.
 *
 * @author Tim Boudreau
 */
public final class ObjectStreamActeur extends Acteur {

    @Inject
    ObjectStreamActeur(Closables clos, Stream stream, ArrayFormatting settings) {
        clos.add(stream);
        setChunked(true);
        reply(settings.successStatus());
        setResponseBodyWriter(ObjectStreamWriter.class);
    }

}
