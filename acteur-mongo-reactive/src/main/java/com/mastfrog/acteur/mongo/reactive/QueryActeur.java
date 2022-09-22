/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.acteur.mongo.reactive;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteurbase.Chain;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.netty.buffer.ByteBuf;
import org.bson.conversions.Bson;

/**
 * Acteur which can be used in &#064;Concluders to run a query and stream its
 * results. Expects to find an instance of Bson (Document, etc.) and
 * CursorControl available (passed to next() in a preceding acteur in the
 * chain).
 *
 * @author Tim Boudreau
 */
public class QueryActeur extends Acteur {

    @SuppressWarnings("unchecked")
    @Inject
    QueryActeur(Bson query, CursorControl ctrl, MongoCollection<?> collection, Chain<Acteur, ? extends Chain<Acteur,?>> chain) {
        FindPublisher<ByteBuf> find = collection.find(query, ByteBuf.class);
        chain.add(WriteCursorContentsAsJSON.class);
        next(find);
    }
}
