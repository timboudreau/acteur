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
package com.mastfrog.acteur.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.marshallers.netty.NettyContentMarshallers;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class BusTest {

    static Throwable thrown;

    private void await(ChannelPromise prom) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        prom.addListener((Future<Void> future) -> {
            latch.countDown();
        });
        latch.await(12, TimeUnit.SECONDS);
    }

    @Test(timeout = 20000)
    public void testPubSub() throws Throwable {
        CH ch0 = new CH("zeroth");
        CH ch1 = new CH("first");
        CH ch2 = new CH("second");
        CH ch3 = new CH("third");
        CH ch4 = new CH("fourth");
        EmbeddedChannel c0 = new EmbeddedChannel(new Xid(ch0.name));
        c0.pipeline().addLast(ch0, ch0.out);
        EmbeddedChannel c1 = new EmbeddedChannel(new Xid(ch1.name));
        c1.pipeline().addLast(ch1, ch1.out);
        EmbeddedChannel c2 = new EmbeddedChannel(new Xid(ch2.name));
        c2.pipeline().addLast(ch2, ch2.out);
        EmbeddedChannel c3 = new EmbeddedChannel(new Xid(ch3.name));
        c3.pipeline().addLast(ch3, ch3.out);
        EmbeddedChannel c4 = new EmbeddedChannel(new Xid(ch4.name));
        c4.pipeline().addLast(ch4, ch4.out);
        assertTrue(c0.isOpen());
        assertTrue(c0.isActive());
        ChannelId first = new ChannelId("first");
        ChannelId second = new ChannelId("second");
        try (X x = new X()) {
            Bus bus = new Bus(x, NettyContentMarshallers.getDefault(new ObjectMapper()), ByteBufAllocator.DEFAULT, Executors.newCachedThreadPool(), new BusListener.Registry());
            assertTrue(bus.subscribe(c0, first).get());
            bus.subscribe(c1, first).get();
            bus.subscribe(c2, first).get();
            bus.subscribe(c3, first).get();
            bus.subscribe(c3, second).get();
            bus.subscribe(c4, second).get();

            assertTrue(bus.hasSubscribers(first));
            assertTrue(bus.hasSubscribers(second));

            ChannelPromise prom = bus.publish("hello", first, c0);
            await(prom);
            Thread.sleep(1000);
            ch1.assertLastMessageIs("hello");
            ch2.assertLastMessageIs("hello");
            ch3.assertLastMessageIs("hello");
            ch0.assertNoMessages();
            ch4.assertNoMessages();

            prom = bus.publish("goodbye", first, c0);
            await(prom);
            ch1.assertLastMessageIs("goodbye");
            ch2.assertLastMessageIs("goodbye");
            ch3.assertLastMessageIs("goodbye");
            ch0.assertNoMessages();
            ch4.assertNoMessages();

            prom = bus.publish("whatevs", first, c1);
            await(prom);
            ch0.assertLastMessageIs("whatevs");
            ch1.assertLastMessageIs("goodbye");
            ch2.assertLastMessageIs("whatevs");
            ch3.assertLastMessageIs("whatevs");
            ch4.assertNoMessages();

            prom = bus.publish("woohoo", second, c1);
            await(prom);
            ch0.assertLastMessageIs("whatevs");
            ch1.assertLastMessageIs("goodbye");
            ch2.assertLastMessageIs("whatevs");
            ch3.assertLastMessageIs("woohoo");
            ch4.assertLastMessageIs("woohoo");

            bus.unsubscribe(c2, first).get();
            prom = bus.publish("bye", first, c0);
            await(prom);
            ch0.assertLastMessageIs("whatevs");
            ch1.assertLastMessageIs("bye");
            ch3.assertLastMessageIs("bye");
            ch2.assertLastMessageIs("whatevs");

            c3.close().sync();
            prom = bus.publish("gone", first, c0);
            await(prom);
            ch2.assertLastMessageIs("whatevs");
            ch1.assertLastMessageIs("gone");
            ch3.assertLastMessageIs("bye");
        }
        if (thrown != null) {
            throw thrown;
        }
    }

    static class CH extends SimpleChannelInboundHandler<Object> {

        List<String> msgs = new ArrayList<>();
        private final String name;
        CH2 out = new CH2();

        CH(String name) {
            this.name = name;
        }

        class CH2 extends ChannelOutboundHandlerAdapter {

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                channelRead0(ctx, msg);
                super.write(ctx, msg, promise);
            }

        }

        @Override
        public boolean acceptInboundMessage(Object msg) throws Exception {
            return true;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            thrown = cause;
            super.exceptionCaught(ctx, cause);
        }

        void assertLastMessageIs(String s) {
            assertFalse(name + ": No messages received", msgs.isEmpty());
            assertEquals(name + ": Wrong last message", s, msgs.get(msgs.size() - 1));
        }

        void assertLastMessageNot(String s) {
            if (msgs.isEmpty()) {
                return;
            }
            assertNotEquals(name + ": Did receive " + s, s, msgs.get(msgs.size() - 1));
        }

        void assertNoMessages() {
            assertTrue(name + ": has messages", msgs.isEmpty());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            String s = null;
            if (msg instanceof String) {
                s = (String) msg;
            } else if (msg instanceof WebSocketFrame) {
                WebSocketFrame f = (WebSocketFrame) msg;
                s = f.content().readCharSequence(f.content().readableBytes(), CharsetUtil.UTF_8).toString();
            }
            if (s == null) {
                throw new IllegalArgumentException("Huh? " + msg);
            }
            msgs.add(s);
        }
    }

    private final class X extends ShutdownHookRegistry implements AutoCloseable {

        @Override
        public void close() {
            super.runShutdownHooks();
        }
    }

    static class Xid implements io.netty.channel.ChannelId {

        private final String s;

        public Xid(String s) {
            this.s = s;
        }

        @Override
        public String asShortText() {
            return s;
        }

        @Override
        public String asLongText() {
            return s;
        }

        public String toString() {
            return s;
        }

        @Override
        public int compareTo(io.netty.channel.ChannelId o) {
            return asShortText().compareTo(o.asShortText());
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.s);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Xid other = (Xid) obj;
            if (!Objects.equals(this.s, other.s)) {
                return false;
            }
            return true;
        }

    }
}
