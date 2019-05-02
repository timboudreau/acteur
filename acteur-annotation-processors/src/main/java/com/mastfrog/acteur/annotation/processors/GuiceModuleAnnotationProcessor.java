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
package com.mastfrog.acteur.annotation.processors;

import com.mastfrog.annotation.registries.AbstractLineOrientedRegistrationAnnotationProcessor;
import com.mastfrog.util.service.ServiceProvider;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(Processor.class)
@SupportedAnnotationTypes("com.mastfrog.acteur.annotations.GuiceModule")
@SupportedSourceVersion(SourceVersion.RELEASE_8)

public class GuiceModuleAnnotationProcessor extends AbstractLineOrientedRegistrationAnnotationProcessor {

    public static final String META_INF_PATH = "META-INF/http/modules.list";

    public GuiceModuleAnnotationProcessor() {
        super("com.google.inject.Module", "com.google.inject.AbstractModule");
    }

    @Override
    protected void handleOne(Element e, AnnotationMirror anno, int order) {
        addLine(META_INF_PATH, utils.canonicalize(e.asType()), e);
    }
}
