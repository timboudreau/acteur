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

package com.mastfrog.acteur.resources.markup;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.mastfrog.acteur.resources.StaticResources;
import com.mastfrog.healthtracker.MarkupFiles;

/**
 * Looks up static html files to be served by acteur-resources and provides an
 * instance of {@link StaticResources} over them. Note you will need to bind
 * {@link StaticResources.Resource} in the request scope for this to work. Uses
 * the following algorithm:
 * <ul>
 * <li>Looks up the value of SETTINGS_KEY_JAR_RELATIVE_FOLDER_NAME
 * ("html.jar.relative.path") in the settings, defaulting it to
 * DEFAULT_JAR_RELATIVE_FOLDER_NAME ("html").</li>
 * <li>Searches for that folder relative to the location of the JAR file or
 * classes directory the passed class lives in, as returned by
 * <code>application.getClass().getProtectionDomain().getCodeSource().getLocation()</code>.
 * This makes it possible to run an application during development while editing
 * html files live.</li>
 * <li>If no such folder exists, look in settings for SETTINGS_KEY_HTML_PATH
 * ("html.path") which should be a path to a folder on disk where the html files
 * to serve already are.</li>
 * <li>If the key is not set or it is set but the folder does not exist, look
 * for an gzipped tar archive of HTML files (you can create one with the maven
 * assembly plugin) in the same package as the application class - by default it
 * will look for <code>html-files.tar.gz</code> in that package, or you can set
 * the name using the settings key SETTINGS_KEY_HTML_ARCHIVE_TAR_GZ_NAME
 * ("archive.tar.gz.name") - the file <em>must</em> have a <code>.tar.gz</code>
 * extension. If that is found:
 * <ul>
 * <li>Create a directory <code>html-$RANDOM_ID</code> in the system temporary
 * directory</li>
 * <li>Unpack the archive there and serve those files</li>
 * </ul>
 * If no archive is found and no place to serve files from as found an
 * IOException will be thrown (if you want to make serving files optional, don't
 * bind StaticResources or add ResourcePage to your application - do that
 * conditionally in that case).
 * </li>
 *
 * @author Tim Boudreau
 */
public class MarkupFilesModule extends AbstractModule {
    private final Class<?> relativeTo;
    
    public MarkupFilesModule(Class<?> relativeTo) {
        this.relativeTo = relativeTo;
    }

    @Override
    protected void configure() {
        bind(Class.class).annotatedWith(Names.named(MarkupFiles.GUICE_BINDING_CLASS_RELATIVE_MARKUP)).toInstance(relativeTo);
        bind(StaticResources.class).toProvider(MarkupFiles.class);
    }

}
