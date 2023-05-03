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

import static com.mastfrog.acteur.Help.HELP_HTML_URL_PATTERN_SETTINGS_KEY;
import static com.mastfrog.acteur.Help.HELP_URL_PATTERN_SETTINGS_KEY;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.Connection;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.mime.MimeType;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.supplierMap;
import static com.mastfrog.util.collections.CollectionUtils.toList;
import static com.mastfrog.util.collections.CollectionUtils.transform;
import com.mastfrog.util.strings.Strings;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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
        String pattern2 = settings.getString(HELP_HTML_URL_PATTERN_SETTINGS_KEY, "^help\\.html$");
        add(af.matchPath(false, pattern, pattern2));
        add(af.sendNotModifiedIfIfModifiedSinceHeaderMatches());
        add(HelpActeur.class);
    }

    private static class HelpActeur extends Acteur {

        private final boolean html;

        @Inject
        HelpActeur(Application app, HttpEvent evt, Charset charset, ZonedDateTime serverStartTime) {
            this.html = "true".equals(evt.urlParameter("html")) || evt.path().lastElement().extensionEquals("html");
            if (html) {
                add(Headers.CONTENT_TYPE, MimeType.HTML_UTF_8.withCharset(charset));
            } else {
                add(Headers.CONTENT_TYPE, MimeType.JSON_UTF_8);
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
                        sb.append("<h3><a style='text-decoration: none' href='#").append("cat-")
                                .append(namify(category)).append("'>")
                                .append(category)
                                .append("</a></h3>\n");
                        sb.append("<ol>\n");
                        for (String item : items) {
                            String nm = stripNamingConventions(item);
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

            private String stripNamingConventions(String item) {
                for (String suffix : new String[]{"Resource", "Helper", "Endpoint", "Page", "Acteur"}) {
                    if (item.endsWith(suffix)) {
                        return item.substring(0, item.length() - suffix.length());
                    }
                }
                return item;
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

            @SuppressWarnings("unchecked")
            private void findPaths(Object path, List<String> result) {
                if (path instanceof Map<?, ?>) {
                    Map<String, Object> m1 = (Map<String, Object>) path;
                    if (m1.containsKey("value")) {
                        Object val = m1.get("value");
                        if (val.getClass().isArray()) {
                            val = toList(val);
                        }
                        if (val instanceof List<?>) {
                            List<?> l = (List<?>) val;
                            for (Object o1 : l) {
                                if (o1 instanceof String) {
                                    result.add((String) o1);
                                } else if (o1 instanceof Pattern) {
                                    result.add(((Pattern) o1).pattern());
                                }
                            }
                        } else if (val instanceof String) {
                            result.add(val.toString());
                        } else if (val instanceof Pattern) {
                            result.add(((Pattern) val).pattern());
                        }
                    }
                }
            }

            private int counter = 0;

            private String humanizeRegex(String regex) {
                regex = Strings.literalReplaceAll("[^\\/]*?", "*", regex);
                regex = Strings.literalReplaceAll("[^\\/]+", "*", regex);
                regex = Strings.literalReplaceAll("[^\\/]+?", "*", regex);
                regex = Strings.literalReplaceAll("[^\\/]*", "*", regex);
                if (regex.startsWith("^\\/")) {
                    regex = regex.substring(3);
                }
                if (regex.startsWith("^") && regex.length() > 1) {
                    regex = "/" + regex.substring(1);
                }
                regex = Strings.literalReplaceAll("[a-fA-F0-9]{24}", "308139abd9ec0c8a650e83"
                        + Strings.toPaddedHex(new byte[]{(byte) counter++}), regex);
                if (regex.endsWith("$")) {
                    regex = regex.substring(0, regex.length() - 1);
                }
                regex = Strings.literalReplaceAll(".*?", "*", regex);
                regex = Strings.literalReplaceAll(".*", "*", regex);
                regex = Strings.literalReplaceAll(".+", "*", regex);
                regex = Strings.literalReplaceAll(".+?", "*", regex);
                regex = Strings.literalReplaceAll("\\/", "/", regex);
                return regex;
            }

            @SuppressWarnings("unchecked")
            private List<String> findPaths(Object o) {
                List<String> result = new ArrayList<>(4);
                if (o instanceof Map<?, ?>) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    Object path = m.get("Path");
                    if (path != null) {
                        findPaths(path, result);
                    }
                    if (result.isEmpty()) {
                        path = m.get("PathRegex");
                        if (path != null) {
                            findPaths(path, result);
                        }
                        if (!result.isEmpty()) {
                            result.replaceAll(regex -> humanizeRegex(regex) + "&nbsp;&nbsp;<i>(generated from regular expression)</i>");
                        }
                    }
                }
                result.sort((a, b) -> 0);
                return result;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Status write(Event<?> evt, Output out, int iteration) throws Exception {
                try {
                    Map<String, Object> help = app.describeYourself();
                    if (html) {
                        IndexBuilder index = new IndexBuilder();
                        StringBuilder sb = new StringBuilder("""
                                <!doctype html>
                                <html>
                                \t<head>
                                \t\t<meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <link href="https://fonts.googleapis.com/css?family=Mukta+Malar" rel="stylesheet">
                                \t\t<style>
                                \t\t\tbody {
                                \t\t\t\tfont-family: 'Mukta Malar'; color:#4e4e5e; margin: 2em;
                                \t\t\t}
                                 .arrayElement { border-right: 2px solid #ccc; font-size: 0.9em;}.arrayElement:last-of-type { border-right: none; }
                                table { font-size: 0.875em; border-spacing: 0; padding: 0; margin: 0; }
                                table table { border-spacing: 0; padding: 0; margin: 0; }
                                .singleValue { background-color: #ffe; border-bottom: 1px #bbb solid; border-right: 1px #bbb solid; }
                                td { border: none; padding-left: 1em; padding-right: 1em; }
                                th { border: none; padding-left: 1em; padding-right: 1em; }
                                .maptitle { background-color: #ccd; border-bottom: 1px #bbb; padding-left: 1em; }
                                .mapvalue { border-bottom: 1px #bbb; padding: 0; }
                                .title { min-width: 12rem; background-color: #dde; padding-left: 1em; padding-right: 1em; text-transform: capitalize}
                                .title,.maptitle { min-width: 12rem; border-bottom: 1px #bbb solid; color: black; }
                                .sample { display: block; max-width: 80%; overflow: auto; word-wrap: break-word; overflow-wrap: break-word }
                                .sample pre { word-wrap: break-word; overflow-wrap: break-word; white-space: pre-wrap; }
                                .value { padding-bottom: 1em;
                                    display: inline-block;
                                    min-height: 100%;
                                    vertical-align: middle;
                                    height: 100%;
                                    line-height: 1em;
                                    padding-top: 1em; }</style>
                                \t\t<title>""")
                                .append(app.getName())
                                .append("""
                                         API Help</title>
                                        \t\t</head>
                                        <body>
                                        \t\t<h1><a name='top'>""")
                                .append(app.getName()).append(" API Help</a></h1>\n");
                        Description des = app.getClass().getAnnotation(Description.class);
                        int offset = sb.length();
                        if (des != null) {
                            sb.append("<p>").append(des.value()).append("</p>\n");
                        }
                        sb.append("\t\t<p><i style='font-size: 0.85em;'>Note that "
                                + "URL matching expressions are relative to the "
                                + "application base path, which can be set by passing "
                                + "<code>--basepath $PATH</code> on the command-line "
                                + "or set in a properties file."
                                + "</i></p>\n");

                        List<Map.Entry<String, Object>> sorted = new ArrayList<>(help.entrySet());

                        sorted.sort((a, b) -> {
                            String acat = findCategory(a.getValue());
                            String bcat = findCategory(b.getValue());
                            int result = acat.compareTo(bcat);
                            if (result == 0) {
                                result = a.getKey().compareTo(b.getKey());
                            }
                            return result;
                        });

                        String topLink = "<a style='display: inline-block; "
                                + "margin-left: 2em; line-height: 0.9rem; "
                                + "vertical-align: middle; text-align: right; "
                                + "font-size: 0.9rem;' href='#top'>[Top]</a>";

                        String lastCategory = "";
                        for (Iterator<Map.Entry<String, Object>> it = sorted.iterator(); it.hasNext();) {
                            Map.Entry<String, Object> e = it.next();
                            String category = findCategory(e.getValue());
                            index.add(category, e.getKey());
                            if (!lastCategory.equals(category)) {
                                sb.append("<h1><a name='cat-").append(namify(category)).append("'>")
                                        .append(category).append(" Category</a>").append("</h1>\n");
                            }
                            lastCategory = category;
                            sb.append("<a name='").append(namify(e.getKey())).append("'>");
                            sb.append("<h2>").append(deBicapitalize(e.getKey())).append(topLink).append("</h2>").append("</a>\n");

                            if (e.getValue() instanceof Map<?, ?>) {

                                Map<String, Object> m = (Map<String, Object>) e.getValue();

                                Object methods = m.get("Methods");
                                if (methods instanceof Map<?, ?>) {
                                    Object val = ((Map<String, Object>) methods).get("value");
                                    if (val != null) {
                                        if (val.getClass().isArray()) {
                                            methods = Strings.join("/", CollectionUtils.toList(val));
                                        } else {
                                            methods = val.toString();
                                        }
                                    }
                                    m.remove("Match Method");
                                }
                                List<String> paths = findPaths(e.getValue());
                                if (!paths.isEmpty()) {
                                    // The help will contain both the annotation *and* the Acteur
                                    // generated from it, which will be duplicate information -
                                    // remove the acteurs describeYourself() output
                                    m.remove("Match Path (exact)");
                                    m.remove("Match Path (regex)");
                                }

                                Object description = m.get("Description");
                                Map<?, ?> desc = null;
                                sb.append("<p><i style='font-size:0.85em'>").append(category).append(" category</i></p>\n");
                                if (description instanceof Map<?, ?>) {
                                    desc = (Map<String, Object>) description;
                                    Object val = desc.get("value");
                                    if (val != null) {
                                        sb.append("<p>").append(val).append("</p>\n");
                                    }
                                    m.remove("Description");
                                }

                                Object examples = m.get("Examples");
                                if (examples instanceof Map<?, ?>) {
                                    Map<String, Object> exs = (Map<String, Object>) examples;
                                    List<String> keys = new ArrayList<>(exs.keySet());
                                    Collections.sort(keys);
                                    int ix = 1;
                                    for (String key : keys) {
                                        Object o = exs.get(key);
                                        if (o instanceof Map<?, ?>) {
                                            Map<?, ?> ex = (Map<?, ?>) o;
                                            String title = (String) ex.get("title");
                                            if (title != null && !title.isEmpty()) {
                                                sb.append("<h3>")
                                                        .append("Use-Case ").append(ix++).append(": ")
                                                        .append(title).append("</h3>\n");
                                            }
                                            String d = (String) ex.get("description");
                                            if (d != null) {
                                                sb.append("<p>").append(d).append("</p>\n");
                                            }
                                            String si = (String) ex.get("Sample Input");
                                            if (si != null) {
                                                sb.append("<b>Sample Input</b>:\n<div class='sample'>\n").append(si).append("</div>\n");
                                            }
                                            String so = (String) ex.get("Sample Output");
                                            if (so != null) {
                                                sb.append("<b>Sample Output</b>:\n<div class='sample'>\n").append(so).append("\n</div>\n");
                                            }
                                            String su = (String) ex.get("Sample URL");
                                            if (su != null) {
                                                sb.append("<p><b>Example:</b><code>");
                                                if (methods != null) {
                                                    sb.append(methods).append(' ');
                                                }
                                                sb.append(su).append("</code></p>");
                                            }
                                        }
                                    }
                                    m.remove("Examples");
                                }
                                Object sampleUrl = m.get("Sample URL");
                                if (sampleUrl != null) {
                                    sb.append("<p><b>Example:</b> <code>");
                                    if (methods != null) {
                                        sb.append(methods).append(' ');
                                    }
                                    sb.append(sampleUrl).append("</code></p>");
                                    m.remove("Sample URL");
                                } else if (methods != null) {
                                    if (!paths.isEmpty()) {
                                        String wd = paths.size() == 1 ? "<p><b>Example:</b> " : "<p><b>Examples:</b><br><ul>";
                                        sb.append(wd);
                                        for (String pth : paths) {
                                            sb.append("<li><code>").append(methods).append(" ").append(pth).append("</code></li>\n");
                                        }
                                        if (paths.size() > 1) {
                                            sb.append("</ul>");
                                        }
                                        sb.append("</p>");
                                    } else {
                                        sb.append("<p><b>Methods:</b><code> ").append(methods).append("</code></p>");
                                    }
                                } else if (!paths.isEmpty()) {
                                    String wd = paths.size() == 1 ? "<p><b>Example:</b> " : "<p><b>Examples:</b><br>";
                                    sb.append(wd);
                                    for (String pth : paths) {
                                        sb.append("&nbsp;").append("<code>").append(pth).append("</code>\n");
                                    }
                                    sb.append("</p>");
                                }
                                Object sampleInput = m.get("Sample Input");
                                if (sampleInput != null) {
                                    sb.append("<h4>Sample Input</h4>\n<blockquote class='sample'>\n");
                                    sb.append(sampleInput);
                                    sb.append("\n</blockquote>\n");
                                    m.remove("Sample Input");
                                }
                                Object sampleOutput = m.get("Sample Output");
                                if (sampleOutput != null) {
                                    sb.append("<h4>Sample Output</h4>\n<blockquote class='sample'>\n");
                                    sb.append(sampleOutput);
                                    sb.append("\n</blockquote>\n");
                                    m.remove("Sample Output");
                                }
                            }
                            sb.append("<h4>Request Processing Steps</h4>\n");
                            writeOut(null, e.getValue(), sb, null, 1);
                            if (it.hasNext()) {
                                sb.append("\n<hr/>\n");
                            }
                        }
                        sb.append("</body></html>\n");
                        sb.insert(offset, index);
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
                s = stripNamingConventions(s);
                StringBuilder sb = new StringBuilder();
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

            private boolean isIgnorable(String key, Object object) {
                if (key != null && key.equals(object)) {
                    return true;
                }
                if ("Category".equalsIgnoreCase(key) && "Web-API".equals(object)) {
                    return true;
                }
                if ("AuthenticationActeur".equalsIgnoreCase(key)) { // useless special case
                    return true;
                }
                return "value".equalsIgnoreCase(key) && "default".equals(object);
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            private Object filterObject(Object object) {
                // A single depth map with just a description is useless - flatten it
                if (object instanceof Map<?, ?>) {
                    Map<String, Object> m = (Map<String, Object>) object;
                    Set<Map.Entry> s = transform(m.entrySet(), e -> {
                        if (isIgnorable(e.getKey(), e.getValue())) {
                            return null;
                        }
                        return e;
                    });
                    if (s.size() == 1) {
                        return s.iterator().next().getValue();
                    }
                }
                return object;
            }

            private final String valueClass = " singleValue";

            StringBuilder maybeAppendValueClass(boolean yes, StringBuilder sb) {
                if (yes) {
                    sb.append(valueClass);
                }
                return sb;
            }

            @SuppressWarnings("unchecked")
            private StringBuilder writeOut(String key, Object object, StringBuilder sb, String parentKey, int depth) {
                boolean code = ("PathRegex".equals(parentKey) || "Path".equals(parentKey) || "Methods".equals(parentKey))
                        && "value".equals(key) || object instanceof Class<?> || "type".equalsIgnoreCase(parentKey);
                if (isIgnorable(key, object)) {
                    return sb;
                }
                object = filterObject(object);
                boolean isValue = "Value".equalsIgnoreCase(key);

                String codeOpen = code ? "<code>" : "";
                String codeClose = code ? "</code>" : "";
                String humanized = deBicapitalize(key);
                if (key == null || object instanceof Map<?, ?>) {
                    Map<String, Object> m = Collections.checkedMap((Map<String, Object>) object, String.class, Object.class);
                    if (key != null) {
                        sb.append("\n<tr class='maprow r").append(depth).append("'>\n" + "<th valign=\"left\" class='maptitle title").append(depth).append("'>\n")
                                .append(humanized).append("\n</th>\n<td class='mapvalue val")
                                .append(depth)
                                .append("'>\n");
                    }
                    sb.append("\n<table class='map t").append(depth).append("'>\n");
                    List<String> sortedKeys = new LinkedList<>(m.keySet());
                    Collections.sort(sortedKeys);
                    for (String k : sortedKeys) {
                        Object val = m.get(k);
                        sb.append(codeOpen);
                        writeOut(k, val, sb, key, depth + 1);
                        sb.append(codeClose);
                    }
                    sb.append("\n</table>\n");
                    if (key != null) {
                        sb.append("\n</td>\n</tr>\n");
                    }
                } else if (object instanceof CharSequence || object instanceof Boolean || object instanceof Number || object instanceof Enum) {
                    sb.append("\n<tr>\n");
                    if (!isValue) {
                        sb.append("\n<th class='title title").append(depth).append("'>\n")
                                .append(humanized).append("\n</th>\n");
                    }
                    sb.append("<td class='value val").append(depth);
                    maybeAppendValueClass(isValue, sb).append("'>\n")
                            .append(codeOpen)
                            .append(object)
                            .append(codeClose)
                            .append("\n</td>\n</tr>\n");
                } else if (object != null && (object.getClass().isArray() || object instanceof List<?>)) {
                    List<?> l = object instanceof List<?> ? (List<?>) object : toList(object);
                    if (l.size() == 1) {
                        sb.append("\n<tr>\n");
                        if (!isValue) {
                            sb.append("<th class='title title").append(depth)
                                    .append("'>\n").append(humanized)
                                    .append("\n</th>\n");
                        }

                        sb.append("\n<td class='value val").append(depth);
                        maybeAppendValueClass(isValue, sb).append("'>\n")
                                .append(codeOpen)
                                .append(toString(l.get(0)))
                                .append(codeClose)
                                .append("\n</td>\n</tr>\n");
                    } else {
                        StringBuilder elems = new StringBuilder("\n<table class='ta tad" + depth + "'>\n<tr>\n");
                        for (Object o : l) {
                            elems.append("\n<td class='arrayElement value val")
                                    .append(depth)
                                    .append("'>\n")
                                    .append(codeOpen)
                                    .append(toString(o))
                                    .append(codeClose)
                                    .append("\n</td>\n");
                        }
                        elems.append("</table>");
                        sb.append("\n<tr>\n");
                        if (!isValue) {
                            sb.append("<th class='title title")
                                    .append(depth);
                            maybeAppendValueClass(isValue, sb).append("'>\n").append(humanized)
                                    .append("\n</th>");
                        }
                        sb.append("\n<td class='value val").append(depth);
                        maybeAppendValueClass(isValue, sb)
                                .append("'>\n")
                                .append(elems)
                                .append("\n</td>\n</tr>\n");
                    }
                } else if (object instanceof Class<?>) {
                    String nm = ((Class<?>) object).getSimpleName();
                    if (((Class<?>) object).isArray()) {
                        nm += "[]";
                    }
                    sb.append("\n<tr>\n");
                    if (!isValue) {
                        sb.append("<th class='title title").append(depth)
                                .append("'>\n").append(humanized)
                                .append("\n</th>\n");
                    }
                    sb.append("<td class='value val").append(depth);
                    maybeAppendValueClass(isValue, sb).append("'>\n")
                            .append(codeOpen)
                            .append(nm)
                            .append(codeClose)
                            .append("\n</td>\n</tr>\n");
                } else {
                    sb.append("\n<tr>\n");
                    if (!isValue) {
                        sb.append("<th class='title title").append(depth)
                                .append("'>\n").append(humanized)
                                .append("\n</th>");
                    }
                    sb.append("\n<td class='value val").append(depth);
                    maybeAppendValueClass(isValue, sb).append("'>\n")
                            .append(codeOpen)
                            .append(object)
                            .append(codeClose)
                            .append("\n</td>\n</tr>\n");
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
