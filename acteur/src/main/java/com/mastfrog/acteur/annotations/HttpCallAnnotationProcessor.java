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
package com.mastfrog.acteur.annotations;

import static com.mastfrog.acteur.annotations.HttpCall.GENERATED_SOURCE_SUFFIX;
import com.mastfrog.acteur.preconditions.InjectUrlParametersAs;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
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
@SupportedAnnotationTypes({"com.mastfrog.acteur.annotations.HttpCall",
    "com.mastfrog.acteur.preconditions.InjectRequestBodyAs",
    "com.mastfrog.acteur.preconditions.InjectParametersAsInterface"
})
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
        TypeElement pageType = elements.getTypeElement("com.mastfrog.acteur.Page");
        if (pageType == null) {
            return false;
        }
        return types.isSubtype(e.asType(), pageType.asType());
    }

    private boolean isActeurSubtype(Element e) {
        Types types = env.getTypeUtils();
        Elements elements = env.getElementUtils();
        TypeElement pageType = elements.getTypeElement("com.mastfrog.acteur.Acteur");
        if (pageType == null) {
            return false;
        }
        return types.isSubtype(e.asType(), pageType.asType());
    }

    private AnnotationMirror findMirror(Element el, Class<? extends Annotation> annoType) {
        for (AnnotationMirror mir : el.getAnnotationMirrors()) {
            TypeMirror type = mir.getAnnotationType().asElement().asType();
            if (annoType.getName().equals(type.toString())) {
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
        List<String> s = new ArrayList<>();
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

    private List<String> typeList(AnnotationMirror mirror, String param) {
        List<String> result = new ArrayList<>();
        if (mirror != null) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> x : mirror.getElementValues().entrySet()) {
                String annoParam = x.getKey().getSimpleName().toString();
                if (param.equals(annoParam)) {
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
                                            "Not a declared type: " + av);
                                }
                            } else {
                                env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                        "Annotation value for scopeTypes is not an AnnotationValue " + types(o));
                            }
                        }
                    } else if (x.getValue().getValue() instanceof DeclaredType) {
                        DeclaredType dt = (DeclaredType) x.getValue().getValue();
                        // Convert e.g. mypackage.Foo.Bar.Baz to mypackage.Foo$Bar$Baz
                        String canonical = canonicalize(dt.asElement().asType(), env.getTypeUtils());
                        result.add(canonical);

                    } else {
                        env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                "Annotation value for scopeTypes is not a list on " + mirror + " - " + types(x.getValue().getValue()));
                    }
                }
            }
        }
        return result;
    }

    private List<String> bindingTypes(Element el) {
        AnnotationMirror mirror = findMirror(el, HttpCall.class);
        List<String> result = typeList(mirror, "scopeTypes");
        mirror = findMirror(el, InjectUrlParametersAs.class);
        result.addAll(typeList(mirror, "value"));
        mirror = findMirror(el, InjectRequestBodyAs.class);
        result.addAll(typeList(mirror, "value"));
        return result;
    }

    StringBuilder lines = new StringBuilder();
    Set<Element> elements = new HashSet<>();

    int ix;

    private List<String> deferred = new LinkedList<String>();
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<Element> all = new HashSet<>(roundEnv.getElementsAnnotatedWith(HttpCall.class));
        all.addAll(roundEnv.getElementsAnnotatedWith(InjectUrlParametersAs.class));
        all.addAll(roundEnv.getElementsAnnotatedWith(InjectRequestBodyAs.class));
        List<String> failed = new LinkedList<>();
        
        // Add in any types that could not be generated on a previous round because
        // they relied on a generated time (i.e. @InjectRequestBodyAs can't be copied
        // correctly into a generated page subclass if the type of its value will be
        // generated by Numble
        for (String type : deferred) {
            TypeElement retry = env.getElementUtils().getTypeElement(type);
            all.add(retry);
        }
        deferred.clear();
        try {
            for (Element e : all) {
                Annotation anno = e.getAnnotation(HttpCall.class);
                int order = 0;
                if (anno instanceof HttpCall) {
                    order = ((HttpCall) anno).order();
                }
                if (anno == null) {
                    continue;
                }
                boolean acteur = isActeurSubtype(e);
                if (!isPageSubtype(e) && !acteur) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Not a subclass of Page or Acteur: " + e.asType(), e);
                    continue;
                }
                elements.add(e);
                if (acteur) {
                    TypeElement te = (TypeElement) e;
                    // Generating a page source may fail if InjectRequetsBodyAs or 
                    // similar may inject a class that has not been generated yet.
                    // So, if it failed, make note of it, don't write the file out
                    // and we'll solve it on a subsequent round
                    AtomicBoolean err = new AtomicBoolean();
                    String className = generatePageSource(te, err);
                    if (!err.get()) {
                        env.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated " + className + " for " + e.asType(), e);
                    } else {
                        failed.add(te.getQualifiedName().toString());
                    }
                } else {
                    if (lines.length() > 0) {
                        lines.append('\n');
                    }
                    lines.append(canonicalize(e.asType(), env.getTypeUtils())).append(":").append(order);
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
            }
            if (all.isEmpty() && lines.length() > 0) {
                if (!elements.isEmpty()) {
                    String path = HttpCall.META_INF_PATH;
                    try {
                        env.getMessager().printMessage(Diagnostic.Kind.NOTE,
                                "Found the following Page classes annotated with @HttpCall:\n" + lines);
                        FileObject fo = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                                "", path, elements.toArray(new Element[0]));
                        try (OutputStream out = fo.openOutputStream()) {
                            try (PrintStream ps = new PrintStream(out)) {
                                ps.println(lines);
                            }
                        }
                    } catch (FilerException ex) {
                        // Harmless;  sort this out at some point - we gat called
                        // again for our generated source
                        ex.printStackTrace();
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(HttpCallAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        deferred.addAll(failed);
        return failed.isEmpty();
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return Collections.<Completion>emptySet();
    }

    private PackageElement findPackage(Element el) {
        while (el != null && !(el instanceof PackageElement)) {
            el = el.getEnclosingElement();
        }
        return (PackageElement) el;
    }

    private String generatePageSource(TypeElement typeElement, AtomicBoolean error) throws IOException {
        PackageElement pkg = findPackage(typeElement);
        String className = typeElement.getSimpleName() + GENERATED_SOURCE_SUFFIX;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(out)) {
            ps.println("package " + pkg.getQualifiedName() + ";");
            TypeElement outer = typeElement;
            while (!outer.getEnclosingElement().equals(pkg)) {
                outer = (TypeElement) outer.getEnclosingElement();
            }
            ps.println("\nimport com.mastfrog.acteur.Page;");
            for (AnnotationMirror am : typeElement.getAnnotationMirrors()) {
                ps.println("import " + am.getAnnotationType() + ";");
            }
            ps.println();
            List<String> precursorClassNames = new ArrayList<>();
            List<String> denoumentClassNames = new ArrayList<>();
            ams:
            for (AnnotationMirror am : typeElement.getAnnotationMirrors()) {
                if (am.getAnnotationType().toString().equals(Precursors.class.getName())) {
                    if (!am.getElementValues().entrySet().isEmpty()) {
                        for (String s : typeList(am, "value")) {
                            precursorClassNames.add(s.replace('$', '.'));
                        }
                        continue;
                    }
                }
                if (am.getAnnotationType().toString().equals(Concluders.class.getName())) {
                    if (!am.getElementValues().entrySet().isEmpty()) {
                        for (String s : typeList(am, "value")) {
                            denoumentClassNames.add(s.replace('$', '.'));
                        }
                        continue;
                    }
                }
                ps.print("@" + am.getAnnotationType());
                boolean first = true;
                Iterator it = am.getElementValues().entrySet().iterator();
                if (it.hasNext()) {
                    while (it.hasNext()) {
                        Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> el = (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>) it.next();
                        if (first) {
                            ps.print('(');
                            first = false;
                        } else {
                            ps.print(',');
                        }
                        String key = "" + el.getKey();
                        ps.print(key.substring(0, key.length() - 2));
                        ps.print('=');
                        if ("<error>".equals(el.getValue().getValue())) {
                            error.set(true);
                            break ams;
                        } else {
                            ps.print(el.getValue());
                        }
                        if (!it.hasNext()) {
                            ps.print(")\n");
                        }
                    }
                } else {
                    ps.print('\n');
                }
            }
            ps.println("\npublic final class " + className + " extends Page {\n");
            ps.println("    " + className + "(){");
            for (String p : precursorClassNames) {
                ps.println("        add(" + p + ".class); //precursor");
            }
            ps.println("        add(" + typeElement.getQualifiedName() + ".class);");
            for (String p : denoumentClassNames) {
                ps.println("        add(" + p + ".class); //denoument");
            }
            ps.println("    }");
            ps.println("}");
        }
        if (!error.get()) {
            JavaFileObject jfo = env.getFiler().createSourceFile(pkg.getQualifiedName() + "." + className, typeElement);
            try (OutputStream stream = jfo.openOutputStream()) {
                stream.write(out.toByteArray());
            }
        }
        return pkg.getQualifiedName() + "." + className;
    }
}
