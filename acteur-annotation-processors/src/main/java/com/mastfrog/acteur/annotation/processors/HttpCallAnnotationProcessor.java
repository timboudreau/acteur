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
import static com.mastfrog.acteur.annotation.processors.HttpCallAnnotationProcessor.INJECT_BODY_AS_ANNOTATION;
import static com.mastfrog.acteur.annotation.processors.HttpCallAnnotationProcessor.INJECT_URL_PARAMS_AS_ANNOTATION;
import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import static com.mastfrog.annotation.AnnotationUtils.types;
import com.mastfrog.annotation.registries.AnnotationIndexFactory;
import com.mastfrog.annotation.registries.IndexGeneratingProcessor;
import com.mastfrog.annotation.registries.Line;
import com.mastfrog.code.generation.common.LinesBuilder;
import com.mastfrog.function.TriConsumer;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.service.ServiceProvider;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

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

    private boolean isPageSubtype(Element e, AnnotationUtils utils) {
        return utils.isSubtypeOf(e, PAGE_FQN).isSubtype();
    }

    private boolean isActeurSubtype(Element e, AnnotationUtils utils) {
        return utils.isSubtypeOf(e, ACTEUR_FQN).isSubtype();
    }

    private List<String> bindingTypes(Element el, AnnotationUtils utils) {
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

    private final List<String> deferred = new LinkedList<>();

    private void sanityCheckNonHttpCallElement(Element el, AnnotationUtils utils) {
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
            utils.log("@Early annotation not applicable to classes not annotated with @HttpCall", el, early);
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
    public boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, AnnotationUtils utils) {
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
                    sanityCheckNonHttpCallElement(e, utils);
                    continue;
                }
                int order = utils.annotationValue(am, "order", Integer.class, 0);

                boolean acteur = isActeurSubtype(e, utils);
                if (!isPageSubtype(e, utils) && !acteur) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Not a subclass of Page or Acteur: " + e.asType(), e, am);
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
                    String className = generatePageSource(te, err, utils);
                    if (!err.get()) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated " + className + " for " + e.asType(), e);
                    } else {
                        failed.add(te.getQualifiedName().toString());
                    }
                } else {
                    StringBuilder lines = new StringBuilder();
                    String canonicalName = utils.canonicalize(e.asType());
                    lines.append(canonicalName).append(":").append(order);
                    List<String> bindingTypes = bindingTypes(e, utils);
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

    private String generatePageSource(TypeElement typeElement, AtomicBoolean error, AnnotationUtils utils) throws IOException {
        boolean hasNumble = utils.type("com.mastfrog.parameters.Types") != null;
        PackageElement pkg = findPackage(typeElement);
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg.getQualifiedName())
                .named(typeElement.getSimpleName() + GENERATED_SOURCE_SUFFIX)
                .extending(simpleName(PAGE_FQN))
                .importing(PAGE_FQN, ACTEUR_FQN,
                        "static com.mastfrog.acteur.headers.Method.*"
                )
                .conditionally(hasNumble, b -> {
                    b.importing("static com.mastfrog.parameters.Types.*",
                            "static org.netbeans.validation.api.builtin.stringvalidation.StringValidators.*");
                })
                .docComment("Generated from annotations on ", typeElement.getSimpleName(),
                        " using Java Vogon", "http://github.com/timboudreau/annotation-utils")
                .annotatedWith(GENERATED_FROM_ANNOTATION, ab -> {
                    ab.addClassArgument("value", typeElement.asType().toString());
                });
        List<String> precursorClassNames = new ArrayList<>();
        List<String> denoumentClassNames = new ArrayList<>();

        List<String> argDebugComments = new ArrayList<>();
        argDebugComments.add("JDK 15 Annotation Argument Debug Info");

        typeElement.getAnnotationMirrors().forEach(am -> {
            cb.importing(am.getAnnotationType().toString());
            if (am.getAnnotationType().toString().equals(PRECURSORS_ANNOTATION)) {
                if (!am.getElementValues().entrySet().isEmpty()) {
                    for (String s : utils.typeList(am, "value")) {
                        String valueClass = s.replace('$', '.');
                        precursorClassNames.add(valueClass);
                    }
                }
                return;
            }
            if (am.getAnnotationType().toString().equals(EARLY_ANNOTATION)) {
                denoumentClassNames.add(0, INSTALL_CHUNK_HANDLER_ACTEUR);
                return;
            }
            if (am.getAnnotationType().toString().equals(CONCLUDERS_ANNOTATION)) {
                if (!am.getElementValues().entrySet().isEmpty()) {
                    for (String s : utils.typeList(am, "value")) {
                        denoumentClassNames.add(s.replace('$', '.'));
                    }
                    return;
                }
            }
            cb.annotatedWith(am.getAnnotationType().toString(), ab -> {
                AnnotationArgumentsInfo args = arguments(am, error, utils);
                argDebugComments.add(am.getAnnotationType().toString() + ": ");
                copyAnnotation(args, argDebugComments, ab, utils, error);
            });
        });

        cb.constructor().setModifier(Modifier.PUBLIC).body(bb -> {
            // For now,
            argDebugComments.forEach(bb::lineComment);
            if (!precursorClassNames.isEmpty()) {
                bb.lineComment("precursors");
                for (String p : precursorClassNames) {
                    bb.invoke("add").withClassArgument(p).inScope();
                }
            }
            bb.lineComment("generator");
            bb.invoke("add").withClassArgument(typeElement.getQualifiedName().toString()).inScope();
            if (!denoumentClassNames.isEmpty()) {
                bb.lineComment("concluders");
                for (String p : denoumentClassNames) {
                    bb.invoke("add").withClassArgument(p).inScope();
                }
            }
        });
        if (!error.get()) {
            JavaFileObject jfo = processingEnv.getFiler().createSourceFile(cb.fqn(), typeElement);
            try (OutputStream stream = jfo.openOutputStream()) {
                stream.write(cb.build().getBytes(UTF_8));
            }
        }
        return cb.fqn();
    }

    private void copyAnnotation(AnnotationArgumentsInfo args, List<String> argDebugComments, ClassBuilder.AnnotationBuilder<?> ab, AnnotationUtils utils, AtomicBoolean error) {
        args.forEach((name, element, val) -> {
            copyOneArgument(argDebugComments, name, val, ab, element, error, utils);
        });
    }

    private void copyOneArgument(List<String> argDebugComments, String name, Object val, ClassBuilder.AnnotationBuilder<?> ab, ExecutableElement element, AtomicBoolean error, AnnotationUtils utils) {
        argDebugComments.add(" * " + name + " = " + val + " is " + (val == null ? "null" : val.getClass().getName()));
        if (val instanceof List<?>) {
            ab.addArrayArgument(name, avb -> {
                List<?> l = (List<?>) val;
                for (Object o : l) {
                    addAnnotationArgument(argDebugComments, o, element, avb::expression);
                }
            });
        } else if (val instanceof AnnotationMirror) {
            AnnotationMirror sub = (AnnotationMirror) val;
            AnnotationArgumentsInfo subArgs = arguments(sub, error, utils);
            ab.addAnnotationArgument(name, sub.getAnnotationType().toString(), abSub -> {
                copyAnnotation(subArgs, argDebugComments, abSub, utils, error);
            });
        } else if (val instanceof TypeMirror) {
            ab.addClassArgument(name, val.toString());
        } else if (val instanceof String) {
            ab.addArgument(name, (String) val);
        } else if (val instanceof Character) {
            char c = (Character) val;
            switch (c) {
                case '\\':
                    ab.addExpressionArgument(name, "'\\\\'");
                    break;
                case '\'':
                    ab.addExpressionArgument(name, "'\\''");
                    break;
                default:
                    ab.addExpressionArgument(name, "'" + c + "'s");
            }
        } else {
            addAnnotationArgument(argDebugComments, val, element, v -> {
                ab.addExpressionArgument(name, v);
            });
        }
    }

    private void addAnnotationArgument(List<String> argDebugComments, Object o, ExecutableElement element, Consumer<String> avb) {
        argDebugComments.add("    ** " + o.getClass().getName());
        argDebugComments.add("    **** " + types(o));
        argDebugComments.add(" --------- " + o + " ---------");
        if (o instanceof AnnotationValue) {
            AnnotationValue av = (AnnotationValue) o;

            TypeMirror retType = element.getReturnType();
            argDebugComments.add("RET TYPE: " + retType);
            argDebugComments.add("  RET TYPE KIND " + retType.getKind());
//            argDebugComments.add("     RTT " + processingEnv.getElementUtils().getAllTypeElements(retType.toString()));
            argDebugComments.add("     RET TYPE types " + types(retType));
            boolean isClass = false;
            String enumType = null;
            if (retType instanceof ArrayType) {
                ArrayType at = (ArrayType) retType;
                at.getComponentType();
                argDebugComments.add("    COMPONENT TYPE " + at.getComponentType());
                argDebugComments.add("       COMP TYPE kind " + at.getComponentType().getKind() + " types " + types(at.getComponentType()));

                DeclaredType dt = (DeclaredType) at.getComponentType();
                Element el = dt.asElement();
                argDebugComments.add("        ELE KIND " + el.getKind() + " types " + types(el));
                switch (el.getKind()) {
                    case ENUM:
                        enumType = dt.toString();
                        break;
                }
            } else if (retType instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) retType;
                Element el = dt.asElement();
                switch (el.getKind()) {
                    case ENUM:
                        enumType = dt.toString();
                        break;
                }
            }
            if (enumType != null) {
                // In JDK 15, enum elements do not contain the type as a prefix;
                // in earlier versions they do, and getting this wrong will result
                // in uncompilable code
                if (av.toString().indexOf('.') < 0) {
                    avb.accept(enumType + "." + av.toString());
                } else {
                    avb.accept(av.toString());
                }
            } else {
                if (isClass) {
                    avb.accept(av.toString() + ".class");
                } else {
                    avb.accept(av.toString());
                }
            }
        } else {
            argDebugComments.add("  * OTYPES " + types(o));
            if (o instanceof TypeMirror) {
                avb.accept(o + ".class");
            } else if (o instanceof String) {
                avb.accept(escapeAndQuoteString((String) o));
            } else if (o instanceof Character) {
                char c = (Character) o;
                switch (c) {
                    case '\\':
                        avb.accept("'\\\\'");
                        break;
                    case '\'':
                        avb.accept("'\\''");
                        break;
                    default:
                        avb.accept("'" + c + "'s");
                }
            } else {
                avb.accept(Objects.toString(o));
            }
        }
    }

    private String escapeAndQuoteString(String s) {
        return LinesBuilder.escape(s);
    }

    private AnnotationArgumentsInfo arguments(AnnotationMirror am, AtomicBoolean error, AnnotationUtils utils) {
        AnnotationArgumentsInfo result = new AnnotationArgumentsInfo();

        am.getElementValues().forEach((ExecutableElement ee, AnnotationValue av) -> {
            if ("<error>".equals(av.getValue() + "")) {
                error.set(true);
                utils.warn("Erroneous type " + av.getValue() + " for "
                        + ee.getSimpleName() + " in " + am
                        + ". If this is to be generated in a subsequent round of annotation processing, this may be a non-problem.");
                return;
            }
            Object val = av.getValue();
            result.add(ee.getSimpleName().toString(), ee, val);
        });
        return result;
    }

    private static final class AnnotationArgumentsInfo {

        private final Map<String, ExecutableElement> elements = new HashMap<>(8);
        private final Map<String, Object> arguments = new LinkedHashMap<>(8);

        void add(String name, ExecutableElement ex, Object arg) {
            elements.put(name, ex);
            arguments.put(name, arg);
        }

        void forEach(TriConsumer<String, ExecutableElement, Object> argConsumer) {
            arguments.forEach((name, arg) -> {
                ExecutableElement el = elements.get(name);
                assert el != null;
                argConsumer.accept(name, el, arg);
            });
        }
    }

}
