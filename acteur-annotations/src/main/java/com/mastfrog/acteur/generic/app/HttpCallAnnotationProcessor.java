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
package com.mastfrog.acteur.generic.app;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Completion;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.openide.util.lookup.ServiceProvider;

/**
 * Processes the &#064;Defaults annotation, generating properties files in the
 * location specified by the annotation (the default is
 * com/mastfrog/defaults.properties).
 * <p/>
 * Keep this in a separate package so it can be detached from this JAR
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes("com.mastfrog.acteur.generic.app.HttpCall")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class HttpCallAnnotationProcessor extends AbstractProcessor {

    private ProcessingEnvironment env;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.env = processingEnv;
    }

    private boolean isPageSubtype(Element e) {
        Types types = env.getTypeUtils();
        Elements elements = env.getElementUtils();
        TypeElement sceneType = elements.getTypeElement("com.mastfrog.acteur.Page");
        if (sceneType == null) {
            return false;
        }
        return types.isSubtype(e.asType(), sceneType.asType());
    }

    private AnnotationMirror findMirror(Element el) {
        for (AnnotationMirror mir : el.getAnnotationMirrors()) {
            TypeMirror type = mir.getAnnotationType().asElement().asType();
            if (HttpCall.class.getName().equals(type.toString())) {
                return mir;
            }
        }
        return null;
    }

    private String canonicalize(TypeMirror tm, Types types) {
        TypeElement e = (TypeElement) types.asElement(tm);
        StringBuilder nm = new StringBuilder(e.getQualifiedName().toString());
        Element enc = e.getEnclosingElement();
        while (enc != null && enc.getKind() != ElementKind.PACKAGE) {
            int ix = nm.lastIndexOf(".");
            if (ix > 0) {
                nm.setCharAt(ix, '$');
            }
            enc = enc.getEnclosingElement();
        }
        return nm.toString();
    }    
    
    private static String types(Object o) { //debug stuff
        List<String> s = new ArrayList<String>();
        Class<?> x = o.getClass();
        while (x != Object.class) {
            s.add(x.getName());
            for (Class<?> c : x.getInterfaces()) {
                s.add(c.getName());
            }
            x = x.getSuperclass();
        }
        StringBuilder sb = new StringBuilder();
        for (String ss : s) {
            sb.append(ss);
            sb.append(", ");
        }
        return sb.toString();
    }

    private List<String> bindingTypes(Element el) {
        AnnotationMirror mirror = findMirror(el);
        List<String> result = new ArrayList<>();
        if (mirror != null) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> x : mirror.getElementValues().entrySet()) {
                String annoParam = x.getKey().getSimpleName().toString();
                if ("scopeTypes".equals(annoParam)) {
                    if (x.getValue().getValue() instanceof List) {
                        List<?> list = (List<?>) x.getValue().getValue();
                        for (Object o : list) {
                            if (o instanceof AnnotationValue) {
                                AnnotationValue av = (AnnotationValue) o;
                                if (av.getValue() instanceof DeclaredType) {
                                    DeclaredType dt = (DeclaredType) av.getValue();
                                    // Convert e.g. mypackage.Foo.Bar.Baz to mypackage.Foo$Bar$Baz
                                    String canonical = canonicalize(dt.asElement().asType(), env.getTypeUtils());
                                    result.add(canonical);
                                } else {
                                    env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                            "Not a declared type: " + av + " on " + el.asType());
                                }
                            } else {
                                env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                        "Annotation value for scopeTypes is not an AnnotationValue " + el.asType());
                            }
                        }
                    } else {
                        env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                "Annotation value for scopeTypes is not a list on " + el.asType());
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        boolean result = true;
        StringBuilder lines = new StringBuilder();
        try {
            Set<Element> elements = new HashSet<>();
            for (Element e : roundEnv.getElementsAnnotatedWith(HttpCall.class)) {
                HttpCall anno = e.getAnnotation(HttpCall.class);
                if (anno == null) {
                    continue;
                }
                if (!isPageSubtype(e)) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Not a subclass of Page: " + e.asType(), e);
                    continue;
                }
                elements.add(e);
                if (lines.length() > 0) {
                    lines.append('\n');
                }
                lines.append(e.asType()).append(":").append(anno.order());
                List<String> bindingTypes = bindingTypes(e);
                if (!bindingTypes.isEmpty()) {
                    lines.append('{');
                    for (Iterator<String> it = bindingTypes.iterator(); it.hasNext();) {
                        lines.append(it.next());
                        if (it.hasNext()) {
                            lines.append(',');
                        } else {
                            lines.append('}');
                        }
                    }
                }
            }
            if (lines.length() > 0) {
                if (!elements.isEmpty()) {
                    String path = HttpCall.META_INF_PATH;
                    try {
                        env.getMessager().printMessage(Diagnostic.Kind.NOTE, "Found the following Page classes annotated with @HttpCall:\n" + lines);
                        FileObject fo = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                                "", path, elements.toArray(new Element[0]));
                        try (OutputStream out = fo.openOutputStream()) {
                            try (PrintStream ps = new PrintStream(out)) {
                                ps.println(lines);
                            }
                        }
                    } catch (FilerException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(HttpCallAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return result;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return Collections.<Completion>emptySet();
    }
}
