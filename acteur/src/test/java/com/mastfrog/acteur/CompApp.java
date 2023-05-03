package com.mastfrog.acteur;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.ActeurFactory.Test;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorRenderer;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluator;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.util.preconditions.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("deprecation")
@Help
@Description("App that does lots of stuff")
public class CompApp extends Application {

    public CompApp() {
        add(IterPage.class);
        add(Unchunked.class);
        add(Echo.class);
        add(DeferredOutput.class);
        add(Branch.class);
//        add(Fails.class);
        add(NoContentPage.class);
        add(DynPage.class);
    }

    public static void main(String[] args) throws Exception {
        Settings s = new SettingsBuilder().add("neverKeepAlive", false)
                .add("aggregateChunks", true)
                .build();
        Dependencies deps = Dependencies.builder().add(new ServerModule<>(CompApp.class)).add(s, Namespace.DEFAULT).build();
        Server server = deps.getInstance(Server.class);
        server.start().await();
    }

    @Override
    public void onError(Throwable err) {
        CompApp.err = err;
    }

    static volatile Throwable err;

    static void throwIfError() throws Throwable {
        Throwable old = err;
        err = null;
        if (old != null) {
            throw old;
        }
    }

    static final class Module extends AbstractModule {

        @Override
        protected void configure() {
            int startPort = 2_000 + (1_000 * new Random(System.currentTimeMillis()).nextInt(40));
            System.setProperty(ServerModule.PORT, "" + new PortFinder(startPort, 65_535, 1_000).findAvailableServerPort());
            install(new ServerModule<>(CompApp.class));
            bind(ExceptionEval.class).asEagerSingleton();
            bind(ErrorRenderer.class).to(ExceptionRen.class);
        }
    }

    static class ExceptionEval extends ExceptionEvaluator {

        @Inject
        ExceptionEval(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
            if (t instanceof ConfigurationException) {
//                if (page instanceof Fails) {
                return Err.conflict("werg");
//                }
            }
            return null;
        }
    }

    static class ExceptionRen extends ErrorRenderer {

        @Override
        @SuppressWarnings("unchecked")
        public String render(ErrorResponse resp, Event<?> evt) throws IOException {
            Map<String, Object> m = (Map<String, Object>) resp.message();
            String s = (String) m.get("error");
            assertNotNull(s);
            if ("werg".equals(s)) {
                return "Hoober";
            }
            return null;
        }

    }

    static class NotBoundThing {

        NotBoundThing(String foo) {

        }
    }

    static class CannotCreateMe {

        CannotCreateMe(NotBoundThing t) {

        }
    }

    /*
    private static class Fails extends Page {

        @Inject
        Fails(ActeurFactory af) {
            add(af.matchPath("^fail$"));
            add(af.matchMethods(Method.GET));
            add(Failer.class);
        }

        static class Failer extends Acteur {

            @Inject
            Failer(CannotCreateMe x) {
                ok("Oh no!");
            }
        }
    }
     */
    private static final class Branch extends Page {

        @Inject
        Branch(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("^branch$"));
            add(af.branch(ABranch.class, BBranch.class, new Test() {

                @Override
                public boolean test(HttpEvent evt) {
                    return "true".equals(evt.urlParameter("a"));
                }

            }));
        }

        private static class ABranch extends Acteur {

            @Inject
            ABranch() {
                ok("A");
            }
        }

        private static class BBranch extends Acteur {

