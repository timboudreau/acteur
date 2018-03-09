package com.mastfrog.acteur.annotations;

import com.mastfrog.util.service.ServiceProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(Processor.class)
@SupportedAnnotationTypes("com.mastfrog.acteur.annotations.GuiceModule")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
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
