package com.mastfrog.acteur.server;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.server.UnknownNetworkEventHandler.UNEH;
import io.netty.channel.ChannelHandlerContext;
import javax.inject.Inject;

/**
 * Handler which can receive Netty events which the framework cannot directly
 * handle.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(UNEH.class)
public abstract class UnknownNetworkEventHandler {

    protected abstract void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;

    static final class UNEH extends UnknownNetworkEventHandler {

        @Inject
        UNEH() {
            // constructor for Graal's native-image code to detect
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("Don't know how to process " + msg + " " + msg.getClass().getName());
        }

    }
}
