package com.mastfrog.acteur.annotations;

import com.google.inject.Module;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 *
 * @author Tim Boudreau
 */
public final class GenericApplicationModule extends ServerModule {
    private final Settings settings;

    public GenericApplicationModule(Settings settings) {
        this(settings, GenericApplication.class);
    }

    public GenericApplicationModule(Settings settings, Class<? extends GenericApplication> appType) {
        super(appType);
        this.settings = settings;
    }

    private <T extends Module> T instantiateModule(Class<T> m) throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        try {
            try {
                Constructor<T> c = m.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (NoSuchMethodException e) {
                Constructor<T> c = m.getDeclaredConstructor(Settings.class);
                c.setAccessible(true);
                return c.newInstance(settings);
            }
        } catch (NoSuchMethodException e) {
            return m.newInstance();
        }
    }

    @Override
    protected void configure() {
        try {
            super.configure();
            HttpCallRegistryLoader ldr = new HttpCallRegistryLoader(appType);
            scope.bindTypes(binder(), ldr.implicitBindings().toArray(new Class<?>[0]));
            for (Class<? extends Module> module : ldr.modules()) {
                install(instantiateModule(module));
            }
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Exceptions.chuck(ex);
        }
    }
}
