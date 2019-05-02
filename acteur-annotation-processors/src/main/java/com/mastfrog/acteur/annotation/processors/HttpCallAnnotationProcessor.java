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
package com.mastfrog.acteur.annotation.processors;

import static com.mastfrog.acteur.annotation.processors.HttpCallAnnotationProcessor.EARLY_ANNOTATION;
import static com.mastfrog.acteur.annotation.processors.HttpCallAnnotationProcessor.HTTP_CALL_ANNOTATION;
import static com.mastfrog.acteur.annotation.processors.HttpCallAnnotationProcessor.INJECT_URL_PARAMS_AS_ANNOTATION;
import com.mastfrog.annotation.registries.AnnotationIndexFactory;
import com.mastfrog.annotation.registries.IndexGeneratingProcessor;
import com.mastfrog.annotation.registries.Line;
import com.mastfrog.util.service.ServiceProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
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
import javax.annotation.processing.Completion;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import static com.mastfrog.acteur.annotation.processors.HttpCallAnnotationProcessor.INJECT_BODY_AS_ANNOTATION;

/**
 * Processes the &#064;Defaults annotation, generating properties files in the
 * location specified by the annotation (the default is
 * com/mastfrog/defaults.properties).
 * <p/>
 * Keep this in a separate package so it can be detached from this JAR
 *
 * @author Tim Boudreau
 */
