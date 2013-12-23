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
package com.mastfrog.acteur;

import com.mastfrog.acteur.util.Headers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.settings.Settings;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
final class HelpPage extends Page {

    public static final String HELP_URL_PATTERN_SETTINGS_KEY = "helpUrlPattern";

    @Inject
    HelpPage(ActeurFactory af, Settings settings, DateTime serverStartTime) {
        String pattern = settings.getString(HELP_URL_PATTERN_SETTINGS_KEY, "^help$");
        getResponseHeaders().addCacheControl(CacheControlTypes.Public);
        getResponseHeaders().setLastModified(serverStartTime);
        add(af.matchPath(pattern));
        add(af.matchMethods(Method.GET));
        add(af.sendNotModifiedIfIfModifiedSinceHeaderMatches());
        add(HelpActeur.class);
    }

    private static class HelpActeur extends Acteur {

        private final Application app;
        private final boolean html;
        private final ObjectMapper mapper;
        private final Charset charset;

        @Inject
        HelpActeur(Application app, HttpEvent evt, ObjectMapper mapper, Charset charset) {
            this.app = app;
            this.charset = charset;
            this.html = "true".equals(evt.getParameter("html"));
            if (html) {
                add(Headers.CONTENT_TYPE, MediaType.HTML_UTF_8.withCharset(charset));
            }
            setResponseWriter(new HelpWriter(mapper, html, app));
            this.mapper = mapper;   
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Collects help info", true);
        }

        @Override
        public State getState() {
            return new RespondWith(200);
        }

        public static final class HelpWriter extends ResponseWriter {
            private final ObjectMapper mapper;
            private final boolean html;
            private final Application app;
            @Inject
            HelpWriter (ObjectMapper mapper, boolean html, Application app) {
                this.mapper = mapper;
                this.html = html;
                this.app = app;
            }

            @Override
            public Status write(Event<?> evt, Output out, int iteration) throws Exception {
                Map<String, Object> help = app.describeYourself();
                if (html) {
                    StringBuilder sb = new StringBuilder("<html><head><title>")
                            .append(app.getName())
                            .append(" API Help</title></head>\n<body>\n<h1>").append(app.getName()).append("API Help</h1>\n\n"
                            + "<p>Note that most URL paths are relative to the application base path");

                    writeOut(null, help, sb);
                    sb.append("</body></html>\n");
                    out.write(sb.toString());
                    return Status.DONE;
                } else {
                    out.writeObject(help);
                    return Status.DONE;
                }
            }

            private StringBuilder writeOut(String key, Object object, StringBuilder sb) {
                if (key == null || object instanceof Map) {
                    Map<String, Object> m = Collections.checkedMap((Map) object, String.class, Object.class);
                    if (key != null) {
                        sb.append("\n<tr><th valign=\"left\" bgcolor='#DDFFDD'>").append(key).append("</th><td>");
                    }
                    sb.append("<table>\n");
                    for (Map.Entry<String, Object> e : m.entrySet()) {
                        writeOut(e.getKey(), e.getValue(), sb);
                        if (key == null) {
                            sb.append("\n<tr><td colspan=2><hr/></td></tr>\n");
                        }
                    }
                    sb.append("\n</table>\n");
                    if (key != null) {
                        sb.append("\n</td></tr>\n");
                    }
                } else if (object instanceof CharSequence || object instanceof Boolean || object instanceof Number || object instanceof Enum) {
                    sb.append("\n<tr><th bgcolor='#DDDDDD'>").append(key).append("</th><td>").append(object).append("</td></tr>\n");
                } else if (object.getClass().isArray()) {
                    try {
                        String s = mapper.writeValueAsString(object);
                        sb.append("\n<tr><th bgcolor='#DDDDDD'>").append(key).append("</th><td>").append(s).append("</td></tr>\n");
                    } catch (IOException ex) {
                        Logger.getLogger(HelpPage.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    sb.append("\n<tr><th bgcolor='#DDDDDD'>").append(key).append("</th><td>").append(object).append("</td></tr>\n");
                }
                return sb;
            }

        }
    }
}
