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

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluator;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import com.mastfrog.acteur.util.ErrorHandler;
import com.mastfrog.giulius.Ordered;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.security.cert.CertificateException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.net.ssl.SSLException;

/**
 *
 * @author Tim Boudreau
 */
final class SelfSignedSslConfig extends ActeurSslConfig {

    private final Provider<SslProvider> provider;
    private final Provider<ErrorHandler.Registry> reg;

    @Inject
    SelfSignedSslConfig(Provider<SslProvider> provider, Provider<ErrorHandler.Registry> reg) {
        this.provider = provider;
        this.reg = reg;
    }

    @Override
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public SslContext createSslContext() throws CertificateException, SSLException {
        new SuppressUnknownCertificateAlertsHandler(reg.get());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(provider.get()).build();
    }

    private static class SuppressUnknownCertificateAlertsHandler extends ErrorHandler {

        private SuppressUnknownCertificateAlertsHandler(Registry handlers) {
            super(handlers);
        }

        @Override
        protected void handle(Throwable t, Consumer<Throwable> next) {
            if (t instanceof io.netty.handler.codec.DecoderException && t.getCause() instanceof javax.net.ssl.SSLException) {
                if (t.getMessage() != null && t.getMessage().contains("Received fatal alert")) {
                    return;
                }
            }
            next.accept(t);
        }
    }

    @Ordered(1)
    static class SuppressUnknownCAExceptions extends ExceptionEvaluator {

        public SuppressUnknownCAExceptions(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
            if (t instanceof io.netty.handler.codec.DecoderException && t.getCause() instanceof javax.net.ssl.SSLException) {
                if (t.getMessage() != null && t.getMessage().contains("Received fatal alert")) {
                    evt.channel().close();
                    return Err.gone("X");
                }
            }
            return null;
        }

    }

}
