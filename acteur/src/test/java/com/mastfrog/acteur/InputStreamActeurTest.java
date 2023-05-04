/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.acteur;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.output.InputStreamActeur;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import com.mastfrog.mime.MimeType;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({InputStreamActeurTest.M.class, SilentRequestLogger.class, HttpTestHarnessModule.class})
public final class InputStreamActeurTest {

    @Test
    @Timeout(30)
    public void testInputStreamIsHandled(HttpHarness harn) {
        harn.get("istream")
                .asserting(a -> {
                    a.assertHeader(CONTENT_TYPE.toString(), MimeType.OCTET_STREAM.toString())
                            .assertOk()
                            .assertChunk("Test chunks match batch size", chunk -> {
                                // Value is set in InputStreamActeurTest.properties in test
                                // resources, to be immune to changes in the default
                                return chunk.remaining() <= 256;
                            })
                            .assertPayload(data -> {
                                return Arrays.equals(BYTES, data);
                            });
                }).assertAllSucceeded();
    }

    static class A extends Application {

        A() {
            add(P.class);
        }
    }

    static class P extends Page {

        @Inject
        P(ActeurFactory af) {
            add(af.matchMethods(GET));
            add(af.matchPath("^istream$"));
            add(af.parametersMustBeNumbersIfTheyArePresent(false, false, "length"));
            add(S.class);
            add(InputStreamActeur.class);
        }
    }

    static class S extends Acteur {

        S() {
            add(CONTENT_TYPE, MimeType.OCTET_STREAM);
            next(new ByteArrayInputStream(BYTES));
        }
    }

    static class M implements Module {

        @Override
        public void configure(Binder binder) {
            binder.install(new ServerModule<>(A.class));
        }
    }

    static final byte[] BYTES;

    static {
        Random rnd = new Random(89_247_284);
        BYTES = new byte[2_500];
        rnd.nextBytes(BYTES);
    }
}
