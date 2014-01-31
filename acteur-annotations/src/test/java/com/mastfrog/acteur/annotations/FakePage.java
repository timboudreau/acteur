package com.mastfrog.acteur.annotations;

import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.annotations.FakePage.Foo.Bar;
import com.mastfrog.util.GUIDFactory;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order=1000, scopeTypes = {String.class, Integer.class, GUIDFactory.class, Bar.class})
public class FakePage extends Page {

    
    public static class Foo {
        public static class Bar {
            
        }
    }
}
