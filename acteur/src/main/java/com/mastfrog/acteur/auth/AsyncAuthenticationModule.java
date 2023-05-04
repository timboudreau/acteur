/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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
package com.mastfrog.acteur.auth;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.util.TypeUtils;
import javax.inject.Inject;

/**
 * Configures an acteur application for asynchronous authentication.
 *
 * @author Tim Boudreau
 */
public class AsyncAuthenticationModule<AuthInfoType> extends AbstractModule {

    private final Class<? extends AuthInfoType> infoType;
    private final Class<? extends AsyncAuthenticator<AuthInfoType>> authenticatorType;

    private final ReentrantScope scope;

    public AsyncAuthenticationModule(Class<? extends AuthInfoType> infoType,
            Class<? extends AsyncAuthenticator<AuthInfoType>> authenticatorType,
            ReentrantScope scope) {
        this.infoType = infoType;
        this.authenticatorType = authenticatorType;
        this.scope = scope;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void configure() {
        scope.bindTypesAllowingNulls(binder(), infoType);
        bind(AuthenticationActeur.class).to(AsyncAuthenticationActeur.class);
        bind(new TypeLiteral<AsyncAuthenticator<?>>() {
        }).to(authenticatorType);
        Key<AuthenticationResult<AuthInfoType>> key = (Key<AuthenticationResult<AuthInfoType>>) Key.get(TypeUtils.generifiedType(AuthenticationResult.class, infoType));
        Provider<AuthenticationResult<AuthInfoType>> p = scope.scope(key, Providers.of(null));
        bind(key).toProvider(p);
        bind(new TypeLiteral<AsyncAuthenticator<?>>() {
        }).to(authenticatorType);
        bind(new TypeLiteral<AuthenticationResult<?>>() {
        }).toProvider(new ParameterizedAuthProvider(p));
    }

    static final class ParameterizedAuthProvider implements Provider<AuthenticationResult<?>> {

        private final Provider<? extends AuthenticationResult<?>> resProvider;

        @Inject
         ParameterizedAuthProvider(Provider<? extends AuthenticationResult<?>> resProvider) {
            this.resProvider = resProvider;
        }

        @Override
        public AuthenticationResult<?> get() {
            return resProvider.get();
        }
    }
}
