/*
 * The MIT License
 *
 * Copyright 2014 tim.
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
package com.mastfrog.acteur.sse;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.mastfrog.util.preconditions.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.util.CharsetUtil;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

/**
 * Receives objects representing server sent events, and publishes them to all
 * registered channels. Automatically used with SseActeur - just inject an
 * EventSink and use its publish method to publish events. In the case of per
 * user or per session EventSinks, write an Acteur that looks up (in a cache or
 * similar) the right EventSink, and include that in its state. Then use the
 * next one.
 *
 * @author Tim Boudreau
 */
@Singleton
public class EventSink {

    private final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    private final AtomicLong count = new AtomicLong();
    private final MessageRenderer ren;
    private final Set<Channel> channels = Sets.newConcurrentHashSet();
    private final Map<EventChannelName, List<Channel>> channelsForName = Maps.newConcurrentMap();
    private volatile boolean shutdown;
    private volatile Thread thread;
    private final ByteBufAllocator alloc;
    private final ApplicationControl ctrl;
    private final Runner runner = new Runner();
    private final Shutdown shutdownRun = new Shutdown();

    /**
     * Normally you will just ask for an instance to be injected into your
     * constructor.
     *
     * @param ren A message renderer
     * @param svc The executor service that messages are dequeued on and sent to
     * all open registered channels
     * @param alloc An allocator for byte buffers, bound by the framework
     * @param ctrl Used to handle any exceptions
     * @param reg Shutdown hook registry that allows this sink to mark itself as
     * shut down, cease sending messages and clean up after itself
     */
    @Inject
    protected EventSink(MessageRenderer ren, 
            @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService svc,
            ByteBufAllocator alloc, ApplicationControl ctrl, ShutdownHookRegistry reg) {
        this.ren = ren;
        this.alloc = alloc;
        this.ctrl = ctrl;
        reg.add(shutdownRun);
        svc.submit(runner);
    }

    /**
     * Publish an event
     *
     * @param name The name of the sub-channel to publish to
     * @param eventType The event type, which will be on the first line of the
     * event, e.g. <code>event: foo</code>.
     * @param message The message. If non-string, it will be encoded as JSON by
     * default
     * @return this
     */
    public EventSink publish(EventChannelName name, String eventType, Object message) {
        if (shutdown || channels.isEmpty()) {
            return this;
        }
        // make sure we use the first instance we were passed
        for (EventChannelName n : channelsForName.keySet()) {
            if (name.equals(n)) {
                name = n;
                break;
            }
        }
//        long id = name == null ? count.getAndIncrement() : name.nextId();
        Message msg = new Message(name, eventType, count.getAndIncrement(), message);
        messages.offer(msg);
        return this;
    }
    
    /**
     * Publish an event
     *
     * @param eventType The event type, which will be on the first line of the
     * event, e.g. <code>event: foo</code>.
     * @param message The message. If non-string, it will be encoded as JSON by
     * default
     * @return this
     */
    public EventSink publish(String eventType, Object message) {
        return publish((EventChannelName) null, eventType, message);
    }

    /**
     * Publish an event
     *
     * @param message The message. If non-string, it will be encoded as JSON by
     * default
     * @return this
     */
    public EventSink publish(Object message) {
        return publish((EventChannelName) null, message);
    }
    
    /**
     * Publish an event to a named channel
     *
     * @param name The name of the subchannel
     * @param message The message. If non-string, it will be encoded as JSON by
     * default
     * @return this
     */
    public EventSink publish(EventChannelName name, Object message) {
        Checks.notNull("message", message);
        if (shutdown || channels.isEmpty()) {
            return this;
        }
        // make sure we use the first instance we were passed
        for (EventChannelName n : channelsForName.keySet()) {
            if (name.equals(n)) {
                name = n;
                break;
            }
        }
        long id = name == null ? count.getAndIncrement() : name.nextId();
        Message msg = new Message(name, id, message);
        messages.offer(msg);
        return this;
    }

    /**
     * Register a channel which will receive events from this event sink.
     *
     * @param channel A channel
     * @return this
     */
    public EventSink register(Channel channel) {
        if (!shutdown && channel.isOpen()) {
            channels.add(channel);
            channel.closeFuture().addListener(remover);
        }
        return this;
    }

    public synchronized EventSink register(EventChannelName name, Channel channel) {
        if (!shutdown && channel.isOpen()) {
            channels.add(channel);
            List<Channel> chlls = channelsForName.get(name);
            if (chlls == null) {
                chlls = Lists.newCopyOnWriteArrayList(Arrays.asList(channel));
                channelsForName.put(name, chlls);
            } else {
                chlls.add(channel);
            }
            channel.closeFuture().addListener(new RemoveListener(name));
            register(channel);
        }
        return this;
    }

