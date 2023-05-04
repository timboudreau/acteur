package com.mastfrog.acteur.annotations;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.annotations.FakePage.Foo;
import com.mastfrog.acteur.annotations.FakePage.Foo.Bar;
import com.mastfrog.acteur.annotations.X.Barble;
import com.mastfrog.acteur.annotations.X.Fooble;
import com.mastfrog.acteur.annotations.X.Wurg;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Example;
import com.mastfrog.acteur.preconditions.Examples;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent;
import com.mastfrog.acteur.preconditions.PathRegex;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order = 30_000, scopeTypes = {Foo.class, Bar.class, String.class})
@PathRegex("^foo\\/bar$")
@Methods({GET, Method.DELETE})
@Precursors({Fooble.class, Barble.class})
@Concluders(Wurg.class)
@ParametersMustBeNumbersIfPresent({"blee", "blah", "wug"})
@Description("This is X")
@com.mastfrog.acteur.preconditions.AuthenticatedIf(setting = "authit", value = "true")
@com.mastfrog.acteur.preconditions.BannedUrlParameters({"glink", "glorp"})
@com.mastfrog.acteur.preconditions.MaximumPathLength(32)
@com.mastfrog.acteur.preconditions.MaximumRequestBodyLength(0)
@com.mastfrog.acteur.preconditions.MinimumRequestBodyLength(0)
@com.mastfrog.acteur.preconditions.RequireAtLeastOneUrlParameterFrom({"wug", "whee"})
@Examples({
    @Examples.Case(title = "Do things", description = "Doing some things", value = @Example(inputField = "STUFF", inputType = X.class))
})
@Example(inputField = "STUFF", inputType = X.class)
public class X extends Acteur {

    public static final String STUFF = "I'm a wookie";
    public static final String MORE_STUFF = "You are not a wookie";

    X() {
        next("Hey");
    }

    @Description("Does foobley things")
    static class Fooble extends Acteur {

        Fooble() {
            next(new Foo());
        }
    }

    @Description("Does barbley things")
    static class Barble extends Acteur {

        Barble() {
            next(new Bar());
        }
    }

    @Description("Does wurgy things")
    static class Wurg extends Acteur {

        @Inject
        Wurg(Foo foo, Bar bar) {
            ok("Hello " + foo + " " + bar);
        }
    }

    public static class Foo {

        public String toString() {
            return "foo";
        }
    }

    public static class Bar {

        public String toString() {
            return "bar";
        }
    }
}
