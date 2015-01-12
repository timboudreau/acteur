package com.mastfrog.acteur;

import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.util.Checks;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A registry of resources (for example, streams or JDBC objects) which should
 * be closed if the connection terminates while a response is being processed.
 * It is common to, say, open a result set, and then drizzle it out one row at a
 * time; if the client closes the connection, registering the object with the
 * Closables instance tied to this request guarantees it is closed if the
 * channel is.
 *
 * @author Tim Boudreau
 */
public final class Closables {

    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private final List<Timer> timers = new CopyOnWriteArrayList<>();
    private final CloseWhenChannelCloses closeListener = new CloseWhenChannelCloses();
    private final ApplicationControl application;

    Closables(Channel channel, ApplicationControl application) {
        channel.closeFuture().addListener(closeListener);
        this.application = application;
    }

    public final <T extends AutoCloseable> T add(T closable) {
        Checks.notNull("closeable", closable);
        if (!closeables.contains(closable)) {
            closeables.add(closable);
        }
        return closable;
    }

    public final <T extends Timer> T add(T timer) {
        Checks.notNull("timer", timer);
        if (!timers.contains(timer)) {
            timers.add(timer);
        }
        return timer;
    }

    public final Closables add(Runnable run) {
        Checks.notNull("run", run);
        for (AutoCloseable clos : closeables) {
            if (clos instanceof RunnableWrapper) {
                RunnableWrapper w = (RunnableWrapper) clos;
                if (w.run == w.run) {
                    return this;
                }
            }
        }
        add(new RunnableWrapper(run));
        return this;
    }

    class CloseWhenChannelCloses implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            close();
        }
    }

    static class RunnableWrapper implements AutoCloseable {

        private final Runnable run;

        public RunnableWrapper(Runnable run) {
            this.run = run;
        }

        @Override
        public void close() throws Exception {
            run.run();
        }
    }

    void close() throws Exception {
        for (AutoCloseable ac : closeables) {
            try {
                ac.close();
            } catch (Exception e1) {
                application.internalOnError(e1);
            }
        }
        for (Timer t : timers) {
            try {
                t.cancel();
            } catch (Exception e2) {
                application.internalOnError(e2);
            }
        }
    }
}
