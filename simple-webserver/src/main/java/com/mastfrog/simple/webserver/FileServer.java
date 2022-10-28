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
package com.mastfrog.simple.webserver;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Help;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.resources.DynamicFileResources;
import com.mastfrog.acteur.resources.Resource;
import com.mastfrog.acteur.resources.ResourcesPage;
import com.mastfrog.acteur.resources.StaticResources;
import static com.mastfrog.acteur.resources.StaticResources.RESOURCE_FOLDERS_KEY;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import static com.mastfrog.acteur.server.ServerModule.PORT;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.SettingsBindings;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.graal.annotation.Expose;
import com.mastfrog.graal.annotation.ExposeMany;
import com.mastfrog.mime.MimeType;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.time.TimeUtil;
import static com.mastfrog.util.time.TimeUtil.GMT;
import io.netty.handler.codec.http.HttpHeaderNames;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;

/**
 * A simple acteur-based file server.
 *
 * @author Tim Boudreau
 */
public final class FileServer extends AbstractModule {

    // Note, this would be simpler if we used @HttpCall and generated
    // our pages here, but some tests want to start multiple independent
    // servers to proxy, so we need to run things in isolation.
    private final File dir;
    private final ReentrantScope scope = new ReentrantScope();

    /**
     * Create a new file server.
     *
     * @param dir The root directory to serve
     */
    @ExposeMany({
        @Expose(type = "io.netty.channel.socket.nio.NioServerSocketChannel",
                methods = @Expose.MethodInfo(name = "<init>", parameterTypes = {})),})
    public FileServer(File dir) {
        this.dir = dir;
    }

    /**
     * Start a file server serving the current directory.
     *
     * @param args The arguments, which will be parsed into settings
     * @throws IOException If something goes wrong
     * @throws InterruptedException If something goes wrong
     */
    public static void main(String... args) throws IOException, InterruptedException {
        start(args).get().await();
    }

    /**
     * Start a file server.
     *
     * @param path A directory
     * @return A file server
     * @throws IOException
     */
    public static ServerControl start(java.nio.file.Path path) throws IOException {
        if (!Files.exists(notNull("path", path)) || !Files.isDirectory(path)) {
            throw new IOException("Does not exist or is not directory: " + path);
        }
        return start(RESOURCE_FOLDERS_KEY, path.toString()).get();
    }

    /**
     * Start a file server.
     *
     * @param args command line arguments to parse into settings
     * @return A file server if the folder exists
     * @throws IOException if something goes wrong
     */
    public static Optional<ServerControl> start(String... args) throws IOException {
        Settings settings = new SettingsBuilder(Namespace.DEFAULT)
                .add(RESOURCE_FOLDERS_KEY, ".")
                .add(PORT, 8080)
                .add(ServerModule.HTTP_COMPRESSION, true)
                .add("application.name", "Simple Web Server")
                .add("neverKeepAlive", "true")
                .add(ServerModule.EVENT_THREADS, 4)
                .add(ServerModule.WORKER_THREADS, 12)
                .defineShortcuts()
                .map('p').to(PORT)
                .map('s').to("ssl")
                .build()
                .parseCommandLineArguments(RESOURCE_FOLDERS_KEY, args)
                .build();
        // Resolve the folder
        File f = new File(settings.getString(RESOURCE_FOLDERS_KEY)).getCanonicalFile();
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("Folder to serve does not exist: " + f.getAbsolutePath());
            return Optional.empty();
        } else {
            System.err.println("Serving files on port " + settings.getInt(PORT) + " from " + f.getAbsolutePath());
        }
        return Optional.of(start(settings, f));
    }

    /**
     * Start a file server with the passed settings over the passed directory.
     * 
     * @param settings A settings
     * @param f A file
     * @return A ServerControl
     * @throws IOException if something goes wrong
     */
    public static ServerControl start(Settings settings, File f) throws IOException {
        FileServer server = new FileServer(f);
        return server.startServer(settings);
    }

    public ServerControl startServer(Settings settings) throws IOException {
        // Create the server and block the main thread until it exits
        Dependencies deps = Dependencies.builder()
                .mergeNamespaces()
                .add(settings)
                .add(this)
                .add(new ServerModule<>(scope, App.class))
                //                .add(new GenericApplicationModule(settings))
                .enableOnlyBindingsFor(SettingsBindings.INT, SettingsBindings.BOOLEAN, SettingsBindings.LONG, SettingsBindings.STRING)
                .build();

        return deps.getInstance(com.mastfrog.acteur.util.Server.class)
                .start(settings.getInt(PORT), settings.getBoolean("ssl", false));
    }

    @Override
    protected void configure() {
        // Bind File.class to the folder we want to serve, from command line args / settings
        bind(File.class).toInstance(dir);
        // DynamicFileResources is a simple implementation of StaticResources that doesn't
        // cache data in memory or do anything exciting (others do).
        bind(StaticResources.class).to(DynamicFileResources.class).asEagerSingleton();
        scope.bindTypes(binder(), Resource.class);
    }

    @Help
    static class App extends Application {

        public App() {
            add(DirPage.class);
            add(RootPage.class);
            add(RP.class);
        }

    }

    // This serves static markup files it finds
    // There are several implementations of StaticResources in acteur-resources
    // We need to include this in the application ourselves, because acteur-resources
    // should not alter the set of URLs the server responds to just because it is on
    // the classpath, so it does not register it
