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

import com.google.common.net.MediaType;
import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.ResponseWriter;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultStaticResources.class)
public interface StaticResources {

    public static final String RESOURCE_CLASSES_KEY = "static.resource.classes";
    public static final String RESOURCE_NAMES_PREFIX = "static.resource.names";
    public static final String RESOURCE_FOLDERS_KEY = "static.resource.folders";

    Resource get(String path);

    String[] getPatterns();

    public static interface Resource {

        void decoratePage(Page page, Event evt, String path);

        ResponseWriter sender(Event evt);

        String getEtag();

        DateTime lastModified();

        MediaType getContentType();

        long getLength();
    }
}
