package com.mastfrog.acteur.server;

import com.mastfrog.util.preconditions.Exceptions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * Instantiates channels without reflection, and allows for debug implementation
 * for tracing surprise close events.
 *
 * @author Tim Boudreau
 */
final class NioServerChannelFactory implements ChannelFactory<NioServerSocketChannel> {

    private final boolean debug;
    NioServerChannelFactory(boolean debug) {
        this.debug = debug;
    }

    @Override
    public NioServerSocketChannel newChannel() {
        try {
            return debug ? new DebugNioServerSocketChannel(ServerSocketChannel.open()) : new NioServerSocketChannel(ServerSocketChannel.open());
        } catch (IOException ioe) {
            return Exceptions.chuck(ioe);
        }
    }

    public Channel newChannel(EventLoop eventLoop) {
        return newChannel();
    }

    private static final class DebugNioServerSocketChannel extends NioServerSocketChannel {

        public DebugNioServerSocketChannel() {
        }

        public DebugNioServerSocketChannel(SelectorProvider provider) {
            super( provider );
        }

        public DebugNioServerSocketChannel(ServerSocketChannel channel) {
            super( channel );
        }

        @Override
        protected void doClose() throws Exception {
            new Exception("server doClose").printStackTrace();
            super.doClose(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void doDeregister() throws Exception {
            new Exception("server doDeregister").printStackTrace();
            super.doDeregister(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            new Exception("server Explicit close w/ promise").printStackTrace();
            return super.close(promise); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture close() {
            new Exception("server Explicit close").printStackTrace();
            return super.close(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture disconnect() {
            new Exception("server Explicit disconnect").printStackTrace();
            return super.disconnect(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void doDisconnect() throws Exception {
            new Exception("server doDisconnect").printStackTrace();
            super.doDisconnect(); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
