package com.mastfrog.acteur;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.TarpitTest.MM;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.auth.Authenticator;
import com.mastfrog.acteur.header.entities.BasicCredentials;
import static com.mastfrog.acteur.headers.Headers.AUTHORIZATION;
import static com.mastfrog.acteur.headers.Headers.WWW_AUTHENTICATE;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.acteur.util.RotatingRealmProvider;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import java.io.IOException;
import static java.lang.System.currentTimeMillis;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests the "tarpit" which delays responses incrementally on repeated failed
 * login attempts.
 *
 * @author Tim Boudreau
 */
@TestWith({MM.class, HttpTestHarnessModule.class, SilentRequestLogger.class})
@SuppressWarnings("deprecation")
public class TarpitTest {

    @Test
    public void testTarpit(HttpHarness harn, Realm realm) throws Exception {
        Optional<Realm> hdr = WWW_AUTHENTICATE.get(harn.get("foo").applyingAssertions(a -> a.assertUnauthorized()
                .assertHasHeader(WWW_AUTHENTICATE)
                .assertHeaderEquals(WWW_AUTHENTICATE.toString(), "Basic realm=\"" + realm + "\"")
        ).assertAllSucceeded()
                .get().headers().firstValue(WWW_AUTHENTICATE.toString()));

        assertTrue(hdr.isPresent(), "No realm in header or no header");
        assertEquals(realm.toString(), hdr.get().toString());
        BasicCredentials creds = new BasicCredentials("foober", "woober");

        long elapsed1 = benchmark(() -> {
            harn.get("foo").setHeader(AUTHORIZATION, creds)
                    .applyingAssertions(a -> a.assertUnauthorized()).assertAllSucceeded();
        });

        System.out.println("---- Invalid auth 1: " + elapsed1 + "ms ----");

        long elapsed2 = benchmark(() -> {
            harn.get("foo").setHeader(AUTHORIZATION, creds)
                    .applyingAssertions(a -> a.assertUnauthorized()).assertAllSucceeded();
        });

        System.out.println("---- Invalid auth 2: " + elapsed2 + "ms ----");

        assertTrue(elapsed2 > 1_000, "Second response should have been delayed at least 1 second.");

        long elapsed3 = benchmark(() -> {
            harn.get("foo").setHeader(AUTHORIZATION, creds)
                    .applyingAssertions(a -> a.assertUnauthorized()).assertAllSucceeded();
        });

        System.out.println("---- Invalid auth 3: " + elapsed3 + "ms ----");
        assertTrue(elapsed3 > 2_000, "Second response should have been delayed at least 2 seconds.");
        long elapsed4 = benchmark(() -> {
            harn.get("foo").setHeader(AUTHORIZATION, creds)
                    .applyingAssertions(a -> a.assertOk()).assertAllSucceeded();
        });
        System.out.println("---- Valid auth: " + elapsed1 + "ms ----");
        assertTrue(elapsed4 < elapsed3, "Successful auth should not be delayed");
    }

    private static long benchmark(ThrowingRunnable run) throws Exception {
        long now = currentTimeMillis();
        run.run();
        return currentTimeMillis() - now;
    }

    @SuppressWarnings("deprecation")
    @com.mastfrog.acteur.ImplicitBindings(Integer.class)
    static class App extends Application {

        App() {
            add(P.class);
        }
    }

    static class P extends Page {

        @Inject
        @SuppressWarnings("deprecation")
        P(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("foo"));
            add(AuthenticationActeur.class);
            add(Stuff.class);
        }
    }

    static class Stuff extends Acteur {

        @Inject
        Stuff(Integer i, BasicCredentials creds) {
            setState(new RespondWith(200, "Hello there " + creds.username + " - you were persistent - " + i + " tries!"));
        }
    }

    @Singleton
    static class Auth implements Authenticator {

        Map<String, Integer> counts = new HashMap<>();

        @Override
        public Object[] authenticate(String realm, BasicCredentials credentials) throws IOException {
            Integer i = counts.get(credentials.username);
            if (i == null) {
                i = 1;
            } else {
                i++;
            }
            counts.put(credentials.username, i);
            if (i > 3) {
                return new Object[]{i, credentials};
            }
            return null;
        }

    }

    static class MM extends ServerModule<App> {

        MM() {
            super(App.class);
        }

        @Override
        protected void configure() {
            super.configure();
            bind(Realm.class).toProvider(RotatingRealmProvider.class);
            bind(Authenticator.class).to(Auth.class);
        }
    }
}
