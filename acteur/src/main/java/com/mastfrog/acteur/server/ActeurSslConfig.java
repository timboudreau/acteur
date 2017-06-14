/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import com.google.inject.ImplementedBy;
import io.netty.handler.ssl.SslContext;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLException;

/**
 * Provides a default implementation using self-signed certificates, but may
 * be bound to provide your own.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(SelfSignedSslConfig.class)
public abstract class ActeurSslConfig {
    // This class mainly provides an indirection so we can use @ImplementedBy
    // to provide a default implementation without binding it explicitly and
    // precluding applications from providing their own.
    protected abstract SslContext createSslContext() throws CertificateException, SSLException;
    
    private SslContext ctx;
    final SslContext get() throws CertificateException, SSLException {
        if (ctx == null) {
            ctx = createSslContext();
        }
        return ctx;
    }
}
