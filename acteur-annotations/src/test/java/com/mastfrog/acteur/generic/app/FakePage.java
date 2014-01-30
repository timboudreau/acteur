package com.mastfrog.acteur.generic.app;

import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.generic.app.FakePage.Foo.Bar;
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
