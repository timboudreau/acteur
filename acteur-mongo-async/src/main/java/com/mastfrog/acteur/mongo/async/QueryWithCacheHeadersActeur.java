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
package com.mastfrog.acteur.mongo.async;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.CheckIfModifiedSinceHeader;
import com.mastfrog.acteur.CheckIfNoneMatchHeader;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.util.Strings;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoCollection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.concurrent.Callable;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * Like QueryActeur - for use as a &#064Concluder after passing the parameters
 * for a query; but performs an initial query returning only the _id fields of
 * matching documents, and uses those to generate an ETag header, which is then
 * checked before performing the full query. This does result it two queries
 * being performed for one HTTP request, but avoids sending the full body over
 * the wire in the case the client already has it.
 * <p>
 * Expects an instance of CacheHeaderInfo to be in scope, which describes what document
 * fields to use for etag and last-modified headers.
 * <p>
 * If a last-modified field is available on documents, prefer that (an etag will be
 * generated from the most recently modified document), since that requires a query
 * that fetches only a single document.
 *
 * @author Tim Boudreau
 */
public final class QueryWithCacheHeadersActeur extends Acteur {

    private static final Bson ID_ONLY = new Document("_id", 1);

    @Inject
    QueryWithCacheHeadersActeur(Bson query, CacheHeaderInfo cacheInfo, CursorControl ctrl, MongoCollection<?> collection, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, Deferral defer) {

        // Guarantee a consistent sort order, so etag does not vary
        CursorControl localCtrl = ctrl.copy().projection(cacheInfo.projection()).sort(ID_ONLY);
        if (cacheInfo.isEmpty()) {
            // Nothing to base cache headers on, skip ahead
            chain.add(QueryActeur.class);
            next();
            return;
        }
        chain.add(SendEtagResult.class);
        if (!cacheInfo.etagFields().isEmpty()) {
            chain.add(CheckIfNoneMatchHeader.class);
        }
        if (cacheInfo.lastModifiedField() != null) {
            chain.add(CheckIfModifiedSinceHeader.class);
        }
        chain.add(QueryActeur.class);
        defer.defer((Resumer res) -> {
            EtagResult result = new EtagResult();
            // If we will need to look for etags, wrap that in a callable
            Callable<Void> findEtag = () -> {
                FindIterable<Document> f = collection.withDocumentClass(Document.class).find(query);
                localCtrl.apply(f);
                final MessageDigest digest = MessageDigest.getInstance("MD5");
                f.forEach((Document t) -> {
                    ZonedDateTime lm = cacheInfo.findLastModified(t);
                    if (lm != null) {
                        if (result.lastModified == null) {
                            result.lastModified = lm;
                        } else if (lm.isAfter(result.lastModified)) {
                            result.lastModified = lm;
                        }
                    }
                    for (String key : cacheInfo.etagFields()) {
                        if ("_id".equals(key)) {
                            ObjectId id = t.getObjectId("_id");
                            digest.update(id.toByteArray());
                        } else {
                            Object o = t.get(key);
                            digest.update(key.getBytes(StandardCharsets.US_ASCII));
                            byte[] bytes;
                            if (o == null) {
                                bytes = new byte[]{0};
                            } else {
                                bytes = o.toString().getBytes(StandardCharsets.UTF_8);
                            }
                            digest.update(bytes);
                        }
                    }
                }, (Void t, Throwable thrwbl) -> {
                    if (thrwbl == null) {
                        result.etag = Strings.toNonPaddedBase36(digest.digest());
                    } else {
                        result.thrown = thrwbl;
                    }
                    res.resume(result);
                });
                return null;
            };
            if (cacheInfo.etagFields().isEmpty() && cacheInfo.lastModifiedField() != null) {
                // Do a simpler, single document query if there are no etag fields to look up
                collection.withDocumentClass(Document.class).find(query)
                        .projection(new Document(cacheInfo.lastModifiedField(), 1))
                        .sort(new Document(cacheInfo.lastModifiedField(), -1))
                        .first((Document t, Throwable thrwbl) -> {
                            if (thrwbl != null) {
                                result.thrown = thrwbl;
                            } else {
                                result.lastModified = cacheInfo.findLastModified(t);
                                result.etag = t.getObjectId("_id").toHexString();
                            }
                            res.resume(result);
                        });
            } else {
                // Fetch both etags and last modified in a single query
                findEtag.call();
            }
        });
        next();
    }

    static final class EtagResult {

        private String etag;
        private Throwable thrown;
        private ZonedDateTime lastModified;

    }

    static final class SendEtagResult extends Acteur {

        @Inject
        SendEtagResult(EtagResult res, Chain<Acteur, ? extends Chain<Acteur, ?>> chain) {
            if (res.thrown != null) {
                reply(Err.of(res.thrown));
            } else {
                if (res.etag != null) {
                    add(Headers.ETAG, res.etag);
                }
                if (res.lastModified != null) {
                    add(Headers.LAST_MODIFIED, res.lastModified);
                }
                next();
            }
        }
    }
}
