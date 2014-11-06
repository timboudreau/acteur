/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur.server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.annotations.GenericApplication;
import com.mastfrog.acteur.annotations.GenericApplicationModule;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.guicy.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.Exceptions;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Hides the complexity of initializing Guice and Settings to start a server
 * simply.
 *
 * @author Tim Boudreau
 */
public final class ServerBuilder {

    private final String namespace;
    private Class<? extends Application> appType;
    private final List<Settings> settingsList = new LinkedList<>();
    private final List<Module> modules = new LinkedList<>();
    private final List<Class<? extends Module>> moduleClasses = new LinkedList<>();
    private final Set<Class<?>> types = new HashSet<>();

    /**
     * Create a ServerBuilder with Namespace.DEFAULT as its namespace.
     * That means settings will be read from /etc/defaults.properties,
     * ~/defaults.properties and ./defaults.properties
     */
    public ServerBuilder() {
        this(Namespace.DEFAULT);
    }

    /**
     * Create a ServerBuilder with the passed namespace as its namespace.
     * That means settings will be read from /etc/$NAMESPACE.properties,
     * ~/$NAMESPACE.properties and ./$NAMESPACE.properties
     * @param namespace A namespace - must be a legal file name, no path separators
     */
    public ServerBuilder(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Set the class of the application. If not set, assumes GenericApplication
     *
     * @param type The application class
     * @return this
     */
    public ServerBuilder applicationClass(Class<? extends Application> type) {
        if (this.appType != null) {
            throw new IllegalStateException("App type was already set to " + this.appType);
        }
        this.appType = type;
        return this;
    }

    /**
     * Explicitly add a Settings which should be used for resolving &#064;Named
     * bindings, in addition to any bound to the namespace (i.e.
     * /etc/$NAMESPACE.properties)
     *
     * @param settings A settings
     * @return this
     */
    public ServerBuilder add(Settings settings) {
        this.settingsList.add(settings);
        return this;
    }

    /**
     * Add a module which should be used for Guice bindings
     *
     * @param module A module
     * @return this
     */
    public ServerBuilder add(Module module) {
        this.modules.add(module);
        return this;
    }

    /**
     * Add a module type which will be instantiated.  The constructor may be
     * a default constructor, or may take a Settings object, or a Settings and a
     * ReentrantScope.
     * @param module The module type
     * @return this
     */
    public ServerBuilder add(Class<? extends Module> module) {
        this.moduleClasses.add(module);
        return this;
    }

    /**
     * Add a type which will be bound.  Use this to add your classes to the Guice
     * injector if they will be passed between Acteurs.  The rule is:  If you 
     * accept it in an Acteur constructor and it is not part of the framework,
     * you need to pass it here.
     * @param types Some classes
     * @return this
     */
    public ServerBuilder withType(Class<?>... types) {
        this.types.addAll(Arrays.asList(types));
        return this;
    }

    /**
     * Build a Server object which can be started with its start() method (call
     * await() on the resulting ServerControl object to keep it running).
     * @return A server, not yet started
     * @throws IOException if something goes wrong
     */
    public Server build() throws IOException {
        SettingsBuilder sb = new SettingsBuilder(namespace);
        sb.addDefaultLocations();
        for (Settings s : settingsList) {
            sb.add(s);
        }
        Settings settings = sb.build();
        ScopeProvider appModule = appModule(settings);
        DependenciesBuilder db = new DependenciesBuilder().add(appModule);
        db.add(settings, namespace);
        db.add(settings, Namespace.DEFAULT);
        for (Module m : modules) {
            db.add(m);
        }
        for (Class<? extends Module> m : moduleClasses) {
            db.add(instantiateModule(m, appModule, settings));
        }
        return db.build().getInstance(Server.class);
    }
    
    private ScopeProvider appModule(Settings settings) {
        if (appType == null || GenericApplication.class.isAssignableFrom(appType)) {
            return new GS(settings, types);
        } else {
            return new TS(appType, types);
        }
    }

    private static final class TS extends ServerModule implements ScopeProvider {

        private final Set<Class<?>> toBind;

        TS(Class<? extends Application> appType, Set<Class<?>> toBind) {
            super(appType);
            this.toBind = toBind;
        }

        @Override
        protected void configure() {
            super.configure();
            Class<?>[] types = toBind.toArray(new Class<?>[toBind.size()]);
            scope.bindTypes(binder(), types);
        }

        public ReentrantScope scope() {
            return scope;
        }
    }

    private static final class GS extends GenericApplicationModule implements ScopeProvider {
        private final Set<Class<?>> toBind;

        public GS(Settings settings, Set<Class<?>> toBind) {
            super(settings);
            this.toBind = toBind;
        }
        
        @Override
        protected void configure() {
            super.configure();
            Class<?>[] types = toBind.toArray(new Class<?>[toBind.size()]);
            scope.bindTypes(binder(), types);
        }        

        public ReentrantScope scope() {
            return scope;
        }
    }

    private interface ScopeProvider extends Module {

        ReentrantScope scope();
    }

    private Module instantiateModule(Class<? extends Module> module, ScopeProvider s, Settings settings) {
        try {
            return GenericApplicationModule.instantiateModule(module, settings, s.scope());
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            return Exceptions.chuck(ex);
        }
    }
}
