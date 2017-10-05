/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.NumblePage.PortValidator;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.parameters.Param;
import com.mastfrog.parameters.Params;
import com.mastfrog.parameters.Types;
import java.io.IOException;
import org.netbeans.validation.api.AbstractValidator;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.builtin.stringvalidation.StringValidators;

/**
 *
 * @author Tim Boudreau
 */
@Path("/numble")
@Methods(PUT)
@HttpCall(order = Integer.MIN_VALUE)
@InjectRequestBodyAs(NumblePageParams.class)
@Params(value = {
    @Param(value = "host", constraints = {StringValidators.NO_WHITESPACE, StringValidators.HOST_NAME}, defaultValue = "timboudreau.com", required = false
    ),
    @Param(value = "port", validators = PortValidator.class, type = Types.NON_NEGATIVE_INTEGER
    ),
    @Param(value = "bool", type = Types.BOOLEAN, defaultValue = "true")
},
        generateToJSON = true
)
public class NumblePage extends Acteur {

    @Inject
    NumblePage(ReentrantScope scope, HttpEvent evt, Provider<NumblePageParams> params) throws JsonProcessingException, IOException {
        ok(params.get().toJSON());
    }

    static final class PortValidator extends AbstractValidator<String> {

        PortValidator() {
            super(String.class);
        }

        @Override
        public void validate(Problems problems, String compName, String model) {
            if (model != null) {
                try {
                    int val = Integer.parseInt(model);
                    if (val <= 0) {
                        problems.append("Port must be 1 or greater");
                    } else if (val > 65535) {
                        problems.append("Port must be less than 65536");
                    }
                } catch (NumberFormatException e) {
                    problems.append("Not a number: " + model);
                }
            }
        }

    }
}
