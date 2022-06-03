package com.mastfrog.acteur.annos;

import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.annos.FakePage.Foo.Bar;
import com.mastfrog.util.strings.RandomStrings;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order=1000, scopeTypes = {String.class, Integer.class, RandomStrings.class, Bar.class})
public class FakePage extends Page {

    
    public static class Foo {
        public static class Bar {
            
        }
    }
}
