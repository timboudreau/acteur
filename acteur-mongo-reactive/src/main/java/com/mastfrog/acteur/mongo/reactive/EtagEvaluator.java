/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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

import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.util.strings.Strings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscriber which receives mongodb documents to compute etag and / or last
 * modified.
 *
 * @author Tim Boudreau
 */
final class EtagEvaluator implements Subscriber<Document> {

    private static final byte[] NO_VALUE = new byte[]{0};
    private static final byte[] ZERO = new byte[]{127};
    private static final byte[] NON_ZERO = new byte[]{-1};
    private final MessageDigest digest;
    private final ETagResult result;
    private final Deferral.Resumer resumer;
    private final CacheHeaderInfo cacheInfo;
    private int count = 0;

    EtagEvaluator(ETagResult result, CacheHeaderInfo cacheInfo, Deferral.Resumer resumer) {
        this.result = result;
        this.cacheInfo = cacheInfo;
        digest = cacheInfo.newDigest();
        this.resumer = resumer;
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(Document doc) {
        count++;
        result.updateLastModified(cacheInfo.findLastModified(doc));
        for (String key : cacheInfo.etagFields()) {
            digest.update(key.getBytes(StandardCharsets.UTF_8));
            if ("_id".equals(key)) {
                ObjectId id = doc.getObjectId("_id");
                digest.update(id.toByteArray());
            } else {
                BsonDocument bd = doc.toBsonDocument();
                if (doc.containsKey(key)) {
                    BsonValue val = bd.get(key);
                    if (!updateDigestFromBson(val, digest)) {
                        Object o = doc.get(key);
                        digest.update(key.getBytes(StandardCharsets.US_ASCII));
                        byte[] bytes;
                        if (o == null) {
                            bytes = ZERO;
                        } else {
                            bytes = o.toString().getBytes(StandardCharsets.UTF_8);
                        }
                        digest.update(bytes);
                    }
                } else {
                    digest.update(NO_VALUE);
                }
            }
        }
    }

    @Override
    public void onError(Throwable thrwbl) {
        thrwbl.printStackTrace();
        result.setThrown(thrwbl);
        resumer.resume(result);
    }

    @Override
    public void onComplete() {
        try {
            if (!result.hasThrown()) {
                if (count == 0) {
                    digest.update((byte) 1);
                }
                result.setEtag(Strings.toBase64(digest.digest()));
                resumer.resume(result);
            }
        } catch (Exception | Error thr) {
            result.setThrown(thr);
            resumer.resume(result);
        }
    }

    private static boolean updateDigestFromBson(BsonValue val, final MessageDigest digest) {
        switch (val.getBsonType()) {
            case BINARY:
                digest.update(val.asBinary().getData());
                break;
            case INT32:
                digest.update(toBytes(val.asInt32().getValue()));
                break;
            case INT64:
                digest.update(toBytes(val.asInt64().getValue()));
                break;
            case DOUBLE:
                digest.update(toBytes(val.asDouble().getValue()));
                break;
            case TIMESTAMP:
                digest.update(toBytes(val.asTimestamp().getValue()));
                break;
            case OBJECT_ID:
                digest.update(val.asObjectId().getValue().toByteArray());
                break;
            case BOOLEAN:
                digest.update(val.asBoolean().getValue() ? NON_ZERO : ZERO);
                break;
            case ARRAY:
                BsonArray arr = val.asArray();
                List<BsonValue> vals = arr.getValues();
                for (int i = 0; i < vals.size(); i++) {
                    BsonValue v = vals.get(i);
                    digest.update(toBytes(i));
                    updateDigestFromBson(v, digest);
                }
                break;
            case DOCUMENT:
                BsonDocument sub = val.asDocument();
                sub.forEach((key, bsonValue) -> {
                    digest.update(key.getBytes(StandardCharsets.UTF_8));
                    updateDigestFromBson(bsonValue, digest);
                });
                break;
            case REGULAR_EXPRESSION:
                digest.update(val.asRegularExpression().getPattern().getBytes(StandardCharsets.UTF_8));
                break;
            case UNDEFINED:
            case NULL:
                digest.update(NO_VALUE);
                break;
            case DB_POINTER:
                digest.update(val.asDBPointer().getId().toByteArray());
                break;
            case DATE_TIME:
                digest.update(toBytes(val.asDateTime().getValue()));
                break;
            case STRING:
                digest.update(val.asString().getValue().getBytes(StandardCharsets.UTF_8));
                break;
            case SYMBOL:
                digest.update(val.asSymbol().getSymbol().getBytes(StandardCharsets.UTF_8));
                break;
            case DECIMAL128:
                digest.update(toBytes(val.asDecimal128().getValue().getHigh()));
                digest.update(toBytes(val.asDecimal128().getValue().getLow()));
                break;
            default:
                return false;
        }
        return true;
    }

    static byte[] toBytes(float val) {
        return toBytes(Float.floatToIntBits(val));
    }

    static byte[] toBytes(double val) {
        return toBytes(Double.doubleToLongBits(val));
    }

    static byte[] toBytes(int val) {
        byte[] result = new byte[]{(byte) (val & 0xFF), (byte) ((val >>> 8) & 0xFF), (byte) ((val >>> 16) & 0xFF), (byte) ((val >>> 24) & 0xFF)};
        return result;
    }

    static byte[] toBytes(long val) {
        byte[] result = new byte[]{(byte) (val & 0xFF), (byte) ((val >>> 8) & 0xFF), (byte) ((val >>> 16) & 0xFF), (byte) ((val >>> 24) & 0xFF), (byte) ((val >>> 32) & 0xFF), (byte) ((val >>> 40) & 0xFF), (byte) ((val >>> 48) & 0xFF), (byte) ((val >>> 56) & 0xFF)};
        return result;
    }

}
