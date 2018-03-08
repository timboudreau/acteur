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
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AsyncAuthenticationModuleTest.AApp.AuthPage.PostAuthActeur;
import com.mastfrog.acteur.auth.AsyncAuthenticationModuleTest.AuthTestModule;
import static com.mastfrog.acteur.headers.Headers.AUTHORIZATION;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.preconditions.Authenticated.OPTIONAL;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.function.EnhCompletableFuture;
import com.mastfrog.util.function.EnhCompletionStage;
import io.netty.handler.codec.http.HttpHeaderNames;
import static io.netty.handler.codec.http.HttpResponseStatus.EXPECTATION_FAILED;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiceRunner.class)
@TestWith({AuthTestModule.class, TestHarnessModule.class})
public class AsyncAuthenticationModuleTest {

    private static final long timeout = 5000;
    private static final Duration DUR = Duration.ofMillis(timeout);

    @Test(timeout = timeout)
    public void testAsyncAuth(TestHarness harn) throws Throwable {
        harn.get("/noauth")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(OK)
                .assertContent("hello");

        harn.get("/authit")
                .addHeader(AUTHORIZATION.toStringHeader(), "Bearer abcd")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(OK)
                .assertContent("Hello Moe");

        harn.get("/authit")
                .addHeader(AUTHORIZATION.toStringHeader(), "Bearer efgh")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(OK)
                .assertContent("Hello Curly");

        harn.get("/authit")
                .addHeader(AUTHORIZATION.toStringHeader(), "Bearer ijkl")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(EXPECTATION_FAILED)
                .assertContent("You are bad.");

        harn.get("/authit")
                .addHeader(AUTHORIZATION.toStringHeader(), "Bearer wxyz")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(UNAUTHORIZED)
                .assertContent("Unknown user");

        harn.get("/authit")
                .addHeader(AUTHORIZATION.toStringHeader(), "Bearer mnop")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(INTERNAL_SERVER_ERROR);

        harn.get("/authit")
                .addHeader(AUTHORIZATION.toStringHeader(), "Gwerb")
                .go()
                .await()
                .assertStatus(UNAUTHORIZED);

        harn.get("/authit")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(UNAUTHORIZED);

        harn.get("/maybe")
                .addHeader(AUTHORIZATION.toStringHeader(), "Bearer abcd")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(OK)
                .assertContent("Goodbye Moe");

        harn.get("/maybe")
                .addHeader(AUTHORIZATION.toStringHeader(), "Bearer efgh")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(OK)
                .assertContent("Goodbye Curly");

        harn.get("/maybe")
                .setTimeout(DUR)
                .go()
                .await()
                .assertStatus(OK)
                .assertContent("Goodbye Nobody");
    }

    static final class AuthTestModule extends AbstractModule {

        private final ReentrantScope scope = new ReentrantScope();

        @Override
        protected void configure() {
            install(new AsyncAuthenticationModule(FakeUser.class, Auth.class, scope));
            install(new ServerModule(scope, AApp.class, 1, 3, 1));
        }
    }

    static final class Auth implements AsyncAuthenticator<FakeUser> {

        private final ExecutorService svc;

        @Inject
        Auth(ShutdownHookRegistry reg) {
            svc = Executors.newCachedThreadPool();
            reg.add(svc);
        }

        @Override
        public String validate(HttpEvent evt, String token) {
            String header = evt.header(HttpHeaderNames.AUTHORIZATION);
            if (header == null) {
                return "No auth header";
            }
            if ("abcd".equals(token) || "efgh".equals(token) || "ijkl".equals(token) || "mnop".equals(token)) {
                return null;
            }
            return "Unknown user";
        }

        @Override
        public Class type() {
            return FakeUser.class;
        }

        @Override
        public EnhCompletionStage<AuthenticationResult<FakeUser>> authenticate(RequestID rid, HttpEvent evt, String token) {
            EnhCompletableFuture<AuthenticationResult<FakeUser>> result = new EnhCompletableFuture<>();
            svc.submit(() -> {
                AuthenticationResult<FakeUser> res;
                if ("abcd".equals(token) || "efgh".equals(token)) {
                    res = new AuthenticationResult<>(new FakeUser("abcd".equals(token) ? "Moe" : "Curly"), token);
                } else {
                    if ("mnop".equals(token)) {
                        result.completeExceptionally(new RuntimeException("Complete auth exceptionally"));
                        return;
                    }
                    res = new AuthenticationResult<>(null, EXPECTATION_FAILED, "You are bad.", token);
                }
                result.complete(res);
            });
            return result;
        }
    }

    public static final class FakeUser {

        public final String user;

        public FakeUser(String user) {
            this.user = user;
        }

        @Override
        public String toString() {
            return user;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FakeUser && ((FakeUser) o).user.equals(user);
        }

        @Override
        public int hashCode() {
            return user.hashCode();
        }
    }

    static final class AApp extends Application {

        AApp() {
            add(AuthPage.class);
            add(OptionalAuthPage.class);
            add(NoAuthPage.class);
        }

        @com.mastfrog.acteur.preconditions.Authenticated
        @com.mastfrog.acteur.preconditions.Methods(GET)
        @com.mastfrog.acteur.preconditions.Path("/authit")
        static final class AuthPage extends Page {

            AuthPage() {
                add(PostAuthActeur.class);
            }

            static final class PostAuthActeur extends Acteur {

                @Inject
                PostAuthActeur(AuthenticationResult<?> aa, AuthenticationResult<FakeUser> fu) {
                    FakeUser u = fu.info;
                    ok("Hello " + u.user);
                }
            }
        }

        @com.mastfrog.acteur.preconditions.Authenticated(OPTIONAL)
        @com.mastfrog.acteur.preconditions.Methods(GET)
        @com.mastfrog.acteur.preconditions.Path("/maybe")
        static final class OptionalAuthPage extends Page {

            OptionalAuthPage() {
                add(PostAuthActeur.class);
            }

            static final class PostAuthActeur extends Acteur {

                @Inject
                PostAuthActeur(@Nullable AuthenticationResult<?> aa, @Nullable AuthenticationResult<FakeUser> fu) {
                    if (fu == null) {
                        ok("Goodbye Nobody");
                        return;
                    }
                    FakeUser u = fu.info;
                    ok("Goodbye " + (u == null ? "Nobody" : u.user));
                }
            }
        }

        @com.mastfrog.acteur.preconditions.Methods(GET)
        @com.mastfrog.acteur.preconditions.Path("/noauth")
        static final class NoAuthPage extends Page {

            NoAuthPage() {
                add(NAA.class);
            }

            static final class NAA extends Acteur {

                NAA() {
                    ok("hello");
                }
            }
        }
    }
}
