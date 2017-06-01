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

import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpContent;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import io.netty.util.CharsetUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith(SseApp.Module.class)
public class SseTest {

    @Test(timeout = 90000)
    public void test(TestHarness harn) throws Throwable {
        long when = System.currentTimeMillis();
        System.err.println("PORT " + harn.getPort());
        System.err.flush();
        harn.get("/foo").setTimeout(Duration.ofSeconds(60)).log().go().assertStatus(NOT_FOUND);
        final StringBuilder content = new StringBuilder();
        final CallResult[] res = new CallResult[1];
        final AtomicInteger count = new AtomicInteger();
        res[0] = harn.get("/sse").on(StateType.ContentReceived, new Receiver<HttpContent>() {

            @Override
            public void receive(HttpContent c) {
                if (c == null) {
                    return;
                }
                ByteBuf buf = c.content();
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                String s = new String(bytes, CharsetUtil.UTF_8);
                content.append(s);
                int ct = count.incrementAndGet();
                if (ct > 5) {
                    res[0].cancel();
                }
            }
        }).setTimeout(Duration.ofSeconds(10)).go();
        try {
            res[0].await();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        List<String> ids = new ArrayList<>();
        List<String> items = new ArrayList<>();
        for (String line : content.toString().split("\n")) {
            if (line.startsWith("id: ")) {
                ids.add(line.substring(4));
            }
            if (line.startsWith("data: ")) {
                items.add(line.substring(6));
            }
        }
        int last = -1;
        for (String s : ids) {
            Matcher m = Pattern.compile("(\\d+)\\-(\\d*)$").matcher(s);
            assertTrue(m.find());
            int id = Integer.parseInt(m.group(1));
            long ts = Long.parseLong(m.group(2));
            assertTrue(id > last);
            assertTrue(ts > when);
            if (last != -1) {
                assertEquals(last + 1, id);
            }
            last = id;
        }
        last = -1;
        for (String item : items) {
            Matcher m = Pattern.compile("hello (\\d+)").matcher(item);
            assertTrue(m.find());
            int val = Integer.parseInt(m.group(1));
            if (last != -1) {
                assertEquals(last + 1, val);
            }
            last = val;
        }
    }
}
