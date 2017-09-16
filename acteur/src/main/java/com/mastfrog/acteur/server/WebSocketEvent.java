package com.mastfrog.acteur.server;

import com.mastfrog.acteur.Event;
import com.mastfrog.util.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.IOException;
import java.net.SocketAddress;

/**
 *
 * @author Tim Boudreau
 */
public final class WebSocketEvent implements Event<WebSocketFrame> {

    private final WebSocketFrame frame;
    private final ChannelHandlerContext channel;
    private final SocketAddress addr;
    private final Codec mapper;

    public WebSocketEvent(WebSocketFrame frame, ChannelHandlerContext channel, SocketAddress addr, Codec mapper) {
        this.frame = frame;
        this.channel = channel;
        this.addr = addr;
        this.mapper = mapper;
    }

    @Override
    public Channel channel() {
        return channel.channel();
    }

    @Override
    public ChannelHandlerContext ctx() {
        return channel;
    }

    @Override
    public WebSocketFrame request() {
        return frame;
    }

    @Override
    public SocketAddress remoteAddress() {
        return addr;
    }

    @Override
    public <T> T jsonContent(Class<T> type) throws IOException {
        return mapper.readValue(new ByteBufInputStream(frame.content()), type);
    }

    @Override
    public ByteBuf content() throws IOException {
        return frame.content();
    }
}
