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

import com.mastfrog.mime.MimeType;
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
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.InjectionInfo;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
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
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Random;
import javax.inject.Inject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({IceptModule.class, TestHarnessModule.class, SilentRequestLogger.class})
public class EarlyInterceptionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Test(timeout = 60000)
    public void test(TestHarness harn, Application app) throws Throwable {
        assertTrue("No early pages found", app.hasEarlyPages());
        assertTrue("URI not matched", app.isEarlyPageMatch(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/intercept")));

        if (true) {
            // XXX fix netty-http-test-harness to deal with early redirects
            return;
        }

        String msg = "hello";
        String expect = "Received: hello";
        harn.post("/intercept")
                .setTimeout(TIMEOUT)
                .setBody(msg, MimeType.PLAIN_TEXT_UTF_8)
                .go()
                .await()
                .assertContent(expect);

        assertEquals(0, interceptedContent);
    }

    private static final class EarlyInterceptionApplication extends Application {

        EarlyInterceptionApplication() {
            add(InterceptPage.class);
            add(ReceivePayloadPage.class);
            add(Fallthrough.class);
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
//                add(Headers.LOCATION, pths.constructURL(com.mastfrog.url.Path.parse("/receive?tok=12345"), false).toURI());
                add(Headers.LOCATION, new URI("/receive?tok=12345"));
                reply(HttpResponseStatus.TEMPORARY_REDIRECT);
                ByteBuf buf = evt.content();
                if (buf == null) {
                    interceptedContent = -1;
                } else {
                    interceptedContent = buf.readableBytes();
                }
                evt.ctx().pipeline().addAfter(HANDLER, "bytes", new Bytes(defer.defer(), evt.channel().alloc()));
            }
        }
    }

    static class Bytes extends SimpleChannelInboundHandler<HttpContent> {

        private final Resumer resumer;
        final CompositeByteBuf buf;

        public Bytes(Resumer resumer, ByteBufAllocator alloc) {
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
                String received = evt.content() != null ? evt.stringContent() : "[nothing]";
                ok("Received: " + received);
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
            int startPort = 2000 + (1000 * new Random(System.currentTimeMillis()).nextInt(40));
            System.setProperty(ServerModule.PORT, "" + new PortFinder(startPort, 65535, 1000).findAvailableServerPort());
            bind(HttpClient.class).toInstance(HttpClient.builder()
                    .noCompression()
                    .followRedirects()
                    .resolveAllHostsToLocalhost()
                    .threadCount(4)
                    .setUserAgent(EarlyInterceptionTest.class.getName()).build());
            install(new ServerModule<>(scope, EarlyInterceptionApplication.class, 2, 2, 1));
            scope.bindTypes(binder(), Bytes.class);
            bind(ErrorInterceptor.class).to(TestHarness.class);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings s = new SettingsBuilder().add("port", 5571).build();
        Dependencies deps = new Dependencies(s, new IceptModule());
        deps.getInstance(Server.class).start().await();
    }
}
