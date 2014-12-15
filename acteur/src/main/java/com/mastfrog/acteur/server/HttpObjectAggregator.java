package com.mastfrog.acteur.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.MessageAggregator;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class HttpObjectAggregator
        extends MessageAggregator<HttpObject, HttpMessage, HttpContent, FullHttpMessage> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpObjectAggregator.class);
    private static final ByteBuf CONTINUE_LINE = Unpooled.copiedBuffer("HTTP/1.1 100 CONTINUE\r\n\r\n", 
            CharsetUtil.US_ASCII);
    private static final ByteBuf TOO_LARGE_LINE = Unpooled.copiedBuffer("HTTP/1.1 413 REQUEST ENTITY TOO LARGE\r\n\r\nContent-Length: 0\r\n", 
            CharsetUtil.US_ASCII);
    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        {@link #handleOversizedMessage(ChannelHandlerContext, HttpMessage)}
     *        will be called.
     */
    public HttpObjectAggregator(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    protected boolean isStartMessage(HttpObject msg) throws Exception {
        return msg instanceof HttpMessage;
    }

    @Override
    protected boolean isContentMessage(HttpObject msg) throws Exception {
        return msg instanceof HttpContent;
    }

    @Override
    protected boolean isLastContentMessage(HttpContent msg) throws Exception {
        return msg instanceof LastHttpContent;
    }

    @Override
    protected boolean isAggregated(HttpObject msg) throws Exception {
        return msg instanceof FullHttpMessage;
    }

    @Override
    protected boolean hasContentLength(HttpMessage start) throws Exception {
        return HttpHeaderUtil.isContentLengthSet(start);
    }

    @Override
    protected long contentLength(HttpMessage start) throws Exception {
        return HttpHeaderUtil.getContentLength(start);
    }

    @Override
    protected Object newContinueResponse(HttpMessage start) throws Exception {
        if (HttpHeaderUtil.is100ContinueExpected(start)) {
            return CONTINUE_LINE.duplicate().retain();
        } else {
            return null;
        }
    }

    @Override
    protected FullHttpMessage beginAggregation(HttpMessage start, ByteBuf content) throws Exception {
        assert !(start instanceof FullHttpMessage);

        HttpHeaderUtil.setTransferEncodingChunked(start, false);

        AggregatedFullHttpMessage ret;
        if (start instanceof HttpRequest) {
            ret = new AggregatedFullHttpRequest((HttpRequest) start, content, null);
        } else if (start instanceof HttpResponse) {
            ret = new AggregatedFullHttpResponse((HttpResponse) start, content, null);
        } else {
            throw new Error();
        }
        return ret;
    }

    @Override
    protected void aggregate(FullHttpMessage aggregated, HttpContent content) throws Exception {
        if (content instanceof LastHttpContent) {
            // Merge trailing headers into the message.
            ((AggregatedFullHttpMessage) aggregated).setTrailingHeaders(((LastHttpContent) content).trailingHeaders());
        }
    }

    @Override
    protected void finishAggregation(FullHttpMessage aggregated) throws Exception {
        // Set the 'Content-Length' header.
        aggregated.headers().set(
                HttpHeaderNames.CONTENT_LENGTH,
                String.valueOf(aggregated.content().readableBytes()));
    }

    @Override
    protected void handleOversizedMessage(final ChannelHandlerContext ctx, HttpMessage oversized) throws Exception {
        if (oversized instanceof HttpRequest) {
            // send back a 413 and close the connection
            ChannelFuture future = ctx.writeAndFlush(TOO_LARGE_LINE.duplicate().retain()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        logger.debug("Failed to send a 413 Request Entity Too Large.", future.cause());
                        ctx.close();
                    }
                }
            });

            // If the client started to send data already, close because it's impossible to recover.
            // If keep-alive is off and 'Expect: 100-continue' is missing, no need to leave the connection open.
            if (oversized instanceof FullHttpMessage ||
                    (!HttpHeaderUtil.is100ContinueExpected(oversized) && !HttpHeaderUtil.isKeepAlive(oversized))) {
                future.addListener(ChannelFutureListener.CLOSE);
            }

            // If an oversized request was handled properly and the connection is still alive
            // (i.e. rejected 100-continue). the decoder should prepare to handle a new message.
            HttpObjectDecoder decoder = ctx.pipeline().get(HttpObjectDecoder.class);
            if (decoder != null) {
                decoder.reset();
            }
        } else if (oversized instanceof HttpResponse) {
            ctx.close();
            throw new TooLongFrameException("Response entity too large: " + oversized);
        } else {
            throw new IllegalStateException();
        }
    }

    private abstract static class AggregatedFullHttpMessage extends DefaultByteBufHolder implements FullHttpMessage {
        protected final HttpMessage message;
        private HttpHeaders trailingHeaders;

        private AggregatedFullHttpMessage(HttpMessage message, ByteBuf content, HttpHeaders trailingHeaders) {
            super(content);
            this.message = message;
            this.trailingHeaders = trailingHeaders;
        }
        @Override
        public HttpHeaders trailingHeaders() {
            return trailingHeaders;
        }

        public void setTrailingHeaders(HttpHeaders trailingHeaders) {
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpVersion protocolVersion() {
            return message.protocolVersion();
        }

        @Override
        public FullHttpMessage setProtocolVersion(HttpVersion version) {
            message.setProtocolVersion(version);
            return this;
        }

        @Override
        public HttpHeaders headers() {
            return message.headers();
        }

        @Override
        public DecoderResult decoderResult() {
            return message.decoderResult();
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            message.setDecoderResult(result);
        }

        @Override
        public FullHttpMessage retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public FullHttpMessage retain() {
            super.retain();
            return this;
        }

        @Override
        public FullHttpMessage touch(Object hint) {
            super.touch(hint);
            return this;
        }

        @Override
        public FullHttpMessage touch() {
            super.touch();
            return this;
        }

        @Override
        public abstract FullHttpMessage copy();

        @Override
        public abstract FullHttpMessage duplicate();
    }

    private static final class AggregatedFullHttpRequest extends AggregatedFullHttpMessage implements FullHttpRequest {

        private AggregatedFullHttpRequest(HttpRequest request, ByteBuf content, HttpHeaders trailingHeaders) {
            super(request, content, trailingHeaders);
        }

        /**
         * Copy this object
         *
         * @param copyContent
         * <ul>
         * <li>{@code true} if this object's {@link #content()} should be used to copy.</li>
         * <li>{@code false} if {@code newContent} should be used instead.</li>
         * </ul>
         * @param newContent
         * <ul>
         * <li>if {@code copyContent} is false then this will be used in the copy's content.</li>
         * <li>if {@code null} then a default buffer of 0 size will be selected</li>
         * </ul>
         * @return A copy of this object
         */
        private FullHttpRequest copy(boolean copyContent, ByteBuf newContent) {
            DefaultFullHttpRequest copy = new DefaultFullHttpRequest(
                    protocolVersion(), method(), uri(),
                    copyContent ? content().copy() :
                        newContent == null ? Unpooled.buffer(0) : newContent);
            copy.headers().set(headers());
            copy.trailingHeaders().set(trailingHeaders());
            return copy;
        }

        @Override
        public FullHttpRequest copy(ByteBuf newContent) {
            return copy(false, newContent);
        }

        @Override
        public FullHttpRequest copy() {
            return copy(true, null);
        }

        @Override
        public FullHttpRequest duplicate() {
            DefaultFullHttpRequest duplicate = new DefaultFullHttpRequest(
                    protocolVersion(), method(), uri(), content().duplicate());
            duplicate.headers().set(headers());
            duplicate.trailingHeaders().set(trailingHeaders());
            return duplicate;
        }

        @Override
        public FullHttpRequest retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public FullHttpRequest retain() {
            super.retain();
            return this;
        }

        @Override
        public FullHttpRequest touch() {
            super.touch();
            return this;
        }

        @Override
        public FullHttpRequest touch(Object hint) {
            super.touch(hint);
            return this;
        }

        @Override
        public FullHttpRequest setMethod(HttpMethod method) {
            ((HttpRequest) message).setMethod(method);
            return this;
        }

        @Override
        public FullHttpRequest setUri(String uri) {
            ((HttpRequest) message).setUri(uri);
            return this;
        }

        @Override
        public HttpMethod method() {
            return ((HttpRequest) message).method();
        }

        @Override
        public String uri() {
            return ((HttpRequest) message).uri();
        }

        @Override
        public FullHttpRequest setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }
    }

    private static final class AggregatedFullHttpResponse extends AggregatedFullHttpMessage
            implements FullHttpResponse {
        private AggregatedFullHttpResponse(HttpResponse message, ByteBuf content, HttpHeaders trailingHeaders) {
            super(message, content, trailingHeaders);
        }

        /**
         * Copy this object
         *
         * @param copyContent
         * <ul>
         * <li>{@code true} if this object's {@link #content()} should be used to copy.</li>
         * <li>{@code false} if {@code newContent} should be used instead.</li>
         * </ul>
         * @param newContent
         * <ul>
         * <li>if {@code copyContent} is false then this will be used in the copy's content.</li>
         * <li>if {@code null} then a default buffer of 0 size will be selected</li>
         * </ul>
         * @return A copy of this object
         */
        private FullHttpResponse copy(boolean copyContent, ByteBuf newContent) {
            DefaultFullHttpResponse copy = new DefaultFullHttpResponse(
                    protocolVersion(), status(),
                    copyContent ? content().copy() :
                        newContent == null ? Unpooled.buffer(0) : newContent);
            copy.headers().set(headers());
            copy.trailingHeaders().set(trailingHeaders());
            return copy;
        }

        @Override
        public FullHttpResponse copy(ByteBuf newContent) {
            return copy(false, newContent);
        }

        @Override
        public FullHttpResponse copy() {
            return copy(true, null);
        }

        @Override
        public FullHttpResponse duplicate() {
            DefaultFullHttpResponse duplicate = new DefaultFullHttpResponse(protocolVersion(), status(),
                    content().duplicate());
            duplicate.headers().set(headers());
            duplicate.trailingHeaders().set(trailingHeaders());
            return duplicate;
        }

        @Override
        public FullHttpResponse setStatus(HttpResponseStatus status) {
            ((HttpResponse) message).setStatus(status);
            return this;
        }

        @Override
        public HttpResponseStatus status() {
            return ((HttpResponse) message).status();
        }

        @Override
        public FullHttpResponse setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }

        @Override
        public FullHttpResponse retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public FullHttpResponse retain() {
            super.retain();
            return this;
        }

        @Override
        public FullHttpResponse touch(Object hint) {
            super.touch(hint);
            return this;
        }

        @Override
        public FullHttpResponse touch() {
            super.touch();
            return this;
        }
    }
}
