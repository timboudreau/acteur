package com.mastfrog.acteur.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.acteur.Event;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;

/**
 *
 * @author Tim Boudreau
 */
final class WebSocketEvent implements Event<WebSocketFrame> {
    
    private final WebSocketFrame frame;
    private final Channel channel;
    private final SocketAddress addr;
    private final ObjectMapper mapper;

    public WebSocketEvent(WebSocketFrame frame, Channel channel, SocketAddress addr, ObjectMapper mapper) {
        this.frame = frame;
        this.channel = channel;
        this.addr = addr;
        this.mapper = mapper;
    }
    
    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public WebSocketFrame getRequest() {
        return frame;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return addr;
    }

    @Override
    public <T> T getContentAsJSON(Class<T> type) throws IOException {
        return mapper.readValue(new ByteBufInputStream(frame.content()), type);
    }

    @Override
    public ByteBuf getContent() throws IOException {
        return frame.content();
    }

    public OutputStream getContentAsStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(40);
        getChannel().read();
        ByteBuf inbound = getContent();
        int count;
        do {
            count = inbound.readableBytes();
            if (count > 0) {
                inbound.readBytes(out, count);
            }
        } while (count > 0);
        return out;
    }
}
