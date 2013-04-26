package com.mastfrog.acteur.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.CharsetUtil;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class CursorWriter implements ChannelFutureListener {

    private final DBCursor cursor;
    private final boolean closeConnection;
    private volatile boolean first = true;
    private final ObjectMapper mapper;
    private final MapFilter filter;

    @Inject
    public CursorWriter(DBCursor cursor, Event evt, ObjectMapper mapper, MapFilter filter) {
        this(cursor, !evt.isKeepAlive(), mapper, filter);
    }

    public CursorWriter(DBCursor cursor, boolean closeConnection, ObjectMapper mapper, MapFilter filter) {
        this.cursor = cursor;
        this.closeConnection = closeConnection;
        this.filter = filter;
        this.mapper = mapper;
    }

    void finish(ChannelFuture future) {
        cursor.close();
        future = future.channel().write(Unpooled.wrappedBuffer("\n]\n".getBytes(CharsetUtil.UTF_8)));
        if (closeConnection) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        if (!future.channel().isOpen()) {
            cursor.close();
            return;
        }
        if (first) {
            first = false;
            future = future.channel().write(Unpooled.wrappedBuffer("[\n".getBytes(CharsetUtil.UTF_8)));
        }
        if (cursor.hasNext()) {
            try {
                DBObject ob = cursor.next();
                future = future.channel().write(Unpooled.wrappedBuffer(mapper.writeValueAsBytes(filter.filter(ob.toMap()))));
                if (cursor.hasNext()) {
                    future = future.channel().write(Unpooled.wrappedBuffer(",\n".getBytes(CharsetUtil.UTF_8)));
                    future.addListener(this);
                } else {
                    finish(future);
                }
            } catch (Exception e) {
                try {
                    future.channel().close();
                } finally {
                    try {
                        cursor.close();
                    } finally {
                        throw e;
                    }
                }
            }
        } else {
            finish(future);
        }
    }
    
    @ImplementedBy(MapFilterImpl.class)
    public static interface MapFilter {
        public Map<String,Object> filter(Map<String,Object> m);
    }
    
    static class MapFilterImpl {
        public Map<String,Object> filter(Map<String,Object> m) {
            return m;
        }
    }
}
