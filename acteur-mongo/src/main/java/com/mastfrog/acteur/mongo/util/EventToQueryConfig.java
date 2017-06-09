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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.mongo.util.EventToQuery.QueryDecorator;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * @author tim
 */
public class EventToQueryConfig implements Iterable<String> {

    private final Set<String> ignored;
    private final Set<String> numeric;
    private final List<QueryDecorator> decorators;

    EventToQueryConfig(Set<String> ignored, Set<String> numeric, List<QueryDecorator> decorators) {
        this.ignored = ignored;
        this.numeric = numeric;
        this.decorators = decorators;
    }

    public Iterator<String> iterator() {
        return numeric.iterator();
    }

    boolean isIgnoredParameter(String name) {
        return ignored.contains(name);
    }

    boolean isNumericParameter(String name) {
        return numeric.contains(name);
    }

    public static EventToQueryConfig.Builder builder() {
        return new Builder();
    }
    
    BasicDBObject onQueryConstructed(HttpEvent evt, BasicDBObject obj) {
        for (QueryDecorator d : decorators) {
            obj = d.onQueryConstructed(evt, obj);
        }
        return obj;
    }

    public static class Builder {

        private final List<String> ignored = new ArrayList<>();
        private final List<String> numeric = new ArrayList<>();
        private final List<QueryDecorator> decorators = new ArrayList<>();

        Builder() {
            ignored.add("_id");
        }

        public Builder addIgnored(String... name) {
            for (String s : name) {
                ignored.add(s);
            }
            return this;
        }

        public Builder addNumeric(String... name) {
            for (String s : name) {
                numeric.add(s);
            }
            return this;
        }
        
        public Builder add(QueryDecorator decorator) {
            decorators.add(decorator);
            return this;
        }

        public EventToQueryConfig build() {
            Collections.sort(ignored);
            Collections.sort(numeric);
            List<QueryDecorator> l = ImmutableList.copyOf(decorators);
            return new EventToQueryConfig(ImmutableSortedSet.copyOf(ignored), ImmutableSortedSet.copyOf(numeric), decorators);
        }
    }
}
