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
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Named;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
@Defaults("static.base.url.path=")
public class ResourcesPage extends Page {

    /**
     * Settings key for the url path under which static resources live.
     * <b>Important</b>: This is a regex, and it needs to end in a capture group
     * - for example,
     * <code>static/(.*)</code>.
     */
    public static final String SETTINGS_KEY_STATIC_RESOURCES_BASE_URL_PATH = "static.base.url.path";

    @Inject
    public ResourcesPage(ActeurFactory af, StaticResources r, Settings settings) {
        add(af.matchMethods(Method.GET, Method.HEAD));
        String base = settings.getString(SETTINGS_KEY_STATIC_RESOURCES_BASE_URL_PATH);
        System.out.println("BAASI " + base);
        if (base != null) {
            add(af.matchPath(base));
        }
//        add(af.matchPath(r.getPatterns()));
        add(ResourceNameMatcher.class);
        add(af.sendNotModifiedIfIfModifiedSinceHeaderMatches());
        add(af.sendNotModifiedIfETagHeaderMatches());
        add(BytesWriter.class);
    }

    private static final class ResourceNameMatcher extends Acteur {

        @Inject
        ResourceNameMatcher(Event evt, StaticResources res, @Named(SETTINGS_KEY_STATIC_RESOURCES_BASE_URL_PATH) String base, ExpiresPolicy policy, Page page) throws UnsupportedEncodingException {
            String path = evt.getPath().toString();
            if (base != null && !base.isEmpty()) {
                Pattern p = Pattern.compile(base);
                Matcher m = p.matcher(path);
                if (m.find()) {
                    path = m.group(1);
                }
            }
            System.out.println("PATH NOW " + path);
            path = URLDecoder.decode(path, "UTF-8");
            for (String pat : res.getPatterns()) {
                System.out.println("COMPARE " + path + " and " + pat);
                if (path.equals(pat)) {
                    Resource r = res.get(path);
                    if (r == null) {
                        setState(new RejectedState());
                        return;
                    } else {
                        r.decoratePage(page, evt, path);
                        MediaType mimeType = r.getContentType();
                        if (mimeType != null) {
                            DateTime dt = policy.get(mimeType, Path.parse(path));
                            if (dt != null) {
                                add(Headers.EXPIRES, dt);
                            }
                        }
                        setState(new ConsumedLockedState(r));
                        return;
                    }
                }
            }
            setState(new RejectedState());
        }
    }

    private static class BytesWriter extends Acteur {

        @Inject
        BytesWriter(Event evt, Resource r) {
            setState(new RespondWith(HttpResponseStatus.OK));
            if (evt.getMethod() != Method.HEAD) {
                setResponseWriter(r.sender(evt));
            }
        }
    }
}
