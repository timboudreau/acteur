package com.mastfrog.acteur.annotations;

import com.mastfrog.acteur.Acteur;
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
@HttpCall(order=30000)
@PathRegex ("^foo\\/bar$")
@Methods({GET, Method.DELETE})
@Precursors({Fooble.class, Barble.class})
public class X extends Acteur {

    static class Fooble extends Acteur {
        
    }
    
    static class Barble extends Acteur {
        
    }
}
