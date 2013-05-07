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
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.acteur.resources.StaticResources.Resource;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URLDecoder;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
public class ResourcesPage extends Page {

    @Inject
    public ResourcesPage(Event evt, ActeurFactory af, StaticResources r) {
        add(af.matchMethods(Method.GET, Method.HEAD));
//        add(af.matchPath(r.getPatterns()));
        add(ResourceNameMatcher.class);
        add(ResourceFinder.class);
        add(af.sendNotModifiedIfIfModifiedSinceHeaderMatches());
        add(af.sendNotModifiedIfETagHeaderMatches());
        if (evt.getMethod() == Method.HEAD) {
            add(af.respondWith(HttpResponseStatus.OK));
        } else {
            add(BytesWriter.class);
        }
    }

    private static final class ResourceNameMatcher extends Acteur {

        @Inject
        ResourceNameMatcher(Event evt, StaticResources res) {
            String nm = evt.getPath().getLastElement().toString();
            nm = URLDecoder.decode(nm);
            for (String pat : res.getPatterns()) {
                if (nm.equals(pat)) {
                    setState(new ConsumedState());
                    return;
                }
            }
            setState(new RejectedState());
        }
    }

    private static class ResourceFinder extends Acteur {

        @Inject
        ResourceFinder(Event evt, Page page, StaticResources res, ExpiresPolicy policy) {
            String path = evt.getPath().toString();
            Resource r = res.get(path);
            if (r == null) {
                setState(new RejectedState());
            } else {
                r.decoratePage(page, evt);
                MediaType mimeType = r.getContentType();
                if (mimeType != null) {
                    DateTime dt = policy.get(mimeType, evt.getPath());
                    if (dt != null) {
                        add(Headers.EXPIRES, dt);
                    }
                }
                setState(new ConsumedLockedState(r));
            }
        }
    }

    private static class BytesWriter extends Acteur {

        @Inject
        BytesWriter(Event evt, Resource r) {
            setResponseWriter(r.sender(evt));
            setState(new RespondWith(HttpResponseStatus.OK));
        }
    }
}
