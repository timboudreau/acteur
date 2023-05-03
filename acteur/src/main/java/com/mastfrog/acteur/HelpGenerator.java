/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Acteur.WrapperActeur;
import com.mastfrog.acteur.annotations.Concluders;
import com.mastfrog.acteur.annotations.GeneratedFrom;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.annotations.Precursors;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Example;
import com.mastfrog.acteur.preconditions.Examples;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.collections.CollectionUtils.toList;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;

/**
 * Generates JSON help for the application; HelpPage converts this into HTML.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class HelpGenerator {

    private final Set<AnnotationDescriptionPlugin<?>> plugins = new HashSet<>();

    @Inject
    HelpGenerator() {

    }

    private <T extends Annotation> void register(AnnotationDescriptionPlugin<T> plugin) {
        this.plugins.add(plugin);
    }

    <T extends Annotation> boolean write(Application application, Map<String, Object> into, T annotation) {
        boolean result = false;
        for (AnnotationDescriptionPlugin<?> p : plugins) {
            result |= p.doWrite(application, into, annotation);
            if (result) {
                break;
            }
        }
        return result;
    }

    void generate(Application application, List<Object> pagesAndPageTypes, Map<String, Object> m) {
        for (Object o : pagesAndPageTypes) {
            if (o instanceof Class<?>) {
                Class<?> type = (Class<?>) o;
                Map<String, Object> pageDescription = new LinkedHashMap<>();
                String typeName = type.getName();
                if (typeName.endsWith(HttpCall.GENERATED_SOURCE_SUFFIX)) {
                    typeName = typeName.substring(0, typeName.length() - HttpCall.GENERATED_SOURCE_SUFFIX.length());
                }
                pageDescription.put("type", type.getName());
                String className = type.getSimpleName();
                if (className.endsWith(HttpCall.GENERATED_SOURCE_SUFFIX)) {
                    className = className.substring(0, className.length() - HttpCall.GENERATED_SOURCE_SUFFIX.length());
                }
                m.put(className, pageDescription);
                Annotation[] l = type.getAnnotations();
                for (Annotation a : l) {
                    if (a instanceof HttpCall) {
                        continue;
                    }
                    if (a instanceof Example) {
                        Example ex = (Example) a;
                        if (!ex.value().isEmpty()) {
                            pageDescription.put("Sample URL", ex.value());
                        }
                        if (ex.inputType() != Object.class) {
                            pageDescription.put("Sample Input", reflectAndJsonify(application, ex.inputField(), ex.inputType()));
                        }
                        if (ex.outputType() != Object.class) {
                            pageDescription.put("Sample Output", reflectAndJsonify(application, ex.outputField(), ex.outputType()));
                        }
                        continue;
                    }
                    Map<String, Object> annoDescription = new LinkedHashMap<>();
                    pageDescription.put(a.annotationType().getSimpleName(), annoDescription);
                    try {
                        introspectAnnotation(application, a, annoDescription);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    if (annoDescription.size() == 1 && "value".equals(annoDescription.keySet().iterator().next())) {
                        pageDescription.put(a.annotationType().getSimpleName(), annoDescription.values().iterator().next());
                    }
                }
                try {
                    Page p = (Page) application.getDependencies().getInstance(type);
                    p.application = application;
                    for (Object acteur : p.acteurs(application.isDefaultCorsHandlingEnabled())) {
                        Class<?> at = null;
                        if (acteur instanceof Acteur.WrapperActeur) {
                            at = ((WrapperActeur) acteur).type();
                        } else if (acteur instanceof Class<?>) {
                            at = (Class<?>) acteur;
                        }
                        if (at != null) {
                            Map<String, Object> callFlow = new HashMap<>();
                            for (Annotation a1 : at.getAnnotations()) {
                                introspectAnnotation(application, a1, callFlow);
                            }
                            if (!className.equals(at.getSimpleName())) {
                                for (Annotation a2 : at.getAnnotations()) {
                                    introspectAnnotation(application, a2, callFlow);
                                }
                            }
                            if (!callFlow.isEmpty()) {
                                pageDescription.put(at.getSimpleName(), callFlow);
                            }
                        } else if (acteur instanceof Acteur) {
                            Map<String, Object> callFlow = new HashMap<>();
                            for (Annotation a1 : acteur.getClass().getAnnotations()) {
                                introspectAnnotation(application, a1, callFlow);
                            }
                            ((Acteur) acteur).describeYourself(callFlow);
                            if (!callFlow.isEmpty()) {
                                pageDescription.put(acteur.toString(), callFlow);
                            }
                        }
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    // A page may legitimately be uninstantiable
                    e.printStackTrace();
                }
            } else if (o instanceof Page) {
                ((Page) o).describeYourself(m);
            }
        }

    }

    private static String deConstantNameify(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
            } else {
                if (capitalize) {
                    c = Character.toUpperCase(c);
                    capitalize = false;
                } else {
                    c = Character.toLowerCase(c);
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void introspectAnnotation(Application application, Annotation a, Map<String, Object> into) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (write(application, into, a)) {
            return;
        }
        if (a instanceof HttpCall) {
        } else if (a instanceof Precursors) {
            Precursors p = (Precursors) a;
            for (Class<?> t : p.value()) {
                for (Annotation anno : t.getAnnotations()) {
                    introspectAnnotation(application, anno, into);
                }
            }
        } else if (a instanceof Concluders) {
            Concluders c = (Concluders) a;
            for (Class<?> t : c.value()) {
                for (Annotation anno : t.getAnnotations()) {
                    introspectAnnotation(application, anno, into);
                }
            }
        } else if (a instanceof Examples) {
            Examples e = (Examples) a;
            int ix = 1;
            for (Examples.Case kase : e.value()) {
                Map<String, Object> m = new TreeMap<>();
                if (!kase.title().isEmpty()) {
                    m.put("title", kase.title());
                }
                if (!kase.description().isEmpty()) {
                    m.put("description", kase.description());
                }
                Example ex = kase.value();
                m.put("Sample URL", ex.value());
                if (ex.inputType() != Object.class) {
                    Object inp = reflectAndJsonify(application, ex.inputField(), ex.inputType());
                    m.put("Sample Input", inp);
                }
                if (ex.outputType() != Object.class) {
                    Object out = reflectAndJsonify(application, ex.outputField(), ex.outputType());
                    m.put("Sample Output", out);
                }
                into.put("example-" + ix++, m);
            }
        } else if (a instanceof GeneratedFrom) {
            GeneratedFrom gf = (GeneratedFrom) a;
            Class<?> from = gf.value();
            into.put("name", from.getName());
            Description desc = from.getAnnotation(Description.class);
            if (desc != null) {
                into.put("description", desc.value());
            }
        } else if (a != null) {
            Class<? extends Annotation> type = a.annotationType();
            for (java.lang.reflect.Method m : type.getMethods()) {
                switch (m.getName()) {
                    case "annotationType":
                    case "toString":
                    case "hashCode":
                        break;
                    default:
                        if (m.getParameterTypes().length == 0 && m.getReturnType() != null) {
                            Object mr = m.invoke(a);
                            if (mr.getClass().isArray()) {
                                mr = toList(mr);
                            }
//                            if (mr instanceof List<?>) {
//                                List<Object> mrs = new ArrayList<>(5);
//                                for (Object o : ((List<?>) mr)) {
//                                    if (o instanceof Annotation) {
//                                        Map<String, Object> ar = new LinkedHashMap<>();
//                                        introspectAnnotation((Annotation) o, ar);
//                                        mrs.add(ar);
//                                    } else {
//                                        mrs.add(o);
//                                    }
//                                }
//                                into.put(name, mrs);
//                            } else if (mr instanceof Annotation) {
//                                Map<String, Object> ar = new LinkedHashMap<>();
//                                introspectAnnotation((Annotation) mr, ar);
//                                into.put(name, ar);
//                            } else {
                            into.put(m.getName(), mr);
//                            }
                        }
                }
            }
            if (type.getAnnotation(Description.class) != null) {
                Description d = type.getAnnotation(Description.class);
                into.put("Description", d.value());
            }
        }
    }

    private String reflectAndJsonify(Application application, String field, Class<?> type) {
        try {
            Field f = type.getDeclaredField(field);
            f.setAccessible(true);
            if (f.getType() == String.class) {
                String res = (String) f.get(null);
                if (res != null) {
                    res = res.replaceAll("\\&", "&amp;");
                    res = Strings.literalReplaceAll("\"", "&quot;", res);
                    res = Strings.literalReplaceAll(">", "&gt;", res);
                    res = Strings.literalReplaceAll("<", "&lt;", res);
                }
                return "\n<pre>" + res + "</pre>\n";
            }
            Object o = f.get(null);
            ObjectMapper mapper = application.getDependencies().getInstance(ObjectMapper.class)
                    .copy()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return "<pre>" + mapper.writeValueAsString(o)
                    .replace("\"", "&quot;") + "</pre>";
        } catch (Exception e) {
            return "Could not lookup and generate JSON from " + type.getName() + "." + field + ": "
                    + e;
        }
    }

    /**
     * Allows code to be plugged in to contribute to the help page information
     * about specific types of annotation. To plug one in, implement and bind as
     * an eager singleton.
     *
     * @param <T>
     */
    public static abstract class AnnotationDescriptionPlugin<T extends Annotation> {

        final Class<T> annotationType;
        final HelpGenerator gen;

        @SuppressWarnings("LeakingThisInConstructor")
        protected AnnotationDescriptionPlugin(Class<T> annotationType, HelpGenerator gen) {
            this.annotationType = notNull("annotationType", annotationType);
            this.gen = gen;
            gen.register(this);
        }

        boolean doWrite(Application application, Map<String, Object> addTo, Annotation a) {
            boolean result = annotationType.isInstance(a);
            int oldSize = addTo.size();
            if (result) {
                write(application, addTo, annotationType.cast(a));
            }
            return result && oldSize != addTo.size();
        }

        /**
         * Convert typical Java constant naming conventions - e.g. FOO_BAR_BAZ
         * into a documentation-friendly string "Foo Bar Baz".
         *
         * @param s The string
         * @return A munged version of the passed strings
         */
        protected final String deConstantNameify(String s) {
            return HelpGenerator.deConstantNameify(s);
        }

        /**
         * Introspect, continuing the help mechanism, an annotation found when
         * examining the annotation passed to <code>write()</code> (but must not
         * <b>be</b> the annotation passed there). If your annotation, say,
         * inserts additional Acteurs in the chain, whose behavior should be
         * reflected in the help - &#064;Precursors and &#064Concluders are
         * examples of this - call this method to automatically look up and use
         * any plugins needed. Typically, your <code>write()</code> method will
         * create and add a new sub-map which you pass to this method, to the
         * map you were passed.
         *
         * @param application The application instance
         * @param a The annotation
         * @param into The map to write into
         * @throws IllegalAccessException If reflection fails
         * @throws IllegalArgumentException If reflection fails
         * @throws InvocationTargetException If reflection fails
         */
        protected final void introspectAnnotation(Application application, Annotation a, Map<String, Object> into) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            if (annotationType.isInstance(a)) {
                throw new IllegalArgumentException("Already introspecting " + a + " - use this method for "
                        + " annotations found indirectly when examining it.  Here this would just result"
                        + " in an endless loop.");
            }
            gen.introspectAnnotation(application, a, into);
        }

        /**
         * Used for examples - looks up the given static field by reflection and
         * returns an html-ized JSONified view of its contents.
         *
         * @param application The application
         * @param field The field name
         * @param type The type the field is on
         * @return A description or null if something goes wrong
         */
        protected final String reflectAndJsonify(Application application, String field, Class<?> type) {
            return gen.reflectAndJsonify(application, field, type);
        }

        /**
         * Add key/value pairs to the passed map, describing this annotation,
         * for the JSON help description.
         *
         * @param addTo The map to add to
         * @param anno The annotation
         */
        protected abstract void write(Application application, Map<String, Object> addTo, T anno);
    }
}
