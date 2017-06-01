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
package com.mastfrog.acteur.resources;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public class MergedResources implements StaticResources {
    private final StaticResources[] resources;
    private boolean warned;

    @Inject
    public MergedResources(List<StaticResources> all) {
        resources = all.toArray(new StaticResources[0]);
    }

    public Resource get(String path) {
        for (StaticResources s : resources) {
            Resource result = s.get(path);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public String[] getPatterns() {
        List<String> result = new ArrayList<>();
        for (StaticResources r : resources) {
            String[] pats = r.getPatterns();
            if (pats != null) {
                result.addAll(Arrays.asList(r.getPatterns()));
            }
        }
        if (!warned) {
            warned = true;
            Set<String> s = new HashSet<>(result);
            if (s.size() != result.size()) {
                System.err.println("Duplicate resources in " + result);
            }
        }
        return result.isEmpty() ? null : result.toArray(new String[result.size()]);
    }
}
