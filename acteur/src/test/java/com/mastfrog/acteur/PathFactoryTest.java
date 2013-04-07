package com.mastfrog.acteur;
import com.mastfrog.acteur.Application;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.settings.Settings;
import com.mastfrog.giulius.tests.GuiceTest;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.acteur.AppTest.M;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import java.io.UnsupportedEncodingException;
import static org.junit.Assert.*;
import org.junit.Test;

@TestWith({M.class})
@Defaults({"hostname=example.com", "basepath=foo/bar", "port=8080"})
public class PathFactoryTest extends GuiceTest {
    
    static class M extends ServerModule<A> {

        M() {
            super(A.class);
        }
        
    }
    
    static class A extends Application {
        
    }
    
    @Test
    public void test(PathFactory dpf, Settings settings) throws UnsupportedEncodingException {
        assertEquals ("example.com", settings.getString("hostname"));
        assertEquals ("foo/bar", settings.getString("basepath"));
        
        assertEquals ("DefaultPathFactory", dpf.getClass().getSimpleName());
        URL url = dpf.constructURL(Path.parse(""), false);
//        assertEquals ("http://example.com:8080/foo/bar", url.toString());
        
        Path path = dpf.toPath("foo/bar/baz/moo.txt");
        assertEquals ("baz/moo.txt", path.toString());

        path = dpf.toPath("/foo/bar/baz/moo.txt");
        assertEquals ("baz/moo.txt", path.toString());
        
        path = dpf.toExternalPath("moo.txt");
        assertEquals ("foo/bar/moo.txt", path.toString());
        
//        path = Path.parse("loading-small.gif");
//        System.err.println("Parses as " + path);
//        
//        System.err.println("DECODES AS " + URLDecoder.decode(path.toString(), "UTF-8"));
//        
//        String s = URLEncoder.encode("loading-small.gif", "UTF-8");
//        System.err.println("S is " + s);
//        path = dpf.toPath("loading%2dsmall.gif");
//        assertEquals("loading-small.gif", path.toString());
    }
}
