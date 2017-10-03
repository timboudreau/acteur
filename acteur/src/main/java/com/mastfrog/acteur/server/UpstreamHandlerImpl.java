/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
import com.mastfrog.acteur.ContentConverter;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_DECODE_REAL_IP;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Codec;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import io.netty.handler.codec.http.HttpUtil;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AsciiString;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@ChannelHandler.Sharable
@Singleton
final class UpstreamHandlerImpl extends ChannelInboundHandlerAdapter {

    private final ApplicationControl application;
    private final PathFactory paths;
    private final boolean neverKeepAlive;
    private final boolean aggregateChunks;
    private final Codec mapper;
    @Inject
    private UnknownNetworkEventHandler uneh;

    private final boolean decodeRealIP;
    private final ContentConverter converter;
    private final boolean ssl;

    @Inject
    UpstreamHandlerImpl(ApplicationControl application, PathFactory paths, Codec mapper, Settings settings, ContentConverter converter) {
        this.application = application;
        this.paths = paths;
        this.mapper = mapper;
        this.converter = converter;
        aggregateChunks = settings.getBoolean("aggregateChunks", PipelineFactoryImpl.DEFAULT_AGGREGATE_CHUNKS);
        neverKeepAlive = settings.getBoolean("neverKeepAlive", false);
        decodeRealIP = settings.getBoolean(SETTINGS_KEY_DECODE_REAL_IP, true);
        ssl = settings.getBoolean(ServerModule.SETTINGS_KEY_SSL_ENABLED, false);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        application.internalOnError(cause);
    }

    private static final AsciiString X_REAL_IP = new AsciiString("X-Real-IP");
    private static final AsciiString X_FORWARDED_FOR = new AsciiString("X-Forwarded-For");

    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest request, boolean early) {
        SocketAddress addr = ctx.channel().remoteAddress();
        if (decodeRealIP) {
            String hdr = request.headers().get(X_REAL_IP);
            if (hdr == null) {
                hdr = request.headers().get(X_FORWARDED_FOR);
            }
            if (hdr != null) {
                addr = InetSocketAddress.createUnresolved(hdr, addr instanceof InetSocketAddress ? ((InetSocketAddress) addr).getPort() : 80);
            }
        }
        EventImpl evt = new EventImpl(request, addr, ctx, paths, converter, ssl);
        if (early) {
            evt.early();
            System.out.println("Handle Early Event " + request.uri());
        }
        evt.setNeverKeepAlive(neverKeepAlive);
        application.onEvent(evt, ctx.channel());
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            if (!aggregateChunks && HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }
            handleHttpRequest(ctx, request, false);
        } else if (msg instanceof CloseWebSocketFrame) {
//            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            ctx.channel().close();
        } else if (msg instanceof PingWebSocketFrame) {
            PingWebSocketFrame frame = (PingWebSocketFrame) msg;
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
        } else if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            SocketAddress addr = ctx.channel().remoteAddress();
            // XXX - any way to decode real IP?
            WebSocketEvent wsEvent = new WebSocketEvent(frame, ctx, addr, mapper);

            application.onEvent(wsEvent, ctx.channel());
        } else {
            if (uneh != null) {
                uneh.channelRead(ctx, msg);
            }
        }
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
        ctx.writeAndFlush(response);
    }
}
