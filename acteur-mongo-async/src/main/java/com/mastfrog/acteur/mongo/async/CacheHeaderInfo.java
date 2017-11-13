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

import com.mastfrog.util.time.TimeUtil;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.bson.Document;

/**
 * Info about how to generate a query for last modified and etag header fields.
 *
 * @author Tim Boudreau
 */
public final class CacheHeaderInfo {

    private final Set<String> etagFields = new HashSet<>(10);
    private String lastModifiedField;

    public CacheHeaderInfo(String... etagFields) {
        this.etagFields.addAll(Arrays.asList(etagFields));
    }

    public CacheHeaderInfo setLastModifiedField(String field) {
        lastModifiedField = field;
        return this;
    }

    public CacheHeaderInfo addEtagField(String field) {
        etagFields.add(field);
        return this;
    }

    public String lastModifiedField() {
        return lastModifiedField;
    }

    public Set<String> etagFields() {
        return etagFields;
    }

    @Override
    public String toString() {
        return "CacheHeaderInfo{" + "etagFields=" + etagFields + ", lastModifiedField=" + lastModifiedField + '}';
    }

    public boolean isEmpty() {
        return etagFields.isEmpty() && lastModifiedField == null;
    }

    Document projection() {
        Document result = new Document();
        Set<String> flds = new HashSet<>(etagFields);
        flds.add("_id");
        if (lastModifiedField != null) {
            flds.add(lastModifiedField);
        }
        for (String fld : flds) {
            result.append(fld, 1);
        }
        return result;
    }

    ZonedDateTime findLastModified(Document d) {
        if (lastModifiedField == null) {
            return null;
        }
        Object o = d.get(lastModifiedField);
        if (o instanceof Number) {
            return TimeUtil.fromUnixTimestamp(((Number) o).longValue());
        } else if (o instanceof Date) {
            return TimeUtil.fromUnixTimestamp(((Date) o).getTime());
        } else if (o instanceof String) {
            try {
                return TimeUtil.fromIsoFormat(o.toString());
            } catch (Exception e) {
                return TimeUtil.fromUnixTimestamp(Date.parse(o.toString()));
            }
        } else {
            return null;
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.etagFields);
        hash = 53 * hash + Objects.hashCode(this.lastModifiedField);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CacheHeaderInfo other = (CacheHeaderInfo) obj;
        if (!Objects.equals(this.lastModifiedField, other.lastModifiedField)) {
            return false;
        }
        if (!Objects.equals(this.etagFields, other.etagFields)) {
            return false;
        }
        return true;
    }
}
