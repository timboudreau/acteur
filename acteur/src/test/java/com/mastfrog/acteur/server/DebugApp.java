/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_ENABLED;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
public class DebugApp extends Application {

    DebugApp() {
        add(HelloPage.class);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings s = new SettingsBuilder().add(SETTINGS_KEY_CORS_ENABLED, "false").build();
        new ServerBuilder()
                .add(s)
                .applicationClass(DebugApp.class)
                .build()
                .start(8_192)
                .await();
    }

    private static final class HelloPage extends Page {

        @Inject
        @SuppressWarnings("deprecation")
        HelloPage(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("hello"));
            add(HelloActeur.class);
        }

        @Override
        public String toString() {
            return "HELLOPAGE";
        }
    }

    private static final class HelloActeur extends Acteur {

        @Inject
        HelloActeur(HttpEvent evt) {
            ok("Hello world\n");
        }
    }

}