//    @HttpCall(scopeTypes = Resource.class)
    @Methods({Method.GET, Method.HEAD})
    static class RP extends ResourcesPage {

        @Inject
        public RP(ActeurFactory af, StaticResources r, Settings settings) {
            super(af, r, settings);
        }
    }

    @Methods({Method.GET, Method.HEAD})
    static class DirPage extends Page {

        DirPage() {
            add(DirActeur.class);
        }
    }

    // Serves folder listings
//    @HttpCall(order = -1000)
//    @Methods({Method.GET, Method.HEAD})
    static final class DirActeur extends Acteur {

        @Inject
        DirActeur(File root, HttpEvent evt, PathFactory paths) {
            File dir = evt.path().toString().isEmpty() ? root : new File(root, evt.path().toString());
            if (dir.exists() && dir.isDirectory()) {
                add(Headers.CONTENT_TYPE, MimeType.HTML_UTF_8);
                StringBuilder result = new StringBuilder(512).append("<!doctype html><html><head><title>").append(dir.getName()).append("</title>")
                        .append("<style>body { font-family:'Helvetica'; margin: 2em;}</style>")
                        .append("</head><body>");
                result.append("<h1>").append(dir.getName()).append("</h1>\n");
                result.append("<p>").append(dir.getAbsolutePath()).append("</p>\n");
                result.append("<table>");
                String base = evt.path().toString().isEmpty() ? "/" : evt.path().toStringWithLeadingSlash() + "/";
                long lm = 0;
                long etagBase = 0;
                File[] files = dir.listFiles();
                Arrays.sort(files, (a, b) -> {
                    String as = a.getName();
                    String bs = b.getName();
                    if (as.charAt(0) == '.') {
                        as = as.substring(1);
                    }
                    if (bs.charAt(0) == '.') {
                        bs = bs.substring(1);
                    }
                    return as.compareToIgnoreCase(bs);
                });
                for (File f : files) {
                    String pth = base + f.getName();
                    long lastModified = f.lastModified();
                    long length = f.length();
                    result.append("\t<tr><td><a href='").append(pth).append("'>").append(f.getName()).append("</a></td><td style='text-align: right'>\n")
                            .append(length).append("</td><td style='padding-left: 3em;'>").append(Headers.ISO2822DateFormat.format(TimeUtil.fromUnixTimestamp(lastModified)))
                            .append("</td></tr>\n");
                    lm = Math.max(lm, lastModified);
                    etagBase += lastModified + length;
                }
                add(Headers.LAST_MODIFIED, TimeUtil.fromUnixTimestamp(lm).withZoneSameInstant(GMT));
                add(Headers.CACHE_CONTROL, CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY);
                String etag = Long.toString(etagBase, 36);
                add(Headers.ETAG, etag);
                String inm = evt.header(HttpHeaderNames.IF_NONE_MATCH.toString());
                if (inm != null && etag.equals(inm) || ('"' + etag + '"').equals(inm)) {
                    reply(NOT_MODIFIED);
                    return;
                }
                result.append("</body></html>\n");
                ok(result.toString());
            } else {
                reject();
            }
        }
    }

    @Methods(Method.GET)
    @Path("/")
    static class RootPage extends Page {

        RootPage() {
            add(RootActeur.class);
            add(DirActeur.class);
        }
    }

    // Redirects / to something reasonable
//    @HttpCall()
//    @Methods(Method.GET)
//    @Path("/")
//    @Concluders(DirActeur.class)
    static class RootActeur extends Acteur {

        @Inject
        RootActeur(File dir) throws URISyntaxException {
            File file = null;
            for (File f : dir.listFiles()) {
                if (f.getName().startsWith("index")
                        || f.getName().startsWith("home")
                        || f.getName().startsWith("default")) {
                    file = f;
                    break;
                }
            }
            if (file == null) {
                next();
                return;
            }
            add(Headers.LOCATION, new URI("/" + file.getName()));
            reply(FOUND);
        }
    }

}
