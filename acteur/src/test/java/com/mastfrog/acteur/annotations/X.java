package com.mastfrog.acteur.annotations;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.annotations.FakePage.Foo;
import com.mastfrog.acteur.annotations.FakePage.Foo.Bar;
import com.mastfrog.acteur.annotations.X.Barble;
import com.mastfrog.acteur.annotations.X.Fooble;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PathRegex;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order = 30_000, scopeTypes = {Foo.class, Bar.class})
@PathRegex("^foo\\/bar$")
@Methods({GET, Method.DELETE})
@Precursors({Fooble.class, Barble.class})
public class X extends Acteur {

    static class Fooble extends Acteur {

        Fooble() {
            next(new Foo());
        }
    }

    static class Barble extends Acteur {

    }

    public static class Foo {
        
    }
    public static class Bar {

    }
}
