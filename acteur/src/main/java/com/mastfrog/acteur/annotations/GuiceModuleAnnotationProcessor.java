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
package com.mastfrog.acteur.annotations;

import com.mastfrog.util.service.AbstractSingleAnnotationLineOrientedRegistrationAnnotationProcessor;
import com.mastfrog.util.service.ServiceProvider;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(Processor.class)
@SupportedAnnotationTypes("com.mastfrog.acteur.annotations.GuiceModule")
@SupportedSourceVersion(SourceVersion.RELEASE_8)

public class GuiceModuleAnnotationProcessor extends AbstractSingleAnnotationLineOrientedRegistrationAnnotationProcessor<GuiceModule> {

    public static final String META_INF_PATH = "META-INF/http/modules.list";

    public GuiceModuleAnnotationProcessor() {
        super(GuiceModule.class, "com.google.inject.Module", "com.google.inject.AbstractModule");
    }

    @Override
    protected void doHandleOne(Element e, GuiceModule anno, int order) {
        addLine(META_INF_PATH, canonicalize(e.asType(), processingEnv.getTypeUtils()), e);
    }
}
/*
public class GuiceModuleAnnotationProcessor extends AbstractProcessor {

    public static final String META_INF_PATH = "META-INF/http/modules.list";

    private ProcessingEnvironment env;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.env = processingEnv;
    }

    private boolean isModuleSubtype(Element e) {
        Types types = env.getTypeUtils();
        Elements elements = env.getElementUtils();
        TypeElement moduleType = elements.getTypeElement("com.google.inject.Module");
        if (moduleType == null) {
            return false;
        }
        return types.isSubtype(e.asType(), moduleType.asType());
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

    public boolean checkConstructors(TypeElement el) {
        int ccount = 0;
        for (Element e : el.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR && e instanceof ExecutableElement) {
                ExecutableElement ee = (ExecutableElement) e;
                if (ee.getParameters() != null && ee.getParameters().isEmpty()) {
                    return true;
                }
            }
        }
        return ccount == 0;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        boolean result = true;
        StringBuilder lines = new StringBuilder("# " + new Date() + " compiled by " + System.getProperty("user.name"));
        try {
            Set<Element> elements = new HashSet<>();
            for (Element e : roundEnv.getElementsAnnotatedWith(GuiceModule.class)) {
                GuiceModule anno = e.getAnnotation(GuiceModule.class);
                if (anno == null) {
                    continue;
                }
                if (!isModuleSubtype(e)) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Not a subclass of Module: " + e.asType(), e);
                    continue;
                }
                if (!checkConstructors((TypeElement) e)) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Module must have a no-argument constructor", e);
                    continue;
                }
                elements.add(e);
                lines.append('\n');
                lines.append(canonicalize(e.asType(), env.getTypeUtils()));
            }
            if (lines.length() > 0) {
                if (!elements.isEmpty()) {
                    String path = GuiceModule.META_INF_PATH;
                    try {
                        env.getMessager().printMessage(Diagnostic.Kind.NOTE, "Found the following Guice module classes annotated with @GuiceModule:\n" + lines);
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
}
*/