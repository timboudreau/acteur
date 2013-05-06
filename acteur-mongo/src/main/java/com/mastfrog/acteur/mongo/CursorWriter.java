package com.mastfrog.acteur.mongo;

import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.ResponseWriter;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.CharsetUtil;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class CursorWriter extends ResponseWriter {

    private final DBCursor cursor;
    private final boolean closeConnection;
    private final MapFilter filter;

    @Inject
    public CursorWriter(DBCursor cursor, Event evt, Provider<MapFilter> filter) {
        this(cursor, !evt.isKeepAlive(), filter);
    }

    public CursorWriter(DBCursor cursor, boolean closeConnection, Provider<MapFilter> filter) {
        this.cursor = cursor;
        this.closeConnection = closeConnection;
        MapFilter mf;
        try {
            mf = filter.get();
        } catch (IllegalStateException ex) {
            mf = null;
        }
        this.filter = mf;
    }

    @Override
    public Status write(Event evt, Output out, int iter) throws Exception {
        try {
            if (iter == 0) {
                out.write("[\n");
            }
            boolean hasNext = cursor.hasNext();
            if (hasNext) {
                DBObject ob = cursor.next();
                Map<String, Object> m = ob.toMap();
                if (m.get("_id") instanceof ObjectId) {
                    ObjectId oid = (ObjectId) m.get("_id");
                    m.put("_id", oid.toString());
                }
                System.out.println("Write object " + iter);
                if (filter != null) {
                    out.writeObject(filter.filter(m));
                } else {
                    out.writeObject(m);
                }
                hasNext = cursor.hasNext();
            }
            if (!hasNext) {
                out.write("\n]\n");
                cursor.close();
                if (closeConnection) {
//                    out.future().addListener(ChannelFutureListener.CLOSE);
                }
            } else {
                out.write(",\n");
            }
            return hasNext ? Status.NOT_DONE : Status.DONE;
        } catch (Exception e) {
            try {
                out.channel().close();
            } finally {
                cursor.close();
            }
            throw e;
        }
    }

    @ImplementedBy(MapFilterImpl.class)
    public static interface MapFilter {

        public Map<String, Object> filter(Map<String, Object> m);
    }

    public static final MapFilter NO_FILTER = new MapFilterImpl();

    static class MapFilterImpl implements MapFilter {

        public Map<String, Object> filter(Map<String, Object> m) {
            return m;
        }
    }
}
