/*
 * The MIT License
 *
 * Copyright 2014 tim.
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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Makes it easy to write MongoDB updates.
 *
 * @author Tim Boudreau
 */
public class UpdateBuilder {

    private final Map<String, Object> set = new HashMap<>();
    private final Map<String, Object> inc = new HashMap<>();
    private final Map<String, Object> push = new HashMap<>();
    private final Map<String, Object> pull = new HashMap<>();
    private final Set<String> unset = new HashSet<>();

    public BasicDBObject build() {
        BasicDBObject result = new BasicDBObject();
        if (!set.isEmpty()) {
            result.append("$set", new BasicDBObject(set));
        }
        if (!inc.isEmpty()) {
            result.append("$inc", new BasicDBObject(inc));
        }
        if (!push.isEmpty()) {
            result.append("$push", new BasicDBObject(push));
        }
        if (!pull.isEmpty()) {
            result.append("$pull", new BasicDBObject(push));
        }
        if (!unset.isEmpty()) {
            BasicDBList list = new BasicDBList();
            list.addAll(unset);
            result.append("$unset", list);
        }
        return result;
    }

    public static UpdateBuilder $() {
        return new UpdateBuilder();
    }

    public UpdateBuilder unset(String name) {
        unset.add(name);
        return this;
    }

    public UpdateBuilder set(String name, Object value) {
        set.put(name, value);
        return this;
    }

    public UpdateBuilder decrement(String name) {
        return increment(name, -1);
    }

    public UpdateBuilder increment(String name) {
        return increment(name, 1);
    }

    public UpdateBuilder increment(String name, int amount) {
        inc.put(name, amount);
        return this;
    }
    
    public UpdateBuilder increment(String name, long amount) {
        inc.put(name, amount);
        return this;
    }

    public UpdateBuilder push(String name, Object val) {
        push.put(name, val);
        return this;
    }

    public UpdateBuilder pull(String name, Object val) {
        pull.put(name, val);
        return this;
    }
}