    private final RemoveListener remover = new RemoveListener();

    private final class RemoveListener implements ChannelFutureListener {

        private final EventChannelName name;

        public RemoveListener(EventChannelName name) {
            this.name = name;
        }

        public RemoveListener() {
            this(null);
        }

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            if (name != null) {
                List<Channel> chlls = channelsForName.get(name);
                if (chlls != null) {
                    chlls.remove(f.channel());
                    if (chlls.isEmpty()) {
                        channelsForName.remove(name);
                    }
                }
            }
            channels.remove(f.channel());
        }
    }

    public void clear() {
        channels.clear();
        messages.clear();
    }

    private ByteBuf toByteBuf(Message msg) {
        StringBuilder builder = new StringBuilder();
        if (msg.eventType != null) {
            builder.append("\nevent: ").append(msg.eventType);
        }
        String stringMessage = ren.toString(msg.message).replace("\n", "\ndata: "); //XXX support multiline
        builder.append("\nid: ").append(msg.id).append("-").append(msg.timestamp)
                .append("\ndata: ").append(stringMessage).append('\n').append('\n');
        return alloc.ioBuffer(builder.length()).writeBytes(builder.toString().getBytes(CharsetUtil.UTF_8));
    }

    private class Runner implements Runnable {

        @Override
        public void run() {
            synchronized (EventSink.class) {
                thread = Thread.currentThread();
            }
            final List<Message> msgs = new LinkedList<>();
            try {
                for (;;) {
                    try {
                        if (shutdown) {
                            break;
                        }
                        msgs.add(messages.take());
                        messages.drainTo(msgs);
                        if (channels.isEmpty() && channelsForName.isEmpty()) {
                            msgs.clear();
                            continue;
                        }
                        for (Message msg : msgs) {
                            EventChannelName target = msg.channelName;
                            ByteBuf buf = toByteBuf(msg);
                            if (target == null) {
                                for (Iterator<Channel> channelIterator = channels.iterator(); channelIterator.hasNext();) {
                                    if (shutdown) {
                                        return;
                                    }
                                    Channel channel = channelIterator.next();
                                    if (!channel.isOpen()) {
                                        channelIterator.remove();
                                    } else {
                                        try {
                                            ByteBuf toWrite = buf.duplicate().retain();
                                            channel.writeAndFlush(new DefaultHttpContent(toWrite));
                                        } catch (Exception e) {
                                            ctrl.internalOnError(e);
                                            channelIterator.remove();
                                        }
                                    }
                                }
                            } else {
                                List<Channel> targetChannels = channelsForName.get(target);
                                if (targetChannels != null) {
                                    for (Iterator<Channel> channelIterator = targetChannels.iterator(); channelIterator.hasNext();) {
                                        if (shutdown) {
                                            return;
                                        }
                                        Channel channel = channelIterator.next();
                                        if (!channel.isOpen()) {
                                            channelIterator.remove();
                                        } else {
                                            try {
                                                ByteBuf toWrite = buf.duplicate().retain();
                                                channel.writeAndFlush(new DefaultHttpContent(toWrite));
                                            } catch (Exception e) {
                                                ctrl.internalOnError(e);
                                                channelIterator.remove();
                                            }
                                        }
                                    }
                                }
                            }
                            buf.release();
                        }
                        msgs.clear();
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            } finally {
                msgs.clear();
                try {
                    for (Channel c : channels) {
                        c.close();
                    }
                } finally {
                    channels.clear();
                    synchronized (EventSink.this) {
                        thread = null;
                    }
                }
            }
        }
    }

    private class Shutdown implements Runnable {

        @Override
        public void run() {
            shutdown = true;
            Thread t;
            synchronized (EventSink.this) {
                t = thread;
                thread = null;
            }
            if (t != null && t.isAlive()) {
                t.interrupt();
            }
        }
    }

    private static final class Message {

        public final long timestamp = System.currentTimeMillis();
        public final String eventType;
        public final long id;
        public final Object message;
        public final EventChannelName channelName;

        public Message(long id, Object message) {
            this((EventChannelName) null, id, message);
        }

        public Message(EventChannelName name, long id, Object message) {
            this(name, null, id, message);
        }

        public Message(String eventType, long id, Object message) {
            this(null, eventType, id, message);
        }

        public Message(EventChannelName channelName, String eventType, long id, Object message) {
            this.channelName = channelName;
            this.eventType = eventType;
            this.id = id;
            this.message = message;
        }
    }
}
