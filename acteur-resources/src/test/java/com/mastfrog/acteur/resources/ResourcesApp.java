package com.mastfrog.acteur.resources;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.ImplicitBindings;
import com.mastfrog.acteur.resources.StaticResources.Resource;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.GUIDFactory;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author tim
 */
@ImplicitBindings(Resource.class)
public class ResourcesApp extends Application {

    ResourcesApp() {
        add(ResourcesPage.class);
    }

    static File tmpdir;
    static String[] FILES = new String[]{
        "hello.txt", "another.txt"
    };
    static String STUFF = GUIDFactory.get().newGUID(18, 10);

    static {
        try {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            tmpdir = new File(tmp, "resources-" + Long.toString(System.currentTimeMillis(), 36));
            assertTrue(tmpdir.mkdirs());
        } catch (Exception ex) {
            Exceptions.chuck(ex);
        }
    }
    
    static class DynFileResourcesModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(DynamicFileResources.class);
//            bind(HttpClient.class).toInstance(HttpClient.builder().noCompression().build());
            bind(HttpClient.class).toInstance(HttpClient.builder().build());
        }
    }

    static class FileResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(FileResources.class);
//            bind(HttpClient.class).toInstance(HttpClient.builder().noCompression().build());
            bind(HttpClient.class).toInstance(HttpClient.builder().build());
        }
    }
    static class FileResourcesModule2 extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(FileResources.class);
            bind(HttpClient.class).toInstance(HttpClient.builder().build());
        }
    }
    static class ClasspathResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<>(ResourcesApp.class));
            bind(StaticResources.class).to(ClasspathResources.class);
            bind(ClasspathResourceInfo.class).toInstance(new ClasspathResourceInfo(ResourcesApp.class,
                    "hello.txt", "another.txt"));
            bind(HttpClient.class).toInstance(HttpClient.builder().build());
        }
    }

    static class MergedResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<ResourcesApp>(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(MergedResources.class);
            bind(ClasspathResourceInfo.class).toInstance(new ClasspathResourceInfo(ResourcesApp.class,
                    "hello.txt", "another.txt"));
            bind(new TL()).toProvider(P.class);
            bind(HttpClient.class).toInstance(HttpClient.builder().build());
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
