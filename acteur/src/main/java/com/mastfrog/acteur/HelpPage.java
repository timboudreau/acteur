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

import com.google.common.net.MediaType;
import static com.mastfrog.acteur.Help.HELP_URL_PATTERN_SETTINGS_KEY;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.settings.Settings;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Description("Provides a list of API calls this API supports")
@Methods(GET)
final class HelpPage extends Page {

    @Inject
    HelpPage(ActeurFactory af, Settings settings) {
        String pattern = settings.getString(HELP_URL_PATTERN_SETTINGS_KEY, "^help$");
        add(af.matchPath(pattern));
        add(af.sendNotModifiedIfIfModifiedSinceHeaderMatches());
        add(HelpActeur.class);
    }

    private static class HelpActeur extends Acteur {

        private final boolean html;

        @Inject
        HelpActeur(Application app, HttpEvent evt, Charset charset, ZonedDateTime serverStartTime) {
            this.html = "true".equals(evt.getParameter("html"));
            if (html) {
                add(Headers.CONTENT_TYPE, MediaType.HTML_UTF_8.withCharset(charset));
            }
            setChunked(true);
            setResponseWriter(new HelpWriter(html, app));
            add(Headers.CONNECTION, Connection.close);
            add(Headers.CACHE_CONTROL, CacheControl.PUBLIC_MUST_REVALIDATE);
            add(Headers.LAST_MODIFIED, serverStartTime);
        }

        @Override
        public void describeYourself(Map<String, Object> into) {
            into.put("Collects help info", true);
        }

        @Override
        public BaseState getState() {
            return new RespondWith(200);
        }

        public static final class HelpWriter extends ResponseWriter {

            private final boolean html;
            private final Application app;

            @Inject
            HelpWriter(boolean html, Application app) {
                this.html = html;
                this.app = app;
            }

            @Override
            public Status write(Event<?> evt, Output out, int iteration) throws Exception {
                Map<String, Object> help = app.describeYourself();
                if (html) {
                    StringBuilder sb = new StringBuilder("<html><head><style>body { font-family: 'Helvetica';}</style><title>")
                            .append(app.getName())
                            .append(" API Help</title></head>\n<body>\n<h1>").append(app.getName()).append(" API Help</h1>\n");
                    Description des = app.getClass().getAnnotation(Description.class);
                    if (des != null) {
                        sb.append("<p>").append(des.value()).append("</p>\n");
                    }
                    sb.append("<p><i style='font-size: 0.85em;'>Note that "
                            + "URL matching expressions are relative to the "
                            + "application base path, which can be set by passing "
                            + "<code>--basepath $PATH</code> on the command-line "
                            + "or set in a properties file."
                            + "<i></p>");
                    writeOut(null, help, sb, null);
                    sb.append("</body></html>\n");
                    out.write(sb.toString());
                    return Status.DONE;
                } else {
                    out.writeObject(help);
                    return Status.DONE;
                }
            }

            private String deBicapitalize(String s) {
                if (s == null) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                if (s.endsWith("Page")) {
                    s = s.substring(0, s.length() - "Page".length());
                }
                if (s.endsWith("Resource")) {
                    s = s.substring(0, s.length() - "Resource".length());
                }
                boolean lastWasCaps = true;
                for (char c : s.toCharArray()) {
                    if (Character.isUpperCase(c)) {
                        if (!lastWasCaps) {
                            sb.append(' ');
                        }
                        lastWasCaps = true;
                    } else {
                        lastWasCaps = false;
                    }
                    sb.append(c);
                }
                return sb.toString();
            }

            @SuppressWarnings("unchecked")
            private StringBuilder writeOut(String key, Object object, StringBuilder sb, String parentKey) {
                boolean code = ("PathRegex".equals(parentKey) || "Path".equals(parentKey) || "Methods".equals(parentKey))
                        && "value".equals(key);
                String codeOpen = code ? "<code>" : "";
                String codeClose = code ? "</code>" : "";
                String humanized = deBicapitalize(key);
                if (key == null || object instanceof Map<?,?>) {
                    Map<String, Object> m = Collections.checkedMap((Map<String,Object>) object, String.class, Object.class);
                    if (key != null) {
                        sb.append("\n<tr><th valign=\"left\" bgcolor='#FFEECC'>").append(humanized).append("</th><td>");
                    }
                    sb.append("<table>\n");
                    List<String> sortedKeys = new LinkedList<>(m.keySet());
                    Collections.sort(sortedKeys);
                    for (String k : sortedKeys) {
                        Object val = m.get(k);
                        sb.append(codeOpen);
                        writeOut(k, val, sb, key);
                        sb.append(codeClose);
                        if (key == null) {
                            sb.append("\n<tr><td colspan=2><hr/></td></tr>\n");
                        }
                    }
                    sb.append("\n</table>\n");
                    if (key != null) {
                        sb.append("\n</td></tr>\n");
                    }
                } else if (object instanceof CharSequence || object instanceof Boolean || object instanceof Number || object instanceof Enum) {
                    sb.append("\n<tr><th bgcolor='#DDDDDD'>").append(humanized).append("</th><td>")
                            .append(codeOpen)
                            .append(object)
                            .append(codeClose)
                            .append("</td></tr>\n");
                } else if (object != null && object.getClass().isArray()) {
                    String s = toString(object);
                    sb.append("\n<tr><th bgcolor='#DDDDDD'>").append(humanized).append("</th><td>")
                            .append(codeOpen)
                            .append(s)
                            .append(codeClose)
                            .append("</td></tr>\n");
                } else {
                    sb.append("\n<tr><th bgcolor='#DDDDDD'>").append(humanized).append("</th><td>")
                            .append(codeOpen)
                            .append(object)
                            .append(codeClose)
                            .append("</td></tr>\n");
                }
                return sb;
            }

            private String toString(Object o) {
                if (o.getClass().isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Array.getLength(o); i++) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(toString(Array.get(o, i)));
                    }
                    return sb.toString();
                } else {
                    return Objects.toString(o);
                }
            }

        }
    }
}
