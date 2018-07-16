/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur.resources;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.RequestLogger;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.RandomStrings;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author tim
 */
@SuppressWarnings("deprecation")
@com.mastfrog.acteur.ImplicitBindings(Resource.class)
public class ResourcesApp extends Application {

    ResourcesApp() {
        add(ResourcesPage.class);
    }

    static File tmpdir;
    static String[] FILES = new String[]{
        "hello.txt", "another.txt"
    };
    static String STUFF = new RandomStrings().get(1800);

    static {
        try {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            tmpdir = new File(tmp, "resources-" + Long.toString(System.currentTimeMillis(), 36));
            assertTrue(tmpdir.mkdirs());
        } catch (Exception ex) {
            Exceptions.chuck(ex);
        }
    }

    static final class SilentRequestLogger implements RequestLogger {

        @Override
        public void onBeforeEvent(RequestID rid, Event<?> event) {
            // do nothing
        }

        @Override
        public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
            // do nothing
        }

    }
    
    static class Compression extends AbstractModule {

        @Override
        protected void configure() {
            bind(HttpClient.class).toInstance(HttpClient.builder().useCompression().build());
            bind(RequestLogger.class).to(SilentRequestLogger.class);
        }
    }

    static class NoCompression extends AbstractModule {

        @Override
        protected void configure() {
            bind(HttpClient.class).toInstance(HttpClient.builder().noCompression().build());
            bind(RequestLogger.class).to(SilentRequestLogger.class);
        }
        
    }

    
    static class DynFileResourcesModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(DynamicFileResources.class);
            bind(RequestLogger.class).to(SilentRequestLogger.class);
        }
    }

    static class FileResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(FileResources.class);
            bind(RequestLogger.class).to(SilentRequestLogger.class);
        }
    }
    static class FileResourcesModule2 extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(FileResources.class);
            bind(RequestLogger.class).to(SilentRequestLogger.class);
        }
    }
    static class ClasspathResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(StaticResources.class).to(ClasspathResources.class);
            bind(ClasspathResourceInfo.class).toInstance(new ClasspathResourceInfo(ResourcesApp.class,
                    "hello.txt", "another.txt"));
            bind(RequestLogger.class).to(SilentRequestLogger.class);
        }
    }

    static class MergedResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(MergedResources.class);
            bind(ClasspathResourceInfo.class).toInstance(new ClasspathResourceInfo(ResourcesApp.class,
                    "hello.txt", "another.txt"));
            bind(new TL()).toProvider(P.class);
            bind(RequestLogger.class).to(SilentRequestLogger.class);
        }

        static final class TL extends TypeLiteral<List<StaticResources>> {
        }

        @Singleton
        static final class P implements Provider<List<StaticResources>> {

            private final FileResources fr;
            private final ClasspathResources cr;

            @Inject
            public P(FileResources fr, ClasspathResources cr) {
                this.fr = fr;
                this.cr = cr;
            }

            @Override
            public List<StaticResources> get() {
                return Arrays.asList(cr, fr);
            }
        }
    }
}
