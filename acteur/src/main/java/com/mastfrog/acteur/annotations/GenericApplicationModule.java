package com.mastfrog.acteur.annotations;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Acteur ServerModule which uses GenericApplication. Page that have the
 * HttpCall annotation which are on the classpath will automatically be added,
 * and any bindings they specify will be bound in request scope. Any modules
 * with the &#064;GuiceModule annotation will be automatically installed.
 * <p/>
 * An exclusion list can be passed to
 *
 * @author Tim Boudreau
 */
public class GenericApplicationModule extends ServerModule { // non final for unit tests that need to hide arguments

    private final Settings settings;
    private final Class<?>[] exclude;
    public static final String EXCLUDED_CLASSES = "excluded";

    /**
     * Constructor which just takes a Settings, for use with giulius-tests
     *
     * @param settings
     */
    public GenericApplicationModule(Settings settings) {
        this(settings, GenericApplication.class, new Class<?>[0]);
    }

    /**
     * Create a new GenericApplicationModule using the passed settings and the
     * specified class exclusion list.
     *
     * @param settings
     * @param exclude A list of Page, Module or implicit binding classes which
     * should be ignored
     */
    public GenericApplicationModule(Settings settings, Class<?>... exclude) {
        this(settings, GenericApplication.class, exclude);
    }

    /**
     * Create a new GenericApplicationModule with a specific subtype of
     * GenericApplication.
     *
     * @param settings Settings
     * @param appType The application type
     * @param exclude A list of Page, Module or implicit binding classes which
     * should be ignored
     */
    public GenericApplicationModule(Settings settings, Class<? extends GenericApplication> appType, Class<?>... exclude) {
        super(appType);
        this.settings = settings;
        this.exclude = exclude;
    }

    private <T extends Module> T instantiateModule(Class<T> m) throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        try {
            try {
                Constructor<T> c = m.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (NoSuchMethodException e) {
                try {
                    Constructor<T> c = m.getDeclaredConstructor(Settings.class);
                    c.setAccessible(true);
                    return c.newInstance(settings);
                } catch (NoSuchMethodException e1) {
                    try {
                        Constructor<T> c = m.getDeclaredConstructor(Settings.class, ReentrantScope.class);
                        c.setAccessible(true);
                        return c.newInstance(settings, scope);
                    } catch (NoSuchMethodException e2) {
                        Constructor<T> c = m.getDeclaredConstructor(ReentrantScope.class, Settings.class);
                        c.setAccessible(true);
                        return c.newInstance(scope, settings);
                    }
                }
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
            Set<Class<?>> toExclude = ImmutableSet.copyOf(new HashSet<>(Arrays.asList(exclude)));
            bind(Class[].class).annotatedWith(Names.named(EXCLUDED_CLASSES)).toInstance(exclude);
            bind(new GenericArrayOfClasses()).annotatedWith(Names.named(EXCLUDED_CLASSES)).toInstance(exclude);
            bind(new SetOfClasses()).annotatedWith(Names.named(EXCLUDED_CLASSES)).toInstance(toExclude);
            Set<Class<?>> bindTypes = ldr.implicitBindings();
            bindTypes.removeAll(toExclude);
            scope.bindTypes(binder(), bindTypes.toArray(new Class<?>[0]));
            for (Class<? extends Module> module : ldr.modules()) {
                if (!toExclude.contains(module)) {
                    install(instantiateModule(module));
                }
            }
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Exceptions.chuck(ex);
        }
    }

    private static final class GenericArrayOfClasses extends TypeLiteral<Class<?>[]> {

    }

    private static final class SetOfClasses extends TypeLiteral<Set<Class<?>>> {
    }
}
