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
package com.mastfrog.acteur;

import com.google.inject.AbstractModule;
import com.mastfrog.acteur.EarlyInterceptionTest.IceptModule;
import com.mastfrog.acteur.annotations.Early;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.POST;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.PathFactory;
import static com.mastfrog.acteur.server.PipelineDecorator.HANDLER;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.InjectionInfo;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import static com.mastfrog.mime.MimeType.PLAIN_TEXT_UTF_8;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.net.PortFinder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.LastHttpContent;
import static io.netty.util.CharsetUtil.UTF_8;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Random;
import javax.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests that the &#064;Early annotation works to
 *
 * @author Tim Boudreau
 */
@TestWith({IceptModule.class, HttpTestHarnessModule.class, SilentRequestLogger.class})
public class EarlyInterceptionTest {

    @Test
    @Timeout(60)
    public void test(HttpHarness harn, Application app) throws Throwable {
        assertTrue(app.hasEarlyPages(), "No early pages found");
        assertTrue(app.isEarlyPageMatch(new DefaultHttpRequest(HTTP_1_1, HttpMethod.POST, "/intercept")), "URI not matched");
        String msg = "hello";
        String expect = "Received=hello, bytes=0, tok=12345";

        harn.post("/intercept", msg, UTF_8)
                .setHeader(Headers.CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .asserting(a -> a.assertOk().assertBody(expect))
                .assertAllSucceeded();

        assertEquals(0, interceptedContent);
        harn.rethrowServerErrors();
    }

    private static final class EarlyInterceptionApplication extends Application {

        EarlyInterceptionApplication() {
            add(InterceptPage.class);
            add(ReceivePayloadPage.class);
//            add(Fallthrough.class);
        }
    }

    static int interceptedContent = -2;

    @Path("/intercept")
    @Methods(POST)
    @Early
    static final class InterceptPage extends Page {

        InterceptPage() {
            add(InterceptActeur.class);
        }

        static class InterceptActeur extends Acteur {

            @Inject
            @SuppressWarnings("deprecation")
            InterceptActeur(HttpEvent evt, PathFactory pths, Deferral defer) throws URISyntaxException, IOException {
                ByteBuf buf = evt.content();
                if (buf == null) {
                    interceptedContent = -1;
                } else {
                    interceptedContent = buf.readableBytes();
                }
                evt.ctx().pipeline().addAfter(HANDLER, "bytes", new Bytes(defer.defer(), evt.channel().alloc()));
                reply(HttpResponseStatus.TEMPORARY_REDIRECT);
                add(Headers.LOCATION, new URI("/receive?tok=12345&bytes=" + interceptedContent));
            }
        }
    }

    static class Bytes extends SimpleChannelInboundHandler<HttpContent> {

        private final Resumer resumer;
        final CompositeByteBuf buf;

        Bytes(Resumer resumer, ByteBufAllocator alloc) {
            super(HttpContent.class);
            this.resumer = resumer;
            buf = alloc.compositeBuffer();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext chc, HttpContent i) throws Exception {
            buf.addComponent(i.content());
            buf.writerIndex(buf.writerIndex() + i.content().readableBytes());
            if (i instanceof LastHttpContent) {
                resumer.resume(this);
            }
        }
    }

    @Path("/receive")
    @Methods(POST)
    static final class ReceivePayloadPage extends Page {

        @Inject
        ReceivePayloadPage(HttpEvent evt) {
            add(ReceiveActeur.class);
        }

        static class ReceiveActeur extends Acteur {

            @Inject
            ReceiveActeur(HttpEvent evt) throws URISyntaxException, IOException {
                evt.content().resetReaderIndex();
                Optional<Integer> bytes = evt.uriQueryParameter("bytes", Integer.class);
                if (bytes.isEmpty()) {
                    reply(INTERNAL_SERVER_ERROR, "No bytes= url parameter");
                } else {
                    int val = bytes.get();
                    if (val != 0) {
                        reply(INTERNAL_SERVER_ERROR, "HTTP request should not have had a payload yet when "
                                + "the redirect was processed, but found " + val + " bytes");
                    }
                }
                String received = evt.content() != null ? evt.stringContent() : "[nothing]";
                ok("Received=" + received + ", bytes=" + evt.urlParameter("bytes") + ", tok=" + evt.urlParameter("tok"));
            }
        }
    }

    static final class Fallthrough extends Page {

        Fallthrough() {
            add(FallthroughActeur.class);
        }

        static final class FallthroughActeur extends Acteur {

            @Inject
            FallthroughActeur(HttpEvent evt) {
                reply(BAD_REQUEST, "No " + evt.method() + " " + evt.path());
            }
        }
    }

    static class IceptModule extends AbstractModule {

        private final ReentrantScope scope = new ReentrantScope(new InjectionInfo());

        @Override
        protected void configure() {
            int startPort = 2_000 + (1_000 * new Random(System.currentTimeMillis()).nextInt(40));
            System.setProperty(ServerModule.PORT, "" + new PortFinder(startPort, 65_535, 1_000).findAvailableServerPort());
            install(new ServerModule<>(scope, EarlyInterceptionApplication.class, 2, 2, 1));
            scope.bindTypes(binder(), Bytes.class);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings s = new SettingsBuilder().add("port", 5_571).build();
        Dependencies deps = new Dependencies(s, new IceptModule());
        deps.getInstance(Server.class).start().await();
    }

}
