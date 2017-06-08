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
import com.mastfrog.acteur.CheckIfModifiedSinceHeader;
import com.mastfrog.acteur.CheckIfNoneMatchHeader;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.resources.Resource;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URLDecoder;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
@Description("Serves static files")
@Methods({Method.GET, Method.HEAD})
public class ResourcesPage extends Page {

    /**
     * Settings key for the url path under which static resources live.
     * <b>Important</b>: This is a regex, and it needs to end in a capture group
     * - for example, <code>static/(.*)</code>.
     */
    public static final String SETTINGS_KEY_STATIC_RESOURCES_BASE_URL_PATH = "static.base.url.path";

    @Inject
    public ResourcesPage(ActeurFactory af, StaticResources r, Settings settings) {
        String base = settings.getString(SETTINGS_KEY_STATIC_RESOURCES_BASE_URL_PATH);
        if (base != null && !base.isEmpty()) {
            add(af.matchPath(base));
        }
        add(ResourceNameMatcher.class);
        add(CheckIfModifiedSinceHeader.class);
        add(CheckIfNoneMatchHeader.class);
        add(BytesWriter.class);
    }

    static boolean chunked = true;

    private static final class ResourceNameMatcher extends Acteur {

        @Inject
        ResourceNameMatcher(HttpEvent evt, StaticResources res, Settings settings, ExpiresPolicy policy, Page page) throws Exception {
            String base = settings.getString(SETTINGS_KEY_STATIC_RESOURCES_BASE_URL_PATH);
            String path = evt.getPath().toString();
            if (base != null && !base.isEmpty()) {
                Pattern p = Pattern.compile(base);
                Matcher m = p.matcher(path);
                if (m.find()) {
                    path = m.group(1);
                }
            }
            path = URLDecoder.decode(path, "UTF-8");
            String[] patterns = res.getPatterns();
            if (patterns != null) {
                for (String pat : res.getPatterns()) {
                    if (path.equals(pat)) {
                        Resource r = res.get(path);
                        if (r == null) {
                            reject();
                            return;
                        } else {
                            r.decorateResponse(evt, path, response(), chunked);
                            MediaType mimeType = r.getContentType();
                            if (mimeType != null) {
                                ZonedDateTime dt = policy.get(mimeType, Path.parse(path));
                                if (dt != null) {
                                    add(Headers.EXPIRES, dt);
                                }
                            }
                            next(r);
                            return;
                        }
                    }
                }
            } else {
                Resource r = res.get(path);
                if (r != null) {
                    r.decorateResponse(evt, path, response(), chunked);
                    MediaType mimeType = r.getContentType();
                    if (mimeType != null) {
                        ZonedDateTime dt = policy.get(mimeType, Path.parse(path));
                        if (dt != null) {
                            add(Headers.EXPIRES, dt);
                        }
                    }
                    next(r);
                    return;
                }
            }
            reject();
        }
    }

    private static class BytesWriter extends Acteur {

        @Inject
        BytesWriter(HttpEvent evt, Resource r) throws Exception {
            setState(new RespondWith(HttpResponseStatus.OK));
            setChunked(false);
            if (evt.getMethod() != Method.HEAD) {
                Response rr = response();
                r.attachBytes(evt, rr, chunked);
            }
        }
    }
}
