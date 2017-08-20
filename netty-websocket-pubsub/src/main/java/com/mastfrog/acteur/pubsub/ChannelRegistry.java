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

import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Bi-directionally maps sets of channels to sets of identifiers (such as a
 * subscriber or channel id). The one requirement is that the identifier objects
 * be immutable and implement equals/hashCode() correctly. Strings will do.
 *
 * @author Tim Boudreau
 */
@Singleton
final class ChannelRegistry<Identifier> {

    private final Map<Identifier, Set<Channel>> channelsForId
            = CollectionUtils.concurrentSupplierMap(() -> ConcurrentHashMap.newKeySet(50));

    private final Map<Channel, Set<Identifier>> idsForChannel
            = CollectionUtils.concurrentSupplierMap(() -> ConcurrentHashMap.newKeySet(50));

    private final ChannelFutureListener remover = new ClosureRemover();
    private final ExecutorService mutationThread;

    @Inject
    ChannelRegistry(ShutdownHookRegistry reg) {
        // Though we are using concurrent data structures, they are not atomic
        // when used in combination as we are here.  So confine mutations to
        // a single thread to avoid out-of-order surprises.
        mutationThread = Executors.newFixedThreadPool(1);
        reg.add(() -> {
            idsForChannel.keySet().forEach((channel) -> {
                try {
                    channel.close().sync();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            channelsForId.clear();
            idsForChannel.clear();
        });
        reg.add(mutationThread);
    }

    public Future<Boolean> register(Identifier id, Channel channel) {
        Set<Identifier> ids = idsForChannel.get(channel);
        if (ids.contains(id)) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            result.complete(true);
            return result;
        }
        return mutationThread.submit(() -> {
            Set<Channel> channels = channelsForId.get(id);
            boolean result = channels.add(channel);
            if (result) {
                idsForChannel.get(channel).add(id);
                channel.closeFuture().addListener(remover);
            }
            return result;
        });
    }

    public Set<Channel> channels(Identifier id) {
        return new HashSet<>(channelsForId.get(id));
    }

    public Set<Identifier> idsForChannel(Channel channel) {
        return new HashSet<>(idsForChannel.get(channel));
    }

    public Set<Channel> allChannels() {
        return Collections.unmodifiableSet(new HashSet<>(idsForChannel.keySet()));
    }

    public Set<Identifier> allIds() {
        return Collections.unmodifiableSet(new HashSet<>(channelsForId.keySet()));
    }

    public Future<Boolean> unsubscribe(Identifier from, Channel channel) {
        return mutationThread.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Set<Channel> channelsFor = channelsForId.get(from);
                boolean result = channelsFor.remove(channel);
                Set<Identifier> idsFor = idsForChannel.get(channel);
                result |= idsFor.remove(from);
                return result;
            }
        });
    }

    public void unregister(Channel channel) {
        mutationThread.submit(() -> {
            Map<Identifier, Set<Channel>> alsoRemove = new HashMap<>();
            Set<Identifier> ids = idsForChannel.get(channel);
            if (ids != null) {
                ids.forEach((id) -> {
                    Set<Channel> channels = channelsForId.get(id);
                    channels.remove(channel);
                    if (channels.isEmpty()) {
                        alsoRemove.put(id, channels);
                    }
                });
            }
            idsForChannel.remove(channel);
            alsoRemove.entrySet().stream().filter((e) -> (e.getValue().isEmpty())).forEachOrdered((e) -> {
                channelsForId.remove(e.getKey());
            });
            channel.closeFuture().removeListener(remover);
        });
    }

    void gc() {
        // Because we are using SupplierMap, we can wind up populating maps
        // with unused lists
        mutationThread.submit(() -> {
            Set<Identifier> idsToRemove = new HashSet<>();
            Set<Channel> channelsToRemove = new HashSet<>();
            channelsForId.entrySet().stream().filter((e) -> (e.getValue().isEmpty())).forEachOrdered((e) -> {
                idsToRemove.add(e.getKey());
            });
            idsForChannel.entrySet().stream().filter((e) -> (e.getValue().isEmpty())).forEachOrdered((e) -> {
                channelsToRemove.add(e.getKey());
            });
            for (Identifier i : idsToRemove) {
                channelsForId.remove(i);
            }
            for (Channel c : channelsToRemove) {
                idsForChannel.remove(c);
            }
        });
    }

    private final class ClosureRemover implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            unregister(future.channel());
        }
    }
}
