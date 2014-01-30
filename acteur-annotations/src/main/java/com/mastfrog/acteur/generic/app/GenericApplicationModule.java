package com.mastfrog.acteur.generic.app;

import com.mastfrog.acteur.server.ServerModule;

/**
 *
 * @author Tim Boudreau
 */
public class GenericApplicationModule extends ServerModule {

    GenericApplicationModule() {
        super(GenericApplication.class);
    }

    GenericApplicationModule(Class<? extends GenericApplication> appType) {
        super(appType);
    }

    @Override
    protected void configure() {
        super.configure();
        HttpCallRegistryLoader ldr = new HttpCallRegistryLoader(appType);
        scope.bindTypes(binder(), ldr.implicitBindings().toArray(new Class<?>[0]));
    }

}
