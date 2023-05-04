/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
public class GenericApplicationModule<T extends GenericApplication> extends ServerModule<T> { // non final for unit tests that need to hide arguments

    protected final Settings settings;
    private final Class<?>[] exclude;
    public static final String EXCLUDED_CLASSES = "excluded";

    /**
     * Constructor which just takes a Settings, for use with giulius-tests
     *
     * @param settings
     */
    @SuppressWarnings("unchecked")
    public GenericApplicationModule(Settings settings) {
        this(settings, (Class<T>) GenericApplication.class, new Class<?>[0]);
    }

    /**
     * Create a new GenericApplicationModule using the passed settings and the
     * specified class exclusion list.
     *
     * @param settings
     * @param exclude A list of Page, Module or implicit binding classes which
     * should be ignored
     */
    @SuppressWarnings("unchecked")
    public GenericApplicationModule(Settings settings, Class<?>... exclude) {
        this(settings, (Class<? extends T>) GenericApplication.class, exclude);
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
    public GenericApplicationModule(Settings settings, Class<? extends T> appType, Class<?>... exclude) {
        super(appType);
        this.settings = settings;
        this.exclude = exclude;
    }

    /**
     * Create a new GenericApplicationModule with a specific subtype of
     * GenericApplication, passing in the scope to be used for request scope.
     *
     * @param scope The scope to bind types in
     * @param settings Settings
     * @param appType The application type
     * @param exclude A list of Page, Module or implicit binding classes which
     * should be ignored
     */
    public GenericApplicationModule(ReentrantScope scope, Settings settings, Class<? extends T> appType, Class<?>... exclude) {
        super(scope, appType, -1, -1, -1);
        this.settings = settings;
        this.exclude = exclude;
    }

    @SuppressWarnings("deprecation")
    public static <T extends Module> T instantiateModule(Class<T> m, Settings settings, ReentrantScope scope) throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        boolean log = settings.getBoolean("gamlog", false);
        try {
            try {
                Constructor<T> c = m.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (NoSuchMethodException e) {
                if (log) {
                    e.printStackTrace();
                }
                try {
                    Constructor<T> c = m.getDeclaredConstructor(Settings.class);
                    c.setAccessible(true);
                    return c.newInstance(settings);
                } catch (NoSuchMethodException e1) {
                    if (log) {
                        e1.printStackTrace();
                    }
                    try {
                        Constructor<T> c = m.getDeclaredConstructor(Settings.class, ReentrantScope.class);
                        c.setAccessible(true);
                        return c.newInstance(settings, scope);
                    } catch (NoSuchMethodException e2) {
                        if (log) {
                            e2.printStackTrace();
                        }
                        try {
                            Constructor<T> c = m.getDeclaredConstructor(ReentrantScope.class, Settings.class);
                            c.setAccessible(true);
                            return c.newInstance(scope, settings);
                        } catch (NoSuchMethodException e3) {
                            if (log) {
                                e3.printStackTrace();
                            }
                            Constructor<T> c = m.getDeclaredConstructor(ReentrantScope.class);
                            c.setAccessible(true);
                            return c.newInstance(scope);
                        }
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            if (log) {
                e.printStackTrace();
            }
            return m.newInstance();
        }
    }

    @Override
    protected void configure() {
        try {
            super.configure();
            HttpCallRegistryLoader ldr = new HttpCallRegistryLoader(appType);
            Set<Class<?>> toExclude = ImmutableSet.copyOf(setOf(exclude));
            bind(Class[].class).annotatedWith(Names.named(EXCLUDED_CLASSES)).toInstance(exclude);
            bind(new GenericArrayOfClasses()).annotatedWith(Names.named(EXCLUDED_CLASSES)).toInstance(exclude);
            bind(new SetOfClasses()).annotatedWith(Names.named(EXCLUDED_CLASSES)).toInstance(toExclude);
            for (Class<?> c : ldr.implicitBindings()) {
                if (!toExclude.contains(c)) {
                    scope.bindTypes(binder(), c);
                }
            }
            for (Class<? extends Module> module : ldr.modules()) {
                if (!toExclude.contains(module)) {
                    install(instantiateModule(module, settings, scope));
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
