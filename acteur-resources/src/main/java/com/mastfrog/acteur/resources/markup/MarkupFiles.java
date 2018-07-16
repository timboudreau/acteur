package com.mastfrog.acteur.resources.markup;

import com.google.inject.name.Named;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.resources.DynamicFileResources;
import com.mastfrog.acteur.resources.ExpiresPolicy;
import com.mastfrog.acteur.resources.FileResources;
import com.mastfrog.acteur.resources.MimeTypes;
import com.mastfrog.acteur.resources.StaticResources;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.giulius.DeploymentMode;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.RandomStrings;
import io.netty.buffer.ByteBufAllocator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarInputStream;

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
@Singleton
public class MarkupFiles implements Provider<StaticResources> {

    private final StaticResources resources;
    /**
     * Settings key for an explicit path to a directory containing markup files.
     */
    public static final String SETTINGS_KEY_HTML_PATH = "html.path";
    /**
     * Settings key for the path relative to location of the project or jar file
     * shich, if it exists, should be used for markup.
     */
    public static final String SETTINGS_KEY_JAR_RELATIVE_FOLDER_NAME = "html.jar.relative.path";
    public static final String DEFAULT_JAR_RELATIVE_FOLDER_NAME = "html";
    /**
     * Name of an archive of markup files which should be used if no folder can
     * be found relative to the project.
     */
    public static final String SETTINGS_KEY_HTML_ARCHIVE_TAR_GZ_NAME = "archive.tar.gz.name";
    public static final String DEFAULT_HTML_ARCHIVE_TAR_GZ_NAME = "html-files";
    /**
     * Name for the &#064;Named binding of a <code>Class</code> object which
     * should be used for locating the html directory and the markup archive.
     */
    public static final String GUICE_BINDING_CLASS_RELATIVE_MARKUP = "markupRelativeTo";

    /**
     * If set to true, use DynamicFileResources instead of FileResources for
     * serving markup. The difference is that with DynamicFileResources, files
     * are not copied into JVM memory for serving - slightly slower performance,
     * but able to serve files that did not exist on VM startup.
     */
    public static final String SETTINGS_KEY_USE_DYN_FILE_RESOURCES = "markup.files.dynamic";
    public static final boolean DEFAULT_USE_DYN_FILE_RESOURCES = true;

    @Inject
    @SuppressWarnings("unchecked")
    public MarkupFiles(@Named(GUICE_BINDING_CLASS_RELATIVE_MARKUP) Class type, Settings settings, MimeTypes types, DeploymentMode mode, ByteBufAllocator allocator, ExpiresPolicy policy, ShutdownHookRegistry onShutdown, ApplicationControl ctrl, Provider<Closables> clos) throws Exception {
        String jarRelativeFolderName = settings.getString(SETTINGS_KEY_JAR_RELATIVE_FOLDER_NAME, DEFAULT_JAR_RELATIVE_FOLDER_NAME);
        // Find where we're running from and try to look up ../html
        File file = findFolderRelativeToJAR(type, jarRelativeFolderName);
        if (file != null && Boolean.getBoolean("no.relative.files")) {
            // for testing
            file = null;
        }
        // If that isn't there, see if there is a setting html.path that
        // points to it
        if (file == null) {
            String markupPath = settings.getString(SETTINGS_KEY_HTML_PATH);
            if (markupPath != null) {
                File f = new File(markupPath);
                if (f.exists() && f.isDirectory()) {
                    file = f;
                }
            }
        }
        System.out.println("USING FILE " + file);
        boolean dynResources = file != null || settings.getBoolean(SETTINGS_KEY_USE_DYN_FILE_RESOURCES, DEFAULT_USE_DYN_FILE_RESOURCES);
        // If that fails, unpack the embedded archive of html into a unique
        // subdir of /tmp and serve from there
        if (file == null) {
            String archiveName = settings.getString(SETTINGS_KEY_HTML_ARCHIVE_TAR_GZ_NAME, DEFAULT_HTML_ARCHIVE_TAR_GZ_NAME);
            file = unpackMarkupArchive(type, archiveName, onShutdown, ctrl);
        }
        if (dynResources) {
            resources = new DynamicFileResources(file, types, policy, ctrl, allocator, settings, clos);
        } else {
            resources = new FileResources(file, types, mode, allocator, settings, policy);
        }
    }

