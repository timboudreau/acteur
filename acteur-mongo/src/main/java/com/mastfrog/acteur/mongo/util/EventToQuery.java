/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur.mongo.util;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.HttpEvent;
import com.mongodb.BasicDBObject;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;

/**
 * Takes the current HTTP request's URL parameters and converts them to a
 * BasicDBObject which can be used as a mongodb query. In particular, uses a
 * syntax where numeric properties can specify greater than, less than,
 * greater-than-or-equal or less-than-or-equal, e.g.
 * /users/foo/time?start=>=2505050
 * <p/>
 * Also extracts comma-delimited values into a list of strings
 *
 * @author Tim Boudreau
 */
public final class EventToQuery implements Provider<BasicDBObject> {

    private final Provider<HttpEvent> provider;
    private static final String _id = "_id";
    private final EventToQueryConfig config;

    @Inject
    public EventToQuery(Provider<HttpEvent> provider, EventToQueryConfig config) {
        this.provider = provider;
        this.config = config;
    }

    @Override
    public BasicDBObject get() {
        HttpEvent evt = provider.get();
        BasicDBObject obj = new BasicDBObject();
        for (String param : config) {
            boolean found = false;
            String v = evt.urlParameter(param);
            if (v == null) {
                continue;
            }
            try {
                v = URLDecoder.decode(v, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                throw new InvalidParameterException("UTF-8 not supported - WTF?");
            }
            for (Patterns p : Patterns.values()) {
                if (p.process(obj, param, v)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (v != null) {
                    try {
                        long val = Long.parseLong(v);
                        obj.put(param, val);
                    } catch (NumberFormatException ex) {
                        InvalidParameterException rethrow = new InvalidParameterException(
                                "Parameter " + param + " is not a number: " + v);
                        rethrow.addSuppressed(ex);
                        throw rethrow;
                    }
                }
            }
        }

        for (Map.Entry<String, String> e : evt.urlParametersAsMap().entrySet()) {
            if (config.isIgnoredParameter(e.getKey()) || config.isNumericParameter(e.getKey())) {
                continue;
            }
            String v = e.getValue();
            if (v.indexOf(',') > 0) {
                String[] spl = v.split(",");
                List<String> l = new LinkedList<>();
                for (String s : spl) {
                    l.add(s.trim());
                }
                obj.append(e.getKey(), l);
            } else {
                obj.append(e.getKey(), v);
            }
        }
        obj = onQueryConstructed(evt, obj);
        return obj;
    }

    protected BasicDBObject onQueryConstructed(HttpEvent evt, BasicDBObject obj) {
        if (!obj.isEmpty()) {
            String idparam = evt.urlParameter(_id);
            if (idparam != null) {
                try {
                    obj.put(_id, new ObjectId(idparam));
                } catch (IllegalArgumentException ex) {
                    InvalidParameterException pex = new InvalidParameterException("Invalid id: " + idparam);
                    pex.addSuppressed(ex);
                    throw pex;
                }
            }
        }
        return config.onQueryConstructed(evt, obj);
    }

    private static enum Patterns {

        GTE(">[e=]([\\d\\-]+)$"),
        LTE("<[e=]([\\d\\-]+)$"),
        GT(">([\\d\\-]+)$"),
        LT("<([\\-\\d]+)$");
        private final Pattern pattern;

        Patterns(String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        boolean process(BasicDBObject ob, String name, String val) {
            if (val != null) {
                try {
                    val = URLDecoder.decode(val, "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    throw new InvalidParameterException("UTF-8 not supported - WTF?");
                }
                return decorate(ob, name, val);
            }
            return false;
        }

        private Long get(String val) {
            Matcher m = pattern.matcher(val);
            boolean found = m.find();
            if (found) {
                return Long.parseLong(m.group(1));
            }
            return null;
        }

        boolean decorate(BasicDBObject ob, String name, String val) {
            try {
                Long value = get(val);
                if (value != null) {
                    ob.put(name, new BasicDBObject(toString(), value));
                    return true;
                }
            } catch (NumberFormatException nfe) {
                InvalidParameterException rethrow = new InvalidParameterException(
                        "Parameter " + name + " is not a number: " + val);
                rethrow.addSuppressed(nfe);
                throw rethrow;
            }

            return false;
        }

        @Override
        public String toString() {
            return '$' + name().toLowerCase();
        }
    }

    public interface QueryDecorator {

        BasicDBObject onQueryConstructed(HttpEvent evt, BasicDBObject obj);
    }
}
