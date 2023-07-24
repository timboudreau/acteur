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

import com.google.inject.Inject;
import com.mastfrog.mime.MimeType;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.DeploymentMode;
import com.mastfrog.url.Path;
import java.time.Duration;
import java.time.ZonedDateTime;

/**
 *
 * @author Tim Boudreau
 */
class DefaultExpiresPolicy implements ExpiresPolicy {

    private final Settings settings;
    private final boolean production;

    @Inject
    DefaultExpiresPolicy(Settings settings, DeploymentMode mode) {
        this.settings = settings;
        this.production = mode.isProduction();
    }

    @Override
    public ZonedDateTime get(MimeType mimeType, Path path) {
        if (!production) {
            return null;
        }
        Long expires = settings.getLong("expires." + mimeType.primaryType()+ '/' + mimeType.secondaryType().orElse(""));
        if (expires != null) {
            return ZonedDateTime.now().plus(Duration.ofMillis(expires));
        }
        if ("image".equals(mimeType.primaryType())) {
            return ZonedDateTime.now().plus(Duration.ofDays(30));
        }
        return null;
    }
}
