package com.mastfrog.acteur;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur.RespondWith;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.netty.http.test.harness.TestHarness;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import org.openide.util.Exceptions;

/**
 *
 * @author tim
 */
public class CompApp extends Application {

    public CompApp() {
        add(IterPage.class);
        add(Unchunked.class);
        add(Echo.class);
        add(DeferredOutput.class);
        add(NoContentPage.class);
    }

    @Override
    public void onError(Throwable err) {
        System.out.println("OUCH! ");
        err.printStackTrace();
        this.err = err;
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
            install(new ServerModule(CompApp.class));
            bind(ErrorInterceptor.class).to(TestHarness.class);
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
            EchoActeur(Event evt) throws IOException {
                if (!evt.getContent().isReadable()) {
                    setState(new RespondWith(400, "Content not readable"));
                } else if (evt.getContent().readableBytes() <= 0) {
                    setState(new RespondWith(HttpResponseStatus.EXPECTATION_FAILED, "Zero byte content"));
                }
                String content = evt.getContentAsJSON(String.class);
                System.out.println("SEND MESSAGE '" + content + "'");
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
            DeferActeur(Event evt) {
                setResponseWriter(DeferredOutputWriter.class);
                setState(new RespondWith(200));
            }
        }

        private static class DeferredOutputWriter extends ResponseWriter implements Runnable {

            private Output out;

            @Override
            public synchronized void run() {
                try {
                    Thread.sleep(3000);
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
            public synchronized Status write(Event evt, Output out) throws Exception {
                System.out.println("Deferring write");
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

            @Inject
            OldStyleActeur(Event evt) {
                max = evt.getIntParameter("iters").get();
                if (max == null) {
                    max = 5;
                }
                System.out.println("Created an iterWriter");
                msg = evt.getParameter("msg");
                if (msg == null) {
                    msg = "Iteration ";
                }
                setChunked(false);
                setResponseBodyWriter(this);
                setState(new RespondWith(200));
            }

            int iteration = 0;

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                System.out.println("Iteration " + iteration + "\n");
                future = future.channel().write(Unpooled.copiedBuffer(msg + iteration + "\n", CharsetUtil.UTF_8));
                if (iteration++ < max) {
                    future.addListener(this);
                } else {
                    future.addListener(CLOSE);
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
            IterActeur(Event evt) {
                setResponseWriter(IterWriter.class);
                setState(new RespondWith(OK));
            }

            static class IterWriter extends ResponseWriter {

                private int max;

                private String msg;

                @Inject
                IterWriter(Event evt) {
                    max = evt.getIntParameter("iters").get();
                    System.out.println("Created an iterWriter");
                    msg = evt.getParameter("msg");
                    if (msg == null) {
                        msg = "Iteration ";
                    }
                }

                @Override
                public Status write(Event evt, Output out, int iteration) throws Exception {
                    System.out.println("Iteration " + iteration + "\n");
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
    
    public static class NoContentPage extends Page {
        @Inject
        NoContentPage(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("^nothing$"));
            add(NoActeur.class);
        }
        
        static class NoActeur extends Acteur {
            @Inject
            NoActeur() {
                setState(new RespondWith(HttpResponseStatus.PAYMENT_REQUIRED));
            }
        }
    }
}
