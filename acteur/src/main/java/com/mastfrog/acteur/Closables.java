package com.mastfrog.acteur;

import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.function.misc.QuietAutoClosable;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    private final CloseWhenChannelCloses closeListener;
    private final ApplicationControl application;
    private volatile boolean closed;

    Closables(Channel channel, ApplicationControl application) {
        channel.closeFuture().addListener(closeListener = new CloseWhenChannelCloses(channel));
        this.application = application;
    }

    @Override
    public String toString() {
        return Strings.join(',', closeables) + "/" + Strings.join(',', timers);
    }

    public <T, R extends CompletionStage<T>> R add(R fut) {
        if (fut instanceof CompletableFuture<?>) {
            add(new AutoClosableWrapper(fut));
        }
        return fut;
    }

    private void checkClosed() {
        if (closed) {
            try {
                close();
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
        }
    }

    public <T extends AutoCloseable> T add(T closable) {
        Checks.notNull("closeable", closable);
        if (!closeables.contains(closable)) {
            closeables.add(closable);
        }
        checkClosed();
        return closable;
    }

    public <T extends Timer> T add(T timer) {
        Checks.notNull("timer", timer);
        if (!timers.contains(timer)) {
            timers.add(timer);
        }
        checkClosed();
        return timer;
    }

    public Closables add(Runnable run) {
        Checks.notNull("run", run);
        for (AutoCloseable clos : closeables) {
            if (clos instanceof RunnableWrapper) {
                RunnableWrapper w = (RunnableWrapper) clos;
                if (w.run == w.run) { // XXX what was this REALLY trying to do???
                    return this;
                }
            }
        }
        add(new RunnableWrapper(run));
        checkClosed();
        return this;
    }

    void forceClose() throws Exception {
        closeListener.earlyClose();
    }

    void closeOn(ChannelFuture future) {
        future.addListener(closeListener);
        closeListener.detach();
    }

    public boolean isClosed() {
        return closed;
    }

    final class CloseWhenChannelCloses implements ChannelFutureListener {

        private final Channel channel;

        public CloseWhenChannelCloses(Channel channel) {
            this.channel = channel;
            channel.closeFuture().addListener(this);
        }

        void detach() {
            channel.closeFuture().removeListener(this);
        }

        void earlyClose() throws Exception {
            detach();
            close();
        }

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            close();
        }

        public String toString() {
            return "channel:" + channel.id();
        }
    }

    static class RunnableWrapper implements AutoCloseable {

        private final Runnable run;

        RunnableWrapper(Runnable run) {
            this.run = run;
        }

        @Override
        public void close() throws Exception {
            run.run();
        }

        public String toString() {
            return run.toString();
        }
    }

    void close() throws Exception {
        closed = true;
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

    static final class AutoClosableWrapper implements QuietAutoClosable {

        private final Reference<CompletionStage<?>> fut;

        AutoClosableWrapper(CompletionStage<?> fut) {
            this.fut = new WeakReference<>(fut);
        }

        @Override
        public void close() {
            CompletionStage<?> c = fut.get();
            CompletableFuture<?> future = c instanceof CompletableFuture<?>
                    ? (CompletableFuture<?>) fut.get() : null;
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }

        @Override
        public String toString() {
            CompletionStage<?> c = fut.get();
            CompletableFuture<?> f = c instanceof CompletableFuture<?>
                    ? (CompletableFuture<?>) fut.get() : null;
            return f != null ? f.toString() : "<already-gcd>";
        }
    }
}
