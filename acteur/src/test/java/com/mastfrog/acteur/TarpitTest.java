package com.mastfrog.acteur;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.auth.AuthenticateBasicActeur;
import static com.mastfrog.acteur.auth.AuthenticateBasicActeur.SETTINGS_KEY_TARPIT_DELAY_RESPONSE_AFTER;
import static com.mastfrog.acteur.auth.AuthenticateBasicActeur.SETTINGS_KEY_TARPIT_DELAY_SECONDS;
import com.mastfrog.acteur.auth.Authenticator;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.acteur.util.RotatingRealmProvider;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author tim
 */
public class TarpitTest {

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings settings = new SettingsBuilder().add(SETTINGS_KEY_TARPIT_DELAY_RESPONSE_AFTER, "2").add(SETTINGS_KEY_TARPIT_DELAY_SECONDS, "2").build();
        Dependencies deps = new Dependencies(settings, new MM());
        deps.getInstance(Server.class).start().await();
    }

    @ImplicitBindings(Integer.class)
    static class App extends Application {

        App() {
            add(P.class);
        }
    }

    static class P extends Page {

        @Inject
        P(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("foo"));
            add(AuthenticateBasicActeur.class);
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

    static class MM extends ServerModule {

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