@ServiceProvider(Processor.class)
@SupportedAnnotationTypes({HTTP_CALL_ANNOTATION,
    EARLY_ANNOTATION,
    INJECT_BODY_AS_ANNOTATION,
    INJECT_URL_PARAMS_AS_ANNOTATION
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class HttpCallAnnotationProcessor extends IndexGeneratingProcessor<Line> {
    public static final String GENERATED_SOURCE_SUFFIX = "__GenPage";
    public static final String META_INF_PATH = "META-INF/http/pages.list";

    public static final String HTTP_CALL_ANNOTATION = "com.mastfrog.acteur.annotations.HttpCall";
    public static final String EARLY_ANNOTATION = "com.mastfrog.acteur.annotations.Early";
    public static final String INJECT_BODY_AS_ANNOTATION = "com.mastfrog.acteur.preconditions.InjectRequestBodyAs";
    public static final String INJECT_URL_PARAMS_AS_ANNOTATION = "com.mastfrog.acteur.preconditions.InjectUrlParametersAs";

    private static final String PRECURSORS_ANNOTATION = "com.mastfrog.acteur.annotations.Precursors";
    private static final String CONCLUDERS_ANNOTATION = "com.mastfrog.acteur.annotations.Concluders";
    private static final String INSTALL_CHUNK_HANDLER_ACTEUR = "com.mastfrog.acteur.annotations.InstallChunkHandler";
    private static final String GENERATED_FROM_ANNOTATION = "com.mastfrog.acteur.annotations.GeneratedFrom";

    private static final String ACTEUR_FQN = "com.mastfrog.acteur.Acteur";
    private static final String PAGE_FQN = "com.mastfrog.acteur.Page";

    public HttpCallAnnotationProcessor() {
        super(true, AnnotationIndexFactory.lines());
    }

    private boolean isPageSubtype(Element e) {
        return utils.isSubtypeOf(e, PAGE_FQN).isSubtype();
    }

    private boolean isActeurSubtype(Element e) {
        return utils.isSubtypeOf(e, ACTEUR_FQN).isSubtype();
    }

    private List<String> bindingTypes(Element el) {
        AnnotationMirror mirror = utils.findMirror(el, HTTP_CALL_ANNOTATION);
        List<String> result = new ArrayList<String>(15);
        if (mirror != null) {
            result.addAll(utils.typeList(mirror, "scopeTypes"));
        }
        mirror = utils.findMirror(el, INJECT_URL_PARAMS_AS_ANNOTATION);
        if (mirror != null) {
            result.addAll(utils.typeList(mirror, "value"));
        }
        mirror = utils.findMirror(el, INJECT_BODY_AS_ANNOTATION);
        if (mirror != null) {
            result.addAll(utils.typeList(mirror, "value"));
        }
        return result;
    }

    Set<Element> elements = new HashSet<>();

    int ix;

    private List<String> deferred = new LinkedList<String>();

    private void sanityCheckNonHttpCallElement(Element el) {
        AnnotationMirror inj = utils.findAnnotationMirror(el, INJECT_BODY_AS_ANNOTATION);
        if (inj != null) {
            utils.warn("@InjectRequestBodyAs annotation not applicable to classes not annotated with @HttpCall", el, inj);
        }
        AnnotationMirror inj2 = utils.findAnnotationMirror(el, INJECT_URL_PARAMS_AS_ANNOTATION);
        if (inj2 != null) {
            utils.warn("@InjectUrlParametersAs annotation not applicable to classes not annotated with @HttpCall", el, inj2);
        }
        AnnotationMirror early = utils.findAnnotationMirror(el, EARLY_ANNOTATION);
        if (early != null) {
            utils.warn("@Early annotation not applicable to classes not annotated with @HttpCall", el, early);
        }
        AnnotationMirror pre = utils.findAnnotationMirror(el, PRECURSORS_ANNOTATION);
        if (pre != null) {
            utils.warn("@Precursors annotation not applicable to classes not annotated with @HttpCall", el, pre);
        }
        AnnotationMirror conc = utils.findAnnotationMirror(el, CONCLUDERS_ANNOTATION);
        if (conc != null) {
            utils.warn("@Precursors annotation not applicable to classes not annotated with @HttpCall", el, conc);
        }
    }

    @Override
    public boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        Set<Element> all = new HashSet<>(roundEnv.getElementsAnnotatedWith(HttpCall.class));
//        all.addAll(roundEnv.getElementsAnnotatedWith(InjectUrlParametersAs.class));
//        all.addAll(roundEnv.getElementsAnnotatedWith(InjectRequestBodyAs.class));
//        all.addAll(roundEnv.getElementsAnnotatedWith(Early.class));
        Set<Element> all = utils.findAnnotatedElements(roundEnv);
        List<String> failed = new LinkedList<>();

        // Add in any types that could not be generated on a previous round because
        // they relied on a generated time (i.e. @InjectRequestBodyAs can't be copied
        // correctly into a generated page subclass if the type of its value will be
        // generated by Numble
        for (String type : deferred) {
            TypeElement retry = processingEnv.getElementUtils().getTypeElement(type);
            all.add(retry);
        }
        deferred.clear();
        try {
            for (Element e : all) {
                AnnotationMirror am = utils.findAnnotationMirror(e, HTTP_CALL_ANNOTATION);
                if (am == null) {
                    sanityCheckNonHttpCallElement(e);
                    continue;
                }
                Integer order = utils.annotationValue(am, "order", Integer.class, 0);
                
                boolean acteur = isActeurSubtype(e);
                if (!isPageSubtype(e) && !acteur) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Not a subclass of Page or Acteur: " + e.asType(), e);
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
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated " + className + " for " + e.asType(), e);
                    } else {
                        failed.add(te.getQualifiedName().toString());
                    }
                } else {
                    StringBuilder lines = new StringBuilder();
                    String canonicalName = utils.canonicalize(e.asType());
                    lines.append(canonicalName).append(":").append(order);
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
                    addLine(META_INF_PATH, lines.toString(), e);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(HttpCallAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        deferred.addAll(failed);
        return failed.isEmpty();
    }

    private int lineCount;

    protected boolean addLine(String path, String line, Element... el) {
        Line l = new Line(lineCount++, el, line);
        return addIndexElement(path, l, el);
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

    @SuppressWarnings("unchecked")
    private String generatePageSource(TypeElement typeElement, AtomicBoolean error) throws IOException {
        PackageElement pkg = findPackage(typeElement);
        String className = typeElement.getSimpleName() + GENERATED_SOURCE_SUFFIX;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(out, false, "UTF-8")) {
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
                if (am.getAnnotationType().toString().equals(PRECURSORS_ANNOTATION)) {
                    if (!am.getElementValues().entrySet().isEmpty()) {
                        for (String s : utils.typeList(am, "value")) {
                            precursorClassNames.add(s.replace('$', '.'));
                        }
                        continue;
                    }
                }
                if (am.getAnnotationType().toString().equals(EARLY_ANNOTATION)) {
                    denoumentClassNames.add(0, INSTALL_CHUNK_HANDLER_ACTEUR);
                }
                if (am.getAnnotationType().toString().equals(CONCLUDERS_ANNOTATION)) {
                    if (!am.getElementValues().entrySet().isEmpty()) {
                        for (String s : utils.typeList(am, "value")) {
                            denoumentClassNames.add(s.replace('$', '.'));
                        }
                        continue;
                    }
                }
                ps.print("@" + am.getAnnotationType());
                boolean first = true;
                Iterator<?> it = am.getElementValues().entrySet().iterator();
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
            ps.println("@" + GENERATED_FROM_ANNOTATION + "(" + typeElement.asType().toString() + ".class)");
            ps.println("\npublic final class " + className + " extends " + PAGE_FQN + " {\n");
            ps.println("    " + className + "(){");
            for (String p : precursorClassNames) {
                ps.println("        add(" + p + ".class); // precursor");
            }
            ps.println("        add(" + typeElement.getQualifiedName() + ".class); // generator");
            for (String p : denoumentClassNames) {
                ps.println("        add(" + p + ".class); // concluder");
            }
            ps.println("    }");
            ps.println("}");
            ps.flush();
        }
        if (!error.get()) {
            JavaFileObject jfo = processingEnv.getFiler().createSourceFile(pkg.getQualifiedName() + "." + className, typeElement);
            try (OutputStream stream = jfo.openOutputStream()) {
                stream.write(out.toByteArray());
            }
        }
        return pkg.getQualifiedName() + "." + className;
    }
}
