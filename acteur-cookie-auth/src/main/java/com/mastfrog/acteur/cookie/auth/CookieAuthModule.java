package com.mastfrog.acteur.cookie.auth;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.giulius.scope.ReentrantScope;

/**
 * Module which sets up bindings for Cookie authentication, if provided an
 * implementation of {@link UserFinder}.
 *
 * @author Tim Boudreau
 */
public final class CookieAuthModule extends AbstractModule {

    private final ReentrantScope scope;
    private final UserTypeAndFinderType<?> types;

    /**
     * Create a module
     *
     * @param scope This <i>must</i> be the scope which the ServerModule or
     * GenericApplicationModule will use as its request scope. This can be done
     * by subclassing either of those, or by passing the same instance of
     * ReentrantScope to both the constructor of your ServerBuilder
     * @param userType The class that represents a User object in this
     * application, which the UserFinder will return if authentication succeeds.
     * @param finderType The type of UserFinder to bind.
     */
    public <T> CookieAuthModule(ReentrantScope scope, Class<T> userType, Class<? extends UserFinder<T>> finderType) {
        this.scope = scope;
        types = new UserTypeAndFinderType<>(userType, finderType);
    }

    @Override
    protected void configure() {
        bind(new TL()).to(types.finderType);
        scope.bindTypes(binder(), types.userType);
        bind(AuthenticationActeur.class).to(CookieActeur.class);
    }

    private static final class TL extends TypeLiteral<UserFinder<?>> {
    }

    static class UserTypeAndFinderType<T> {

        final Class<T> userType;
        final Class<? extends UserFinder<T>> finderType;

        UserTypeAndFinderType(Class<T> userType, Class<? extends UserFinder<T>> finderType) {
            this.userType = userType;
            this.finderType = finderType;
        }
    }
}
