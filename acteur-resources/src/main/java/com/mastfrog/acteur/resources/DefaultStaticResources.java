/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur.resources;

import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
class DefaultStaticResources extends MergedResources {

    DefaultStaticResources(Settings s, MimeTypes types) {
        super(find(s, types));
    }

    private static List<StaticResources> find(Settings settings, MimeTypes types) {
        List<StaticResources> result = new ArrayList<>();

        for (String name : splitAndTrim(settings.getString(RESOURCE_FOLDERS_KEY))) {
            File f = new File(name);
            if (!f.exists()) {
                throw new ConfigurationError("Resource folder does not exist for "
                        + RESOURCE_FOLDERS_KEY + " - " + f);
            }
            if (!f.isDirectory()) {
                throw new ConfigurationError("Not a folder for "
                        + RESOURCE_FOLDERS_KEY + " - " + f);
            }
            result.add(new FileResources(f, types, settings));
        }
        for (String className : splitAndTrim(settings.getString(RESOURCE_CLASSES_KEY))) {
            try {
                Class<?> type = Class.forName(className);
                String key = RESOURCE_NAMES_PREFIX + type.getName();
                List<String> names = splitAndTrim(key);
                if (names.isEmpty()) {
                    throw new ConfigurationError(className + " listed in "
                            + RESOURCE_CLASSES_KEY + " but no list of file names for " + key);
                }
            } catch (ClassNotFoundException ex) {
                throw new ConfigurationError("Class named in "
                        + RESOURCE_CLASSES_KEY + " could not be loaded: "
                        + className, ex);
            }
        }
        return result;
    }

    private static List<String> splitAndTrim(String data) {
        if (data == null || data.trim().isEmpty()) {
            return Collections.<String>emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String s : data.split(",")) {
            s = s.trim();
            if (!s.isEmpty() && !s.startsWith("#")) {
                result.add(s);
            }
        }
        return result;
    }
}
