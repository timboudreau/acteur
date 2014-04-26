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

package com.mastfrog.acteur.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.buffer.ByteBufAllocator;
import java.sql.ResultSet;
import java.util.concurrent.ExecutorService;

/**
 * Factory for ResultSetWriters so you don't have to ask for all of its
 * dependencies to be injected.
 *
 * @author Tim Boudreau
 */
@Singleton
public class ResultSetWriterFactory {

    private final ObjectMapper mapper;
    private final ByteBufAllocator alloc;
    private final ExecutorService svc;

    @Inject
    ResultSetWriterFactory(ObjectMapper mapper, ByteBufAllocator alloc, @Named(value = "background") ExecutorService svc) {
        this.mapper = mapper;
        this.alloc = alloc;
        this.svc = svc;
    }

    public ResultSetWriter create(ResultSet rs) {
        return new ResultSetWriter(rs, mapper, alloc, svc);
    }
}