    public final File findFolderRelativeToJAR(Class<?> jarClass, String relPath) throws URISyntaxException, IOException {
        // Get the location this JAR is in on disk, to set up paths relative to it
        ProtectionDomain protectionDomain = jarClass.getProtectionDomain();
        URL location = protectionDomain.getCodeSource().getLocation();

        // See if an explicitly provided assets folder was passed to us;  if
        // not we will look for an "assets" folder belonging to this application
        File codebaseDir = new File(location.toURI()).getParentFile();
        if ("target".equals(codebaseDir.getName()) || "build".equals(codebaseDir.getName())) {
            codebaseDir = codebaseDir.getParentFile();
        }
        File f = new File(codebaseDir, relPath);
        File result = f.exists() && f.isDirectory() ? f : null;
        if (result == null) {
            result = new File(codebaseDir, "src" + File.separator + "main" + File.separator + relPath);
            if (!result.exists() || !result.isDirectory()) {
                result = null;
            }
        }
        if (Boolean.getBoolean("acteur.debug")) {
            if (result != null) {
                System.err.println("Using markup files in " + result.getAbsolutePath());
            } else {
                System.err.println("No markup files folder found - unpacking embedded markup tarball");
            }
        }
        return result;
    }

    private File unpackMarkupArchive(Class<?> relativeTo, String archiveName, ShutdownHookRegistry onShutdown, ApplicationControl ctrl) throws IOException, FileNotFoundException {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        String uniq = new RandomStrings().get(7) + "-" + Long.toString(System.currentTimeMillis(), 36);
        if (!archiveName.endsWith(".tar.gz")) {
            archiveName += ".tar.gz";
        }
        File destDir = new File(tmp, "html-" + uniq);
        if (Boolean.getBoolean("acteur.debug")) {
            System.err.println("Decompressing markup archive to " + destDir);
        }
        try (InputStream in = relativeTo.getResourceAsStream(archiveName)) {
            if (in == null) {
                throw new IOException("Markup files missing from archive: " + archiveName + " in " + relativeTo.getPackage().getName().replace('.', '/'));
            }
            if (!destDir.mkdirs()) {
                throw new IOException("Could not create " + destDir.getAbsolutePath());
            }
            List<File> untarred = unTar(in, destDir);
            if (!untarred.isEmpty()) {
                try {
                    onShutdown.add(new MarkupDeleter(destDir, untarred));
                } catch (Exception e) {
                    ctrl.internalOnError(e);
                }
            }
        }
        return destDir;
    }

    @Override
    public StaticResources get() {
        return resources;
    }

    private List<File> unTar(InputStream raw, final File outputDir) throws FileNotFoundException, IOException {
        final List<File> untarredFiles = new LinkedList<>();
        GZIPInputStream is = new GZIPInputStream(raw);
        try (TarInputStream debInputStream = new TarInputStream(is)) {
            TarEntry entry;
            while ((entry = (TarEntry) debInputStream.getNextEntry()) != null) {
                final File outputFile = new File(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!outputFile.exists()) {
                        if (!outputFile.mkdirs()) {
                            throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
                        }
                    }
                } else {
                    try (OutputStream outputFileStream = new FileOutputStream(outputFile)) {
                        Streams.copy(debInputStream, outputFileStream);
                    }
                    outputFile.setLastModified(entry.getModTime().getTime());
                }
                untarredFiles.add(outputFile);
            }
        }
        return untarredFiles;
    }

    static class MarkupDeleter implements Runnable {

        private final File markupDir;
        private final Set<File> files;

        public MarkupDeleter(File markupDir, List<File> files) {
            this.markupDir = markupDir;
            this.files = new HashSet<>(files);
        }

        @Override
        public void run() {
            if (Boolean.getBoolean("acteur.debug")) {
                System.err.println("Deleting unarchived markup in " + markupDir);
            }
            delete(markupDir);
        }

        private void delete(File file) {
            if (file.isFile()) {
                if (files.contains(file)) {
                    if (!file.delete()) {
                        if (Boolean.getBoolean("acteur.debug")) {
                            System.err.println("Could not delete " + file);
                        }
                    }
                }
            } else if (file.isDirectory()) {
                for (File f : file.listFiles()) {
                    delete(f);
                }
                file.delete();
            }
        }
    }
}
