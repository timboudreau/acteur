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

import com.google.common.net.MediaType;
import com.google.inject.AbstractModule;
import com.mastfrog.acteur.EarlyInterceptionTest.IceptModule;
import com.mastfrog.acteur.annotations.Early;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Method.POST;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.PipelineDecorator;
import static com.mastfrog.acteur.server.PipelineDecorator.AGGREGATOR;
import static com.mastfrog.acteur.server.PipelineDecorator.DECODER;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.net.PortFinder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map.Entry;
import java.util.Random;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({IceptModule.class, TestHarnessModule.class})
public class EarlyInterceptionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Test(timeout = 60000)
    public void test(TestHarness harn) throws Throwable {
        if (true) {
            return;
        }
        String msg = "hello";
        String expect = "Received: hello";
        harn.post("/intercept")
                .setTimeout(TIMEOUT)
                .setBody(msg, MediaType.PLAIN_TEXT_UTF_8)
                .log()
                .go()
//                .await()
                .assertContent(expect);

        System.out.println("ICEPTED: " + interceptedContent);
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
            InterceptActeur(HttpEvent evt, PathFactory pths) throws URISyntaxException, IOException {
                add(Headers.LOCATION, pths.constructURL(com.mastfrog.url.Path.parse("/receive?tok=12345"), false).toURI());
                reply(HttpResponseStatus.TEMPORARY_REDIRECT);
                ByteBuf buf = evt.content();
                if (buf == null) {
                    interceptedContent = -1;
                } else {
                    interceptedContent = buf.readableBytes();
                }
                System.out.println("INTERCEPTED " + interceptedContent);
                System.out.println("PIPELINE: ");
                evt.ctx().pipeline().forEach((Entry<String, ChannelHandler> t) -> {
                    System.out.println("  " + t.getKey() + " - " + t.getValue());
                });
                evt.ctx().pipeline().addAfter(DECODER, AGGREGATOR, new HttpObjectAggregator(8192, true));
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
                System.out.println("READABLE: " + evt.content().readableBytes());
                String received = evt.content() != null ?  evt.stringContent() : "[nothing]";
                ok("Received: " + received);
                System.out.println("\n\n\nReceived: " + received);
                System.out.println("PIPELINE: ");
                evt.ctx().pipeline().forEach((Entry<String, ChannelHandler> t) -> {
                    System.out.println("  " + t.getKey() + " - " + t.getValue());
                });
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
            install(new ServerModule<EarlyInterceptionApplication>(EarlyInterceptionApplication.class));
            bind(ErrorInterceptor.class).to(TestHarness.class);
//            bind(PipelineDecorator.class).to(PLDec.class);
        }
    }

    static final class PLDec implements PipelineDecorator {

        private final H h;

        @Inject
        PLDec(PathFactory fac) throws URISyntaxException {
            this.h = new H(fac);
        }

        @Override
        public void onCreatePipeline(ChannelPipeline pipeline) {
        }

        @Override
        public void onPipelineInitialized(ChannelPipeline pipeline) {
            pipeline.addBefore(AGGREGATOR, "early", h);
        }

        @Sharable
        static final class H extends SimpleChannelInboundHandler<HttpMessage> {

            private final PathFactory fac;
            private final URI uri;

            H(PathFactory fac) throws URISyntaxException {
                this.fac = fac;
                uri = fac.constructURL(com.mastfrog.url.Path.parse("/receive?tok=1234"), false).toURI();
            }

            @Override
            public boolean acceptInboundMessage(Object msg) throws Exception {
                return msg instanceof HttpRequest;
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, HttpMessage msg) throws Exception {
                System.out.println("URI " + ((HttpRequest) msg).uri());
                System.out.println("READ 0 " + msg.getClass().getName());
                System.out.println("MSG: " + msg);
                if (((HttpRequest) msg).uri().startsWith("/intercept")) {
                    DefaultHttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT,
                            new DefaultHttpHeaders()
                                    .add("Location", uri.toString())
                                    .add("Content-Length", 0)
                                    .add("Connection", "keep-alive"));
                    System.out.println("Send quick redirect");
                    ctx.writeAndFlush(resp);
                } else {
                    ctx.fireChannelRead(msg);
                }
            }

        }

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings s = new SettingsBuilder().add("port", 5571).build();
        Dependencies deps = new Dependencies(s, new IceptModule());
        deps.getInstance(Server.class).start().await();
    }
}
