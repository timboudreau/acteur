/*
 * The MIT License
 *
 * Copyright 2014 tim.
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

package com.mastfrog.acteur.sse;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Page;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author tim
 */
public class SseApp extends Application implements Runnable {

    private final Provider<EventSink> events;

    @Inject
    SseApp(Provider<EventSink> events, @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService svc) {
        this.events = events;
        add(SsePage.class);
        svc.submit(this);
    }

    @Override
    public void run() {
        int ix = 0;
        try {
            for (;;) {
                Thread.sleep(30);
                events.get().publish("hello " + ix++);
            }
        } catch (InterruptedException ex) {
            return;
        }
    }

    static class Module extends AbstractModule {

        @Override
        protected void configure() {
            install(new ServerModule<SseApp>(SseApp.class));
            bind(ErrorInterceptor.class).to(TestHarness.class);
        }
    }

    @Path("/sse")
    @Methods(GET)
    static class SsePage extends Page {

        SsePage() {
            add(SseActeur.class);
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        Dependencies deps = new Dependencies(SettingsBuilder.createDefault().build(), new SseApp.Module());
        Server server = deps.getInstance(Server.class);
        server.start().await();
    }
}
