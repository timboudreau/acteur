package com.mastfrog.acteur;

import com.google.inject.name.Names;
import com.mastfrog.acteur.AppTest.M;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.ThreadFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

@TestWith({M.class})
public class PathFactoryTest {

    static class M extends ServerModule<A> {

        M() {
            super(A.class);
        }

        @Override
        protected void configure() {
            super.configure();
            bind(ThreadFactory.class).annotatedWith(Names.named(ServerModule.WORKER_THREADS)).toInstance(new ThreadFactory() {

                @Override
                public Thread newThread(Runnable r) {
                    throw new UnsupportedOperationException("Not supported yet.");
                }
            });
        }
    }

    static class A extends Application {
    }

    @Test
    public void test(PathFactory dpf, Settings settings) throws UnsupportedEncodingException {
        assertEquals("example.com", settings.getString("hostname"));
        assertEquals("foo/bar", settings.getString("basepath"));

        assertEquals("DefaultPathFactory", dpf.getClass().getSimpleName());
        URL url = dpf.constructURL(Path.parse(""), false);
        assertEquals("http://example.com:8080/foo/bar", url.toString());

        Path path = dpf.toPath("foo/bar/baz/moo.txt");
        assertEquals("baz/moo.txt", path.toString());

        path = dpf.toPath("/foo/bar/baz/moo.txt");
        assertEquals("baz/moo.txt", path.toString());

        path = dpf.toExternalPath("moo.txt");
        assertEquals("foo/bar/moo.txt", path.toString());

        path = Path.parse("loading-small.gif", true);
        String s = URLEncoder.encode("loading-small.gif", "UTF-8");
        path = dpf.toPath("loading%2dsmall.gif");
        assertEquals("loading-small.gif", path.toString());
    }
}
