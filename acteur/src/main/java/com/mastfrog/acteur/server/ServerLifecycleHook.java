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

package com.mastfrog.acteur.server;

import com.google.inject.Singleton;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.spi.ApplicationControl;
import static com.mastfrog.util.Checks.notNull;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Implement and bind as an eager singleton if you have code that should run
 * <i>only</i> in the case of (and after) the server is started.
 * <p>
 * Note:  in the case you start the server instance repeatedly on several ports
 * (say, ssl and non ssl), initialization code is run once the <i>first</i>
 * port is opened.
 * <p>
 * A hook may throw an exception, which will be logged via the usual mechanism
 * (ApplicationControl.internalOnError()).  This will not prevent other hooks from
 * being called.
 *
 * @author Tim Boudreau
 */
public abstract class ServerLifecycleHook {

    protected ServerLifecycleHook(Registry reg) {
        reg.register(this);
    }

    /**
     * Called when the first server channel (socket) is opened.
     *
     * @param application The application
     * @param channel The server socket channel
     * @throws Exception If something goes wrong
     */
    protected abstract void onStartup(Application application, Channel channel) throws Exception;

    /**
     * Called immediately <i>after</i> shutdown, when the server socket is closed.
     * @throws Exception
     */
    protected void onShutdown() throws Exception {
        // do nothing
    }

    @Singleton
    public static final class Registry {
        private final List<ServerLifecycleHook> hooks = new ArrayList<>();
        private final List<ServerLifecycleHook> forShutdown = new ArrayList<>();
        private final L l = new L();
        private final S s = new S();
        private final Provider<Application> applicationProvider;
        private ChannelFuture future;
        private final Provider<ApplicationControl> ctrl;
        private volatile boolean startupExceptionsThrown;

        @Inject
        Registry(Provider<Application> applicationProvider, Provider<ApplicationControl> ctrl) {
            this.applicationProvider = applicationProvider;
            this.ctrl = ctrl;
        }

        synchronized Registry register(ServerLifecycleHook hook) {
            if (future != null) {
                throw new IllegalStateException("ServerStartupHook " + hook + " being registered *after* startup - not bound as "
                        + "an eager singleton?");
            }
            hooks.add(notNull("hook", hook));
            return this;
        }

        ChannelFutureListener listener() {
            return l;
        }

        public boolean startupExceptionsThrown() {
            return startupExceptionsThrown;
        }

        synchronized void runHooks(ChannelFuture fut) {
            future = fut;
            if (!hooks.isEmpty()) {
                Channel channel = fut.channel();
                channel.closeFuture().addListener(s);
                Application application = applicationProvider.get();
                List<ServerLifecycleHook> local = new ArrayList<>(hooks);
                hooks.clear();
                for (ServerLifecycleHook hook : local) {
                    try {
                        hook.onStartup(application, channel);
                        forShutdown.add(hook);
                    } catch (Exception ex) {
                        startupExceptionsThrown = true;
                        ctrl.get().internalOnError(ex);
                    }
                }
            }
        }
        
        synchronized void onShutdown(ChannelFuture fut) {
            future = fut;
            if (!forShutdown.isEmpty()) {
                for (ServerLifecycleHook hook : forShutdown) {
                    try {
                        hook.onShutdown();
                    } catch (Exception ex) {
                        ctrl.get().internalOnError(ex);
                    }
                }
            }
        }

        private class S implements ChannelFutureListener {

            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                onShutdown(f);
            }

        }

        private class L implements ChannelFutureListener {

            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (f.isSuccess()) {
                    runHooks(f);
                }
            }
        }
    }
}
