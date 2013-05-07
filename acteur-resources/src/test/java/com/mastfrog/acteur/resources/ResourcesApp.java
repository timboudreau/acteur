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
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.GUIDFactory;
import com.mastfrog.util.Streams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertNotNull;
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
    static String[] files = new String[]{
        "hello.txt", "another.txt"
    };
    static String stuff = GUIDFactory.get().newGUID(18, 10);

    static {
        try {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            tmpdir = new File(tmp, "resources-" + Long.toString(System.currentTimeMillis(), 36));
            System.out.println("RESOURCES IN " + tmpdir);
            assertTrue(tmpdir.mkdirs());
            for (String file : files) {
                File f = new File(tmpdir, file);
                assertTrue(f.createNewFile());
                try (InputStream in = ResourcesApp.class.getResourceAsStream(file)) {
                    assertNotNull(file, in);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        Streams.copy(in, out, 128);
                    }
                }
            }
            File sub = new File(tmpdir, "sub");
            assertTrue(sub.mkdirs());
            File subfile = new File(sub, "subfile.txt");
            assertTrue(subfile.createNewFile());
            Streams.writeString(stuff, subfile);
        } catch (IOException ex) {
            Exceptions.chuck(ex);
        }
    }

    static class FileResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(FileResources.class);
        }
    }

    static class ClasspathResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule(ResourcesApp.class));
            bind(StaticResources.class).to(ClasspathResources.class);
            bind(ClasspathResourceInfo.class).toInstance(new ClasspathResourceInfo(ResourcesApp.class,
                    "hello.txt", "another.txt"));
        }
    }

    static class MergedResourcesModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule(ResourcesApp.class));
            bind(File.class).toInstance(tmpdir);
            bind(StaticResources.class).to(MergedResources.class);
            bind(ClasspathResourceInfo.class).toInstance(new ClasspathResourceInfo(ResourcesApp.class,
                    "hello.txt", "another.txt"));
            bind(new TL()).toProvider(P.class);
        }

        static class TL extends TypeLiteral<List<StaticResources>> {
        }

        @Singleton
        static class P implements Provider<List<StaticResources>> {

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
