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
import com.mastfrog.util.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.supplierMap;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
@Description(category = "Info", value = "Generates this API documentation, using the "
        + "internals of the code to generate documentation.")
@Methods(GET)
final class HelpPage extends Page {

    @Inject
    @SuppressWarnings("deprecation")
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
            this.html = "true".equals(evt.urlParameter("html"));
            if (html) {
                add(Headers.CONTENT_TYPE, MediaType.HTML_UTF_8.withCharset(charset));
            } else {
                add(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8);
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

            final class IndexBuilder {

                private final Map<String, LinkedList<String>> itemsForCategory = supplierMap(LinkedList::new);

                void add(String category, String name) {
                    itemsForCategory.get(category).add(name);
                }

                public String toString() {
                    StringBuilder sb = new StringBuilder();
                    List<String> categories = new ArrayList<>(itemsForCategory.keySet());
                    Collections.sort(categories);
                    for (String category : categories) {
                        List<String> items = itemsForCategory.get(category);
                        Collections.sort(items);
                        sb.append("<h3><a style='text-decoration: none' href='")
                                .append(namify(category)).append("'>")
                                .append(category)
                                .append("</a></h3>\n");
                        sb.append("<ol>\n");
                        for (String item : items) {
                            String nm = item;
                            if (item.endsWith("Resource")) {
                                nm = item.substring(0, item.length() - "Resource".length());
                            }
                            sb.append("<li><a style='text-decoration: none' href='#")
                                    .append(namify(item)).append("'>")
                                    .append(deBicapitalize(nm))
                                    .append("</a></li>\n");
                        }
                        sb.append("</ol>\n");
                    }
                    return sb.toString();
                }
            }

            private String namify(String s) {
                return s.toLowerCase().replace("\\s", "-");
            }

            @SuppressWarnings("unchecked")
            private String findCategory(Object o) {
                if (o instanceof Map<?, ?>) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    Object description = m.get("Description");
                    if (description instanceof Map<?, ?>) {
                        Map<String, Object> desc = (Map<String, Object>) description;
                        if (desc.get("category") instanceof String) {
                            return (String) desc.get("category");
                        }
                    }
                }
                return "Web-API";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Status write(Event<?> evt, Output out, int iteration) throws Exception {
                try {
                    Map<String, Object> help = app.describeYourself();
                    if (html) {
                        IndexBuilder index = new IndexBuilder();
                        StringBuilder sb = new StringBuilder("<html><head><style>body { font-family: 'Helvetica';}</style><title>")
                                .append(app.getName())
                                .append(" API Help</title></head>\n<body>\n<h1>").append(app.getName()).append(" API Help</h1>\n");
                        Description des = app.getClass().getAnnotation(Description.class);
                        int offset = sb.length();
                        if (des != null) {
                            sb.append("<p>").append(des.value()).append("</p>\n");
                        }
                        sb.append("<p><i style='font-size: 0.85em;'>Note that "
                                + "URL matching expressions are relative to the "
                                + "application base path, which can be set by passing "
                                + "<code>--basepath $PATH</code> on the command-line "
                                + "or set in a properties file."
                                + "</i></p>");

                        List<Map.Entry<String, Object>> sorted = new ArrayList<>(help.entrySet());

                        Collections.sort(sorted, (a, b) -> {
                            String acat = findCategory(a.getValue());
                            String bcat = findCategory(b.getValue());
                            int result = acat.compareTo(bcat);
                            if (result == 0) {
                                result = a.getKey().compareTo(b.getKey());
                            }
                            return result;
                        });

                        String lastCategory = "";
                        for (Map.Entry<String, Object> e : sorted) {
                            String category = findCategory(e.getValue());
                            index.add(category, e.getKey());
                            if (!lastCategory.equals(category)) {
                                sb.append("<h1><a name='cat-").append(namify(category)).append("'>").append(category).append("</a></h1>\n");
                            }
                            lastCategory = category;
                            sb.append("<a name='").append(namify(e.getKey())).append("'>");
                            sb.append("<h2>").append(deBicapitalize(e.getKey())).append("</h2></a>\n");

                            if (e.getValue() instanceof Map<?, ?>) {

                                Map<String, Object> m = (Map<String, Object>) e.getValue();

                                Object methods = m.get("Methods");
                                if (methods != null && methods instanceof Map<?, ?>) {
                                    Object val = ((Map<String, Object>) methods).get("value");
                                    if (val != null) {
                                        if (val.getClass().isArray()) {
                                            methods = Strings.join(',', CollectionUtils.toList(val));
                                        } else {
                                            methods = val.toString();
                                        }
                                    }
                                }

                                Object description = m.get("Description");
                                Map<?, ?> desc = null;
                                sb.append("<p><i style='font-size:0.85em'>").append(category).append("</i></p>\n");
                                if (description != null && description instanceof Map<?, ?>) {
                                    desc = (Map<String, Object>) description;
                                    Object val = desc.get("value");
                                    if (val != null) {
                                        sb.append("<p>").append(val).append("</p>\n");
                                    }
                                    m.remove("Description");
                                }
                                Object sampleUrl = m.get("Sample URL");
                                if (sampleUrl != null) {
                                    sb.append("<p><b>Ex:</b> <code>");
                                    if (methods != null) {
                                        sb.append(methods).append(' ');
                                    }
                                    sb.append(sampleUrl).append("</code></p>");
                                    m.remove("Sample URL");
                                } else if (methods != null) {
                                    sb.append("<p><b>Methods:</b><code> ").append(methods).append("</code></p>");
                                }
                                Object sampleInput = m.get("Sample Input");
                                if (sampleInput != null) {
                                    sb.append("<h4>Sample Input</h4>");
                                    sb.append(sampleInput);
                                    m.remove("Sample Input");
                                }
                                Object sampleOutput = m.get("Sample Output");
                                if (sampleOutput != null) {
                                    sb.append("<h4>Sample Output</h4>");
                                    sb.append(sampleOutput);
                                    m.remove("Sample Output");
                                }
                            }
                            writeOut(e.getKey(), e.getValue(), sb, null);
                        }
                        sb.append("</body></html>\n");
                        sb.insert(offset, index.toString());
                        out.write(sb.toString());
                        return Status.DONE;
                    } else {
                        out.writeObject(help);
                        return Status.DONE;
                    }
                } catch (Exception ex) {
                    app.control().internalOnError(ex);
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
                        && "value".equals(key) || object instanceof Class<?>;
                String codeOpen = code ? "<code>" : "";
                String codeClose = code ? "</code>" : "";
                String humanized = deBicapitalize(key);
                if (key == null || object instanceof Map<?, ?>) {
                    Map<String, Object> m = Collections.checkedMap((Map<String, Object>) object, String.class, Object.class);
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
                } else if (object instanceof Class<?>) {
                    String nm = ((Class<?>) object).getSimpleName();
                    if (((Class<?>) object).isArray()) {
                        nm += "[]";
                    }
                    sb.append("\n<tr><th bgcolor='#DDDDDD'>").append(humanized).append("</th><td>")
                            .append(codeOpen)
                            .append(nm)
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