            @Inject
            BBranch() {
                ok("B");
            }
        }
    }

    private static final class Echo extends Page {

        @Inject
        Echo(ActeurFactory af) {
            add(af.matchMethods(Method.POST));
            add(af.matchPath("^echo$"));
            add(EchoActeur.class);
        }

        private static final class EchoActeur extends Acteur {

            @Inject
            EchoActeur(HttpEvent evt, ContentConverter cvt) throws Exception {
//                if (!evt.getContent().isReadable()) {
//                    setState(new RespondWith(400, "Content not readable"));
//                    return;
//                } else if (evt.getContent().readableBytes() <= 0) {
//                    setState(new RespondWith(HttpResponseStatus.EXPECTATION_FAILED, "Zero byte content"));
//                    return;
//                }

                String content = cvt.toObject(evt.content(), evt.header(Headers.CONTENT_TYPE), String.class);
//                String content = evt.getContentAsString();
                ByteBuf buf = evt.content();
//                String content = buf.readCharSequence(buf.readableBytes(), CharsetUtil.UTF_8).toString();

                if (content.isEmpty()) {
                    reply(BAD_REQUEST, "Empty content");
                }
                setState(new RespondWith(200, content));
            }
        }
    }

    private static final class DeferredOutput extends Page {

        @Inject
        DeferredOutput(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("^deferred"));
            add(DeferActeur.class);
        }

        private static class DeferActeur extends Acteur {

            @Inject
            DeferActeur(HttpEvent evt) {
                setResponseWriter(DeferredOutputWriter.class);
                setState(new RespondWith(200));
            }
        }

        private static class DeferredOutputWriter extends ResponseWriter implements Runnable {

            private Output out;

            @Override
            public synchronized void run() {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
                try {
                    out.write("I guess it's okay now");
                    out.channel().close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            @Override
            public synchronized Status write(Event<?> evt, Output out) throws Exception {
                this.out = out;
                Thread t = new Thread(this);
                t.setDaemon(true);
                t.start();
                return Status.DEFERRED;
            }

        }

    }

    private static final class Unchunked extends Page {

        @Inject
        Unchunked(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("^unchunked"));
            add(af.requireParameters("iters"));
            add(OldStyleActeur.class);
        }

        private static final class OldStyleActeur extends Acteur implements ChannelFutureListener {

            private String msg;
            private Integer max;
            private final ExecutorService svc;

            @Inject
            OldStyleActeur(HttpEvent evt, @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService svc) {
                this.svc = svc;
                Optional<Integer> result = evt.uriQueryParameter("iters", Integer.class);
                max = result.get();
                if (max == null) {
                    max = 5;
                }
                msg = evt.urlParameter("msg");
                if (msg == null) {
                    msg = "Iteration ";
                }
                setChunked(false);
                setResponseBodyWriter(this);
                setState(new RespondWith(200));
            }

            int iteration = 0;

            volatile int entryCount = 0;

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (entryCount > 0) {
                    svc.submit(new Callable<Void>() {

                        @Override
                        public Void call() throws Exception {
                            operationComplete(future);
                            return null;
                        }
                    });
                    return;
                }
                ChannelFuture f = future;
                entryCount++;
                try {
                    String message = msg + iteration + "\n";
                    f = f.channel().writeAndFlush(Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
                    if (iteration++ < max) {
                        f.addListener(this);
                    } else {
                        f.addListener(CLOSE);
                    }
                } finally {
                    entryCount--;
                }
            }
        }
    }

    private static final class IterPage extends Page {

        @Inject
        IterPage(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("^iter$"));
            add(af.requireParameters("iters"));
            add(IterActeur.class);
        }

        static class IterActeur extends Acteur {

            @Inject
            IterActeur(HttpEvent evt) {
                setResponseWriter(IterWriter.class);
                setState(new RespondWith(OK));
            }

            static class IterWriter extends ResponseWriter {

                private final int max;

                private String msg;

                @Inject
                IterWriter(HttpEvent evt) {
                    Optional<Integer> result
                            = evt.uriQueryParameter("iters", Integer.class);
                    max = result.get();
                    msg = evt.urlParameter("msg");
                    if (msg == null) {
                        msg = "Iteration ";
                    }
                }

                @Override
                public Status write(Event<?> evt, Output out, int iteration) throws Exception {
                    out.write(msg + iteration + "\n");
                    if (iteration < max) {
                        return Status.NOT_DONE;
                    } else {
                        return Status.DONE;
                    }
                }
            }
        }
    }

    @Methods(GET)
    @Path("/nothing")
    public static class NoContentPage extends Page {

        @Inject
        NoContentPage() {
            add(NoActeur.class);
        }

        static class NoActeur extends Acteur {

            @Inject
            NoActeur() {
                setState(new RespondWith(HttpResponseStatus.PAYMENT_REQUIRED));
            }
        }
    }

    public static class DynPage extends Page {

        @Inject
        DynPage(ActeurFactory af) {
            add(af.matchPath("dyn$"));
            add(af.matchMethods(Method.GET));
            add(FirstActeur.class);
        }
    }

    public static class FirstActeur extends Acteur {

        @Inject
        FirstActeur(Chain<Acteur, ? extends Chain<Acteur, ?>> chain) {
            chain.add(SecondActeur.class);
            next();
        }
    }

    public static class SecondActeur extends Acteur {

        @Inject
        SecondActeur(Chain<Acteur, ? extends Chain<Acteur, ?>> chain) {
            chain.add(ThirdActeur.class);
            next();
        }
    }

    public static class ThirdActeur extends Acteur {

        @Inject
        ThirdActeur() {
            ok("Dynamic acteur");
        }
    }
}
