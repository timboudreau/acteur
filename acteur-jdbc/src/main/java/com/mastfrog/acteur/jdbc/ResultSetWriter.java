/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.acteur.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.util.CharsetUtil;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Writes out a result set as JSON, calling itself back one row at a time so
 * threads are maximally available to serve other requests.
 * <p/>
 * Contains a workaround for 
 * <a href="https://github.com/netty/netty/issues/2415">this Netty issue</a>
 * for the time being, ensuring writes happen in the expected sequence.
 * <p/>
 * For ease of construction, ask for a <a href="ResultSetWriterFactory.html">ResultSetWriterFactory</a>
 * to be injected, or use <a href="ResultSetWriterActeur.html">ResultSetWriterActeur</a>.
 *
 * @author Tim Boudreau
 */
public class ResultSetWriter implements ChannelFutureListener {

    private final ResultSet resultSet;
    private volatile boolean first = true;
    private final ObjectMapper mapper;
    private final ByteBufAllocator alloc;
    private final ExecutorService svc;

    @Inject
    public ResultSetWriter(ResultSet resultSet, ObjectMapper mapper, ByteBufAllocator alloc, @Named(/*ServerModule.BACKGROUND_THREAD_POOL_NAME*/ "background") ExecutorService svc) {
        this.resultSet = resultSet;
        this.mapper = mapper;
        this.alloc = alloc;
        this.svc = svc;
    }

    private volatile int entryCount;

    @Override
    public synchronized void operationComplete(ChannelFuture f) {
        if (entryCount > 0) {
            // See https://github.com/netty/netty/issues/2415 for why this is needed
            final ChannelFuture ff = f;
            svc.submit(new Runnable() {

                @Override
                public void run() {
                    ResultSetWriter.this.operationComplete(ff);
                }
            });
            return;
        }
        try {
            entryCount++;
            StringBuilder sb = new StringBuilder();
            boolean done = false;
            if (resultSet.isClosed()) {
                f.channel().close();
                return;
            }
            boolean wasFirst = first;
            if (first) {
                sb.append("[");
                first = false;
            }
            if (resultSet.next()) {
                if (!wasFirst) {
                    sb.append(",\n");
                }
                ResultSetMetaData md = resultSet.getMetaData();
                Map<String, Object> data = new LinkedHashMap<>();
                int count = md.getColumnCount();
                for (int i = 0; i < count; i++) {
                    String name = md.getColumnName(i + 1);
                    Object value = resultSet.getObject(i + 1);
                    data.put(name, value);
                }
                sb.append(mapper.writeValueAsString(data));
            } else {
                done = true;
                sb.append("]\n");
            }
            if (sb.length() > 0) {
                // inserting this sleep cures the problem
                // Thread.sleep(200);
                ByteBuf buf = alloc.buffer().writeBytes(sb.toString().getBytes(CharsetUtil.UTF_8));
                f = f.channel().writeAndFlush(new DefaultHttpContent(buf));
            }
            if (done) {
                f = f.channel().writeAndFlush(new DefaultLastHttpContent()).addListener(CLOSE);
            } else {
                f.addListener(this);
            }
        } catch (SQLException | JsonProcessingException e) {
            try {
                f.channel().close();
            } finally {
                try {
                    resultSet.close();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
            }
            Exceptions.chuck(e);
        } finally {
            first = false;
            entryCount--;
        }
    }

}
