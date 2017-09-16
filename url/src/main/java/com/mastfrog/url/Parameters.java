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
package com.mastfrog.url;

import com.mastfrog.util.AbstractBuilder;
import org.openide.util.NbBundle;
/**
 *
 * @author Tim Boudreau
 */
public class Parameters implements URLComponent {
    private static final long serialVersionUID = 1L;
    private final String txt;
    Parameters(String s) {
        this.txt = s;
    }

    Parameters() {
        assert getClass() != Parameters.class;
        txt = null;
    }

    @Override
    public boolean isValid() {
        return URLBuilder.isEncodableInLatin1(txt);
    }

    public ParsedParameters toParsedParameters() {
        if (this instanceof ParsedParameters) {
            return (ParsedParameters) this;
        }
        if (txt == null) {
            return new ParsedParameters();
        }
        return (ParsedParameters) ParsedParameters.parse(txt);
    }

    @Override
    public String getComponentName() {
        return NbBundle.getMessage(Parameters.class, "parameters");
    }

    @Override
    public void appendTo(StringBuilder sb) {
        sb.append (txt);
    }

    public static AbstractBuilder<ParametersElement, Parameters> builder() {
        return ParsedParameters.builder();
    }

    public static Parameters parse (String params) {
        return ParsedParameters.parse(params);
    }

    public URLComponent[] getElements() {
        return new URLComponent[] { this };
    }
}
