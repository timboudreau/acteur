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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.BuiltInPageAnnotationHandler;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.DeferredComputationResult;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.errors.ErrorResponse;
import com.mastfrog.acteur.errors.ExceptionEvaluator;
import com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry;
import com.mastfrog.acteur.header.entities.BasicCredentials;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.COOKIE_B;
import static com.mastfrog.acteur.headers.Headers.X_FORWARDED_PROTO;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.request.HttpProtocolRequest;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.ErrorHandler;
import com.mastfrog.acteur.util.HttpMethod;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteurbase.ActeurBaseModule;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.InjectionInfo;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.thread.ConventionalThreadSupplier;
import com.mastfrog.giulius.thread.ExecutorServiceBuilder;
import com.mastfrog.giulius.thread.ThreadModule;
import com.mastfrog.giulius.thread.ThreadPoolType;
import com.mastfrog.graal.annotation.Expose;
import com.mastfrog.graal.annotation.Expose.MethodInfo;
import com.mastfrog.graal.annotation.ExposeMany;
import com.mastfrog.marshallers.netty.NettyContentMarshallers;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.Protocols;
import com.mastfrog.util.codec.Codec;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import static com.mastfrog.util.preconditions.Checks.nonNegative;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.strings.Strings;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMaxBytesRecvByteBufAllocator;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.AsciiString;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import org.netbeans.validation.api.InvalidInputException;

/**
 * Guice module for creating a server; also defines settings keys which can
 * affect behavior.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("deprecation")
@ExposeMany({
    @Expose(type = "com.fasterxml.jackson.databind.ObjectMapper", methods = @MethodInfo(parameterTypes = {}))}
)
public class ServerModule<A extends Application> extends AbstractModule {

    /**
     * Header which, when attached to a response, bypasses the compresser - used
     * in an application which supports HTTP compression but which, for some
     * resources, will serve pre-compressed data.
     */
    public static final AsciiString X_INTERNAL_COMPRESS = new AsciiString("X-Internal-Compress");
    /**
     * Header which, when attached to a response, bypasses the compresser - used
     * in an application which supports HTTP compression but which, for some
     * resources, will serve pre-compressed data.
     */
    public static final HeaderValueType<CharSequence> X_INTERNAL_COMPRESS_HEADER = Headers.header(X_INTERNAL_COMPRESS);
    /**
     * Sets the HTTP compression level from 0 to 9, 0 meaning no compression, 9
     * meaning maximum compression; the default is 6.
     */
    public static final String HTTP_COMPRESSION_LEVEL = "compression.level";
    /**
     * Sets the size of the history buffer for compression - should be in the
     * range 9 to 15, higher numbers meaning better commpression at the cost of
     * memory.
     */
    public static final String HTTP_COMPRESSION_WINDOW_BITS = "compression.window.bits";
    /**
     * Sets the amount of memory to use for compression state, from 1 to 9,
     * higher numbers using more memory but getting better and faster
     * compression.
     */
    public static final String HTTP_COMPRESSION_MEMORY_LEVEL = "compression.memory.level";

    /**
     * Sets the compression threshold <i>for responses which have their
     * Content-Length set</i> - Netty's HttpContentCompressor decides to act
     * before HTTP chunks are seen, so chunked responses will use the default
     * behavior. If you are sending raw ByteBufs or attaching the bytes in
     * <code>Acteur.ok(bytes)</code> or
     * <code>Acteur.reply(responseCode, bytes)</code> then this will disable
     * compression for messages smaller than this threshold (for small messages,
     * compression can make them bigger).
     */
    public static final String HTTP_COMPRESSION_THRESHOLD = "compression.threshold";

    /**
     * If true, the response compressor will check the response's Content-Type
     * header and avoid compressing responses with media types that are
     * pre-compressed or liable to be made larger by gzip or deflate
     * compression, such as mpeg or jpeg. This is off by default, since it adds
     * a small amount of overhead to each response.
     */
    public static final String HTTP_COMPRESSION_CHECK_RESPONSE_CONTENT_TYPE = "compression.check.content.type";

    /**
     * Default value for settings key <code>compression.level</code>
     *
     * @see com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_LEVEL
     */
    public static final int DEFAULT_COMPRESSION_LEVEL = 6;
    /**
     * Default value for settings key <code>compression.window.bits</code>
     *
     * @see com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_WINDOW_BITS
     */
    public static final int DEFAULT_COMPRESSION_WINDOW_BITS = 15;
    /**
     * Default value for settings key <code>compression.memory.level</code>
     *
     * @see
     * com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION_MEMORY_LEVEL
     */
    public static final int DEFAULT_COMPRESSION_MEMORY_LEVEL = 8;
    /**
     * Default value for settings key <code>compression.threshold</code>
     *
     * @see com.mastfrog.acteur.server.ServerModule.HTTP_THRESHOLD
     */
    public static final int DEFAULT_COMPRESSION_THRESHOLD = 256;
    /**
     * If set in settings, only this IP address will be bound when starting the
     * server.
     */
    public static final String SETTINGS_KEY_BIND_ADDRESS = "server.bind.interface.address";
    /**
     * The base path for all URLs in the application, allowing it to be
     * "mounted" on a URL path - so a server such as NginX can reverse proxy it
     * under the path <code>/foo</code>, and that is transparent to application
     * code, and is used when generating redirect URLs.
     */
    public static final String SETTINGS_KEY_BASE_PATH = "basepath";
    /**
     * The host name to use in URLs generated for redirects and similar, by
     * PathFactory.
     */
    public static final String SETTINGS_KEY_URLS_HOST_NAME = "hostname";

    /**
     * The external port if running behind a proxy, for use when PathFactory
     * generates redirect URLs.
     */
    public static final String SETTINGS_KEY_URLS_EXTERNAL_PORT = "external.port";

    /**
     * The secure port if running behind a proxy, for use when PathFactory
     * generates redirect URLs.
     */
    public static final String SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT = "external.secure.port";

    /**
     * Whether or not methods on PathFactory which do not take a protocol or
     * secure parameter should generate secure or insecure URLs.
     */
    public static final String SETTINGS_KEY_GENERATE_SECURE_URLS = "secure.urls";

    /**
     * If set to true (the default), turn off Netty's leak detector.
     */
    public static final String SETTINGS_KEY_DISABLE_LEAK_DETECTOR = "disable.leak.detector";
    public static final boolean DEFAULT_DISABLE_LEAK_DETECTOR = true;

    /**
     * Render stack traces into the response if an exception is thrown. Defaults
     * to true. Defaults to true unless Guice's stage is production, but can be
     * overridden with this property.
     */
    public static final String SETTINGS_KEY_RENDER_STACK_TRACES = "render.stack.traces";

    /**
     * URLs are generated using the host from InetAddress.getLocalHostName().
     * Note that the result of this may be quite unpredictable..
     */
    public static final String SETTINGS_KEY_GENERATE_URLS_WITH_INET_ADDRESS_GET_LOCALHOST
            = "urls.use.inetaddress.localhost";

    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests.
     */
    public static final String BACKGROUND_THREAD_POOL_NAME = "background";
    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests.
     */
    public static final String WORKER_THREAD_POOL_NAME = "workers";
    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests, but wrappered so that all runnables are run within the
     * application's request scope and have whatever context they were submitted
     * with.
     */
    public static final String SCOPED_WORKER_THREAD_POOL_NAME = "scopedWorkers";
    /**
     * Name of the &#064;Named parameter that should be used in an annotation if
     * you want Guice to inject the specific thread pool used for processing
     * requests, but wrappered so that all runnables are run within the
     * application's request scope and have whatever context they were submitted
     * with.
     */
    public static final String SCOPED_BACKGROUND_THREAD_POOL_NAME = "scopedBackground";

    /**
     * Property name for setting which byte buffer allocator Netty uses (heap,
     * direct, pooled).
     */
    public static final String BYTEBUF_ALLOCATOR_SETTINGS_KEY = "acteur.bytebuf.allocator";
    /**
     * Property value for telling the server to use the direct byte buffer
     * allocator (non-heap).
     */
    public static final String DIRECT_ALLOCATOR = "direct";
    /**
     * Property value for telling the server to use the heap byte buffer
     * allocator.
     */
    public static final String HEAP_ALLOCATOR = "heap";
    /**
     * Property value for telling the server to use the pooled byte buffer
     * allocator.
     */
    public static final String POOLED_ALLOCATOR = "pooled";
    /**
     * Property value for telling the server to use the pooled byte buffer
     * allocator with custom settings.
     */
    public static final String CUSTOMIZED_POOLED_ALLOCATOR = "pooled-custom";
    /**
     * Property value for telling the server to use the heap or direct byte
     * buffer allocator as decided by Netty's PlatformDependent class.
     */
    public static final String DIRECT_OR_HEAP_BY_PLATFORM = "directOrHeap";
    /**
     * The default allocator to use if none is specified
     */
    public static final String DEFAULT_ALLOCATOR = POOLED_ALLOCATOR;
    /**
     * Settings key for the number of worker threads to use.
     */
    public static final String WORKER_THREADS = "workers";
    /**
     * Number of event threads
     */
    public static final String EVENT_THREADS = "eventThreads";
    /**
     * Number of background thread pool threads. The background thread pool is
     * used by a few things which chunk responses.
     */
    @Deprecated
    public static final String BACKGROUND_THREADS = "backgroundThreads";
    /**
     * The port to run on
     */
    public static final String PORT = "port";

    /**
     * Settings key for enabling HTTP compression.
     */
    public static final String HTTP_COMPRESSION = "httpCompression";

    /**
     * Settings key for the maximum content length.
     */
    public static final String MAX_CONTENT_LENGTH = "maxContentLength";
    /**
     * Guice binding for
     * <code>&#064;Named(DELAY_EXECUTOR) ScheduledExecutorService</code> to get
     * a scheduled executor service which shares a ThreadFactory with the worker
     * thread pool.
     */
    public static final String DELAY_EXECUTOR = "delayExecutor";
    /**
     * Number of threads to process delayed responses (see Acteur.setDelay()).
     * These threads are typically not busy and can be 1-2 threads.
     */
    public static final String SETTINGS_KEY_DELAY_THREAD_POOL_THREADS = "delay.response.threads";
    /**
     * The default number of delay threads.
     */
    private static final int DEFAULT_DELAY_THREADS = 2;

    /**
     * If true, the return value of Event.remoteAddress() will prefer the
     * headers X-Forwarded-For or X-Real-IP if present, so that running an
     * acteur application behind a reverse proxy does not mask the actual IP
     * address.
     */
    public static final String SETTINGS_KEY_DECODE_REAL_IP = "decodeRealIP";
    /**
     * Settings key if true, do CORS responses on OPTIONS requests.
     */
    public static final String SETTINGS_KEY_CORS_ENABLED = "cors.enabled";
    /**
     * If true (the default), a ForkJoinPool will be used for dispatching work
     * to acteurs; if not, a fixed thread ExecutorService will be used. The
     * default is correct for most appliations; applications which require an
     * extremely small memory footprint (7-10Mb) will reduce their memory
     * requirements under load by turning this off.
     */
    public static final String SETTINGS_KEY_USE_FORK_JOIN_POOL = "acteur.fork.join";

    /**
     * If the default support for CORS requests is enabled, this is the max age
     * in minutes that the browser should regard the response as valid.
     */
    public static final String SETTINGS_KEY_CORS_MAX_AGE_MINUTES = "cors.max.age.minutes";

    /**
     * Guice #&064;Named binding for objects you want in the context for every
     * request, which can be replaced by ones provided to the context by
     * Acteurs. This allows you to have always-there default instances of
     * things, which can be replaced by ones customized by acteurs that handle
     * the request. Example: A default mongodb cursor control object which
     * specifies timeout, and which can be copied and customized.
     */
    public static final String GUICE_BINDING_DEFAULT_CONTEXT_OBJECTS = "default.context.objects";
    /**
     * If the default support for CORS requests is enabled, this is the value of
     * what hosts the response is valid for (what sites can use scripts from
     * this server without the browser blocking them). The default is *.
     */
    public static final String SETTINGS_KEY_CORS_ALLOW_ORIGIN = "cors.allow.origin";

    /**
     * If the default support for CORS requests is enabled, or for requests
     * annotated with &064;CORS that do not specify this, this is the value of
     * what hosts the response is valid for (what sites can use scripts from
     * this server without the browser blocking them). The default is *.
     */
    public static final String SETTINGS_KEY_CORS_ALLOW_HEADERS = "cors.allow.headers";
    /**
     * If the default support for CORS requests is enabled, use this instead of
     * the default cors allow headers string.
     */
    public static final String SETTINGS_KEY_CORS_REPLACE_ALLOW_HEADERS = "cors.replace.allow.headers";

    /**
     * If the default support for CORS requests is enabled, this is the value
     * for the <code>Access-Control-Allow-Credentials</code> header.
     */
    public static final String SETTINGS_KEY_CORS_ALLOW_CREDENTIALS = "cors.allow.credentials";
    /**
     * If the default support for CORS requests is enabled, this is the value of
     * what hosts the response is valid for (what sites can use scripts from
     * this server without the browser blocking them). The default is *.
     */
    public static final String SETTINGS_KEY_CORS_CACHE_CONTROL_MAX_AGE = "cors.cache.control.max.age.days";

    /**
     * Default value for @link(ServerModule.SETTINGS_KEY_CORS_ENABLED}.
     */
    public static final boolean DEFAULT_CORS_ENABLED = true;
    /**
     * Default value for @link(ServerModule.SETTINGS_KEY_CORS_MAX_AGE_MINUTES}.
     */
    public static final long DEFAULT_CORS_MAX_AGE_MINUTES = 5;
    /**
     * Default value for
     * @link(ServerModule.SETTINGS_KEY_CORS_ALLOW_CREDENTIALS}.
     */
    public static final boolean DEFAULT_CORS_ALLOW_CREDENTIALS = true;
    /**
     * Default value for @link(ServerModule.SETTINGS_KEY_CORS_ALLOW_ORIGIN}.
     */
    public static final String DEFAULT_CORS_ALLOW_ORIGIN = "*";
    /**
     * Settings key for the SSL engine to use - the name of one of the constants
     * on SslProvider.
     */
    public static final String SETTINGS_KEY_SSL_ENGINE = "ssl.engine";
    /**
     * Determine if the application should exit if an exception is thrown when
     * binding the server socket (usually because the port is in use). The
     * default is true, but in cases where multiple servers are started in one
     * JVM and the failure of one should not cause the JVM to exit, it can be
     * set to false and the JVM will continue running if there are any live
     * non-daemon threads.
     */
    public static final String SETTINGS_KEY_SYSTEM_EXIT_ON_BIND_FAILURE = "system.exit.on.bind.failure";

    /**
     * If enabled, serve HTTPS by default.
     */
    public static final String SETTINGS_KEY_SSL_ENABLED = "ssl.enabled";

    /**
     * If enabled, turn on websocket support for the server process.
     */
    public static final String SETTINGS_KEY_WEBSOCKETS_ENABLED = "websocket.enabled";
    /**
     * Low level socket option for outbound connections; default value is true,
     * disabling Nagle's algorithm.
     */
    public static final String SETTINGS_KEY_SOCKET_TCP_NODELAY = "acteur.outbound.socket.tcp.nodelay";
    /**
     * Low level socket option for outbound connections.
     */
    public static final String SETTINGS_KEY_SOCKET_CONNECT_TIMEOUT_MILLIS = "acteur.inbound.socket.connect.timeout.millis";
    /**
     * Low level socket option for outbound connections.
     */
    public static final String SETTINGS_KEY_SOCKET_MAX_MESSAGES_PER_READ = "acteur.inbound.socket.max.messages.per.read";
    /**
     * Low level socket option for outbound connections.
     */
    public static final String SETTINGS_KEY_SOCKET_MAX_MESSAGES_PER_INDIVIDUAL_READ = "acteur.inbound.socket.max.messages.per.individual.read";
    /**
     * Low level socket option for outbound connections.
     */
    public static final String SETTINGS_KEY_SOCKET_SO_RCVBUF = "acteur.inbound.socket.rcvbuf.size";
    /**
     * Low level socket option for outbound connections.
     */
    public static final String SETTINGS_KEY_SOCKET_SO_SNDBUF = "acteur.outbound.socket.sndbuf.size";
    /**
     * Low level socket option for outbound connections.
     */
    public static final String SETTINGS_KEY_SOCKET_WRITE_SPIN_COUNT = "acteur.outbound.socket.write.spin.count";
    /**
     * Default value for TCP_NODELAY for outbound connections.
     */
    public static final boolean DEFAULT_TCP_NODELAY = true;

    public static final boolean DEFAULT_WEBSOCKET_ENABLED = false;

    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_CACHE_ALIGNMENT = "custom.alloc.cache.alignment";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_USE_CACHE_ALL_THREADS = "custom.alloc.use.cache.all.threads";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_NORMAL_CACHE_SIZE = "custom.alloc.normal.cache.size";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_SMALL_CACHE_SIZE = "custom.alloc.small.cache.size";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_TINY_CACHE_SIZE = "custom.alloc.tiny.cache.size";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_MAX_ORDER = "custom.alloc.max.order";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_PAGE_SIZE = "custom.alloc.page.size";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_NUM_DIRECT_ARENAS = "custom.alloc.num.direct.arenas";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_NUM_HEAP_ARENAS = "custom.alloc.num.heap.arenas";
    /**
     * Fine tuning for using customized pooled byte buf allocator. Only relevant
     * if you set BYTEBUF_ALLOCATOR_SETTINGS_KEY=CUSTOMIZED_POOLED_ALLOCATOR in
     * settings (e.g. <code>acteur.bytebuf.allocator=pooled-custom</code>). See
     * the
     * <a href="https://netty.io/4.1/api/io/netty/buffer/PooledByteBufAllocator.html">javadoc
     * for PooledByteBufAllocator</a>.
     */
    public static final String SETTINGS_KEY_CUSTOM_ALLOC_PREFER_DIRECT = "custom.alloc.prefer.direct";

    /**
     * Length in bytes of the maximum request line length, after which the http
     * codec will return a /bad-request response.
     */
    public static final String SETTINGS_KEY_MAX_REQUEST_LINE_LENGTH = "max.request.line.length";
    /**
     * Length in bytes of the maximum HTTP header buffer size after which the
     * http codec will return a /bad-request response.
     */
    public static final String SETTINGS_KEY_MAX_HEADER_BUFFER_SIZE = "max.header.buffer.size";
    /**
     * Length in bytes of the maximum inbound HTTP chunk size, after which the
     * http codec will return a /bad-request response.
     */
    public static final String SETTINGS_KEY_MAX_CHUNK_SIZE = "max.chunk.size";

    static final AttributeKey<Boolean> SSL_ATTRIBUTE_KEY = AttributeKey.newInstance("ssl");

    protected final Class<? extends A> appType;
    protected final ReentrantScope scope;
    private final int eventThreads;
    private final int workerThreads;
    private final int backgroundThreads;
    private final List<Module> otherModules = new ArrayList<>();

    static final class FastThreadLocalThreadSupplier implements ConventionalThreadSupplier {

        @Override
        public Thread newThread(ThreadGroup group, Runnable run, Settings settings, int stackSize, String bindingName, String threadName) {
            int stack = this.findStackSize(settings, stackSize, bindingName);
            if (stack <= 0) {
                return new FastThreadLocalThread(group, run, threadName);
            } else {
                return new FastThreadLocalThread(group, run, threadName, stack);
            }
        }
    }
    private static final ConventionalThreadSupplier FTL_THREADS = new FastThreadLocalThreadSupplier();

    public ServerModule(Class<? extends A> appType, int workerThreadCount, int eventThreadCount, int backgroundThreadCount) {
        this(new ReentrantScope(new InjectionInfo()), appType, workerThreadCount, eventThreadCount, backgroundThreadCount);
    }

    public ServerModule(ReentrantScope scope, Class<? extends A> appType, int workerThreadCount, int eventThreadCount, int backgroundThreadCount) {
        if (!Application.class.isAssignableFrom(appType)) {
            throw new ClassCastException(appType.getName() + " is not a subclass of " + Application.class.getName());
        }
        this.appType = appType;
        this.workerThreads = workerThreadCount;
        this.eventThreads = eventThreadCount;
        this.backgroundThreads = backgroundThreadCount;
        this.scope = scope;
    }

    public ServerModule(Class<? extends A> appType) {
        this(appType, -1, -1, -1);
    }

    public ServerModule(ReentrantScope scope, Class<? extends A> appType) {
        this(scope, appType, -1, -1, -1);
    }

    /**
     * Get the Guice scope used for injecting dynamic request-related objects
     * into Acteur constructors.
     *
     * @return The scope
     */
    public final ReentrantScope applicationScope() {
        return scope;
    }

    private ThreadModule configureThreadPools(ThreadModule threads) {
        configureThreadPool(threads, EVENT_THREADS);
        configureThreadPool(threads, WORKER_THREADS);
        configureThreadPool(threads, BACKGROUND_THREAD_POOL_NAME);
        configureThreadPool(threads, DELAY_EXECUTOR);
        return threads;
    }

    private void configureThreadPool(ThreadModule threads, String pool) {
        ExecutorServiceBuilder bldr = threads.builder(pool)
                .daemon()
                .withThreadSupplier(FTL_THREADS);
        switch (pool) {
            case EVENT_THREADS:
                bldr.withDefaultThreadCount(4)
                        .withThreadPriority(Thread.MAX_PRIORITY - 1)
                        .withThreadSupplier(FTL_THREADS)
                        // Ensure request serving threads are shut down after
                        // other application level threads are
                        .shutdownCoordination(ExecutorServiceBuilder.ShutdownBatch.LATE)
                        .eager();
                if (eventThreads > 0) {
                    bldr.withExplicitThreadCount(eventThreads);
                }
                break;
            case WORKER_THREADS:
                bldr.withDefaultThreadCount(16)
                        .workStealing()
                        .shutdownCoordination(ExecutorServiceBuilder.ShutdownBatch.LATE)
                        .eager();
                if (workerThreads > 0) {
                    bldr.withExplicitThreadCount(workerThreads);
                }
                break;
            case BACKGROUND_THREAD_POOL_NAME:
                bldr.withDefaultThreadCount(32)
                        .legacyThreadCountName(BACKGROUND_THREADS)
                        .workStealing();

                if (backgroundThreads > 0) {
                    bldr.withExplicitThreadCount(backgroundThreads);
                }
                break;
            case DELAY_EXECUTOR:
                bldr.withDefaultThreadCount(8)
                        .withThreadPoolType(ThreadPoolType.SCHEDULED);
                break;
            default:
                throw new IllegalArgumentException("Unknown thread pool binding " + pool);
        }
        bldr.bind();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void configure() {
        bind(ZonedDateTime.class).toInstance(ZonedDateTime.now());
        bind(Server.class).to(ServerImpl.class);
        bind(ReentrantScope.class).toInstance(scope);
        bind(Application.class).to(appType).asEagerSingleton();
        bind(ChannelHandler.class).to(UpstreamHandlerImpl.class);
        bind(new CISC()).to(PipelineFactoryImpl.class);
        bind(ServerBootstrap.class).toProvider(new ServerBootstrapProvider(
                binder().getProvider(Settings.class),
                binder().getProvider(ByteBufAllocator.class),
                binder().getProvider(ServerBootstrapConfigurer.class)
        ));

        scope.bindTypes(binder(), Event.class, HttpEvent.class, RequestID.class, WebSocketEvent.class,
                Page.class, BasicCredentials.class, Closables.class, DeferredComputationResult.class);
        @SuppressWarnings("deprecation")
        com.mastfrog.acteur.ImplicitBindings implicit = appType.getAnnotation(com.mastfrog.acteur.ImplicitBindings.class);
        if (implicit != null) {
            scope.bindTypes(binder(), implicit.value());
        }
        // Acteurs can ask for a Deferral to pause execution while some
        // other operation completes, such as making an external HTTP request
        // to another server
        install(new ActeurBaseModule(scope));

        install(configureThreadPools(new ThreadModule()));

        bind(UncaughtExceptionHandler.class).to(Uncaught.class);

        Provider<ExecutorService> workerProvider = getProvider(Key.<ExecutorService>get(ExecutorService.class, Names.named(EVENT_THREADS)));
        Provider<ExecutorService> backgroundProvider = getProvider(Key.<ExecutorService>get(ExecutorService.class, Names.named(BACKGROUND_THREAD_POOL_NAME)));

        bind(ExecutorService.class).annotatedWith(Names.named(
                SCOPED_WORKER_THREAD_POOL_NAME)).toProvider(scope.wrapThreadPool(workerProvider));
        bind(ExecutorService.class).annotatedWith(Names.named(
                SCOPED_BACKGROUND_THREAD_POOL_NAME)).toProvider(scope.wrapThreadPool(backgroundProvider));

        bind(Duration.class).toProvider(UptimeProvider.class);
        bind(new CKTL()).toProvider(CookiesProvider.class);

        //XXX anything using this?
        bind(String.class).annotatedWith(Names.named("application")).toInstance(this.appType.getSimpleName());

        bind(ServerImpl.class).asEagerSingleton();
        for (Module m : otherModules) {
            install(m);
        }
        bind(Charset.class).toProvider(CharsetProvider.class);
        bind(ByteBufAllocator.class).toProvider(ByteBufAllocatorProvider.class);
        bind(new ETL()).toProvider(EventProvider.class).in(scope);
        bind(Codec.class).to(CodecImpl.class);
        bind(ApplicationControl.class).toProvider(ApplicationControlProvider.class).in(Scopes.SINGLETON);
        bind(ExceptionEvaluatorRegistry.class).asEagerSingleton();
        bind(InvalidInputExceptionEvaluator.class).asEagerSingleton();
        bind(Channel.class).toProvider(ChannelProvider.class);
        bind(HttpMethod.class).toProvider(MethodProvider.class);
        bind(Method.class).toProvider(MethodProvider2.class);
        bind(Path.class).toProvider(PathProvider.class);
        bind(BuiltInPageAnnotationHandler.class).asEagerSingleton();
        // allow Chain<Acteur> to be injected
        bind(new CL()).toProvider(ChainProvider.class);
        bind(NettyContentMarshallers.class).toProvider(MarshallersProvider.class).in(Scopes.SINGLETON);
        bind(SslProvider.class).toProvider(SSLEngineProvider.class);
        bind(new TypeLiteral<Set<io.netty.handler.codec.http.cookie.Cookie>>() {
        }).toProvider(CookiesProvider2.class);
        bind(Protocol.class).toProvider(ProtocolProvider.class);
        bind(WebSocketFrame.class).toProvider(WebSocketFrameProvider.class);
        bind(HttpProtocolRequest.class).toProvider(HttpProtocolRequestProvider.class);
        bind(ClientDisconnectErrors.class).asEagerSingleton();
    }

    private static final class WebSocketFrameProvider implements Provider<WebSocketFrame> {

        private final Provider<Event> evt;

        @Inject
        public WebSocketFrameProvider(Provider<Event> evt) {
            this.evt = evt;
        }

        @Override
        public WebSocketFrame get() {
            Event evt = this.evt.get();
            if (evt instanceof WebSocketEvent) {
                return ((WebSocketEvent) evt).request();
            }
            throw new IllegalStateException("No web socket event in scope");
        }

    }

    private static final class ProtocolProvider implements Provider<Protocol> {

        private final Provider<Channel> channelProvider;
        private final Provider<HttpEvent> evt;

        @Inject
        ProtocolProvider(Provider<Channel> channel, Provider<HttpEvent> evt) {
            this.channelProvider = channel;
            this.evt = evt;
        }

        @Override
        public Protocol get() {
            Channel ch = channelProvider.get();
            Attribute<Boolean> sslAttr = ch.attr(SSL_ATTRIBUTE_KEY);
            if (sslAttr != null && sslAttr.get()) {
                return Protocols.HTTPS;
            } else {
                CharSequence seq = evt.get().header(X_FORWARDED_PROTO);
                if (seq != null && Strings.charSequencesEqual(seq, Protocols.HTTPS.name(), true)) {
                    return Protocols.HTTPS;
                }
            }
            return Protocols.HTTP;
        }
    }

    private static final class SSLEngineProvider implements Provider<SslProvider> {

        private final SslProvider engine;

        @Inject
        SSLEngineProvider(Settings settings) {
            String name = settings.getString(SETTINGS_KEY_SSL_ENGINE);
            engine = name == null ? SslProvider.JDK : SslProvider.valueOf(name);
        }

        @Override
        public SslProvider get() {
            return engine;
        }
    }

    private static final class MarshallersProvider implements Provider<NettyContentMarshallers> {

        final NettyContentMarshallers marshallers;

        @Inject
        public MarshallersProvider(ObjectMapper mapper) {
            marshallers = NettyContentMarshallers.getDefault(mapper);
        }

        @Override
        public NettyContentMarshallers get() {
            return marshallers;
        }
    }

    static class CL extends TypeLiteral<Chain<Acteur, ? extends Chain<Acteur, ?>>> {

    }

    static class ChainProvider implements Provider<Chain<Acteur, ? extends Chain<Acteur, ?>>> {

        @SuppressWarnings("unchecked")
        private final Provider<Chain> chain;

        @SuppressWarnings("unchecked")
        @Inject
        ChainProvider(Provider<Chain> chain) {
            this.chain = chain;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Chain<Acteur, ? extends Chain<Acteur, ?>> get() {
            return chain.get();
        }

    }

    private static final class PathProvider implements Provider<Path> {

        private final Provider<HttpEvent> evt;

        @Inject
        PathProvider(Provider<HttpEvent> evt) {
            this.evt = evt;
        }

        @Override
        public Path get() {
            return evt.get().path();
        }
    }

    private static final class MethodProvider implements Provider<HttpMethod> {

        private final Provider<HttpEvent> evt;

        @Inject
        MethodProvider(Provider<HttpEvent> evt) {
            this.evt = evt;
        }

        @Override
        public HttpMethod get() {
            return evt.get().method();
        }

    }

    private static final class MethodProvider2 implements Provider<Method> {

        private final Provider<HttpEvent> evt;

        @Inject
        MethodProvider2(Provider<HttpEvent> evt) {
            this.evt = evt;
        }

        @Override
        public Method get() {
            HttpEvent e = evt.get();
            if (e == null) {
                return null;
            }
            return e == null || !(e.method() instanceof Method) ? null : (Method) evt.get().method();
        }

    }

    private static final class ChannelProvider implements Provider<Channel> {

        private final Provider<HttpEvent> evt;

        @Inject
        ChannelProvider(Provider<HttpEvent> evt) {
            this.evt = evt;
        }

        @Override
        public Channel get() {
            return evt.get().channel();
        }
    }

    private static final class InvalidInputExceptionEvaluator extends ExceptionEvaluator {

        @Inject
        public InvalidInputExceptionEvaluator(ExceptionEvaluatorRegistry registry) {
            super(registry);
        }

        @Override
        public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
            if (t instanceof InvalidInputException) {
                InvalidInputException iie = (InvalidInputException) t;
                return Err.badRequest(iie.getProblems().toString());
            }
            return null;
        }
    }

    private static final class EventProvider implements Provider<Event<?>> {

        @SuppressWarnings("unchecked")
        private final Provider<Event> eventProvider;

        @SuppressWarnings("unchecked")
        @Inject
        EventProvider(Provider<Event> eventProvider) {
            this.eventProvider = eventProvider;
        }

        @Override
        public Event<?> get() {
            return eventProvider.get();
        }
    }

    private static class HttpProtocolRequestProvider implements Provider<HttpProtocolRequest> {

        private final Provider<Event<?>> eventProvider;

        @Inject
        HttpProtocolRequestProvider(Provider<Event<?>> eventProvider) {
            this.eventProvider = eventProvider;
        }

        @Override
        public HttpProtocolRequest get() {
            Event<?> evt = eventProvider.get();
            if (evt instanceof HttpProtocolRequest) {
                return (HttpProtocolRequest) evt;
            }
            return null;
        }
    }

    private static final class ETL extends TypeLiteral<Event<?>> {

    }

    static class ApplicationControlProvider implements Provider<ApplicationControl> {

        private final ApplicationControl control;

        @Inject
        public ApplicationControlProvider(Provider<Application> app) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            // In order to separate the API and SPI of Application, so Application
            // is not polluted with methods a subclasser should never call,
            // we do this:
            Application a = app.get();
            java.lang.reflect.Method method = Application.class.getDeclaredMethod("control");
            method.setAccessible(true);
            control = (ApplicationControl) method.invoke(a);
        }

        @Override
        public ApplicationControl get() {
            return control;
        }
    }

    static class CodecImpl implements Codec {

        private final Provider<ObjectMapper> mapper;

        @Inject
        public CodecImpl(Provider<ObjectMapper> mapper) {
            this.mapper = mapper;
        }

        @Override
        public <T> String writeValueAsString(T object) throws IOException {
            return mapper.get().writeValueAsString(object);
        }

        @Override
        public <T> void writeValue(T object, OutputStream out) throws IOException {
            mapper.get().writeValue(out, object);
        }

        @Override
        public <T> T readValue(InputStream byteBufInputStream, Class<T> type) throws IOException {
            return mapper.get().readValue(byteBufInputStream, type);
        }

        @Override
        public <T> byte[] writeValueAsBytes(T object) throws IOException {
            return mapper.get().writeValueAsBytes(object);
        }
    }

    @Singleton
    private static final class CharsetProvider implements Provider<Charset> {

        private final Charset charset;

        @Inject
        CharsetProvider(Settings settings) {
            String set = settings.getString("charset");
            if (set == null) {
                charset = CharsetUtil.UTF_8;
            } else {
                charset = Charset.forName(set);
            }
        }

        @Override
        public Charset get() {
            return charset;
        }
    }

    /**
     * Add another module to be installed with this one
     *
     * @param module
     */
    public ServerModule<A> add(Module module) {
        otherModules.add(module);
        return this;
    }

    /**
     * Override to configure options on the bootstrap which will start the
     * server. The default implementation sets up the allocator.
     *
     * @param bootstrap The server bootstrap
     * @param settings The application settings
     * @return The same bootstrap or optionally another one
     * @deprecated Bind an instance of ServerBootstrapConfigurer instead
     */
    @Deprecated
    protected ServerBootstrap configureServerBootstrap(ServerBootstrap bootstrap, Settings settings) {
        return bootstrap;
    }

    static final class NoOpServerBootstrapConfigurer implements ServerBootstrapConfigurer {

        @Inject
        NoOpServerBootstrapConfigurer() {
        }

        @Override
        public ServerBootstrap configureServerBootstrap(ServerBootstrap bootstrap, Settings settings) {
            return bootstrap;
        }
    }

    @SuppressWarnings("deprecation")
    private static ByteBufAllocator createCustomPooledAllocator(Settings settings) {
        boolean preferDirect = settings.getBoolean(SETTINGS_KEY_CUSTOM_ALLOC_PREFER_DIRECT, true);
        int nHeapArena = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_NUM_HEAP_ARENAS, PooledByteBufAllocator.DEFAULT.numHeapArenas());
        int nDirectArena = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_NUM_DIRECT_ARENAS, PooledByteBufAllocator.DEFAULT.numDirectArenas());
        int pageSize = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_PAGE_SIZE, PooledByteBufAllocator.defaultPageSize());
        int maxOrder = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_MAX_ORDER, PooledByteBufAllocator.defaultMaxOrder());
        int tinyCacheSize = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_TINY_CACHE_SIZE, PooledByteBufAllocator.defaultTinyCacheSize());
        int smallCacheSize = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_SMALL_CACHE_SIZE, PooledByteBufAllocator.defaultSmallCacheSize());
        int normalCacheSize = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_NORMAL_CACHE_SIZE, PooledByteBufAllocator.defaultNormalCacheSize());
        boolean useCacheForAllThreads = settings.getBoolean(SETTINGS_KEY_CUSTOM_ALLOC_USE_CACHE_ALL_THREADS, PooledByteBufAllocator.defaultUseCacheForAllThreads());
        int directMemoryCacheAlignment = settings.getInt(SETTINGS_KEY_CUSTOM_ALLOC_CACHE_ALIGNMENT, 0); // copied from PooledByteBufAllocator.DEFAULT
        return new PooledByteBufAllocator(
                preferDirect,
                nHeapArena,
                nDirectArena,
                pageSize,
                maxOrder,
                tinyCacheSize,
                smallCacheSize,
                normalCacheSize,
                useCacheForAllThreads,
                directMemoryCacheAlignment);
    }

    @Singleton
    private static final class ByteBufAllocatorProvider implements Provider<ByteBufAllocator> {

        private final ByteBufAllocator allocator;

        @Inject
        public ByteBufAllocatorProvider(Settings settings) {
            String s = settings.getString(BYTEBUF_ALLOCATOR_SETTINGS_KEY, DEFAULT_ALLOCATOR);
            boolean disableLeakDetector = settings.getBoolean(SETTINGS_KEY_DISABLE_LEAK_DETECTOR, DEFAULT_DISABLE_LEAK_DETECTOR);
            ByteBufAllocator result;
            switch (s) {
                case DIRECT_OR_HEAP_BY_PLATFORM:
                    result = UnpooledByteBufAllocator.DEFAULT;
                    break;
                case DIRECT_ALLOCATOR:
                    result = new UnpooledByteBufAllocator(true, disableLeakDetector);
                    break;
                case HEAP_ALLOCATOR:
                    result = new UnpooledByteBufAllocator(false, disableLeakDetector);
                    break;
                case POOLED_ALLOCATOR:
                    result = PooledByteBufAllocator.DEFAULT;
                    break;
                case CUSTOMIZED_POOLED_ALLOCATOR:
                    result = createCustomPooledAllocator(settings);
                    break;
                default:
                    throw new ConfigurationError("Unknown value for " + BYTEBUF_ALLOCATOR_SETTINGS_KEY
                            + " '" + s + "'; valid values are " + DIRECT_ALLOCATOR + ", "
                            + HEAP_ALLOCATOR + ", " + POOLED_ALLOCATOR);
            }
            this.allocator = result;
        }

        @Override
        public ByteBufAllocator get() {
            return this.allocator;
        }
    }

    private final class ServerBootstrapProvider implements Provider<ServerBootstrap> {

        private final Provider<Settings> settings;
        private final Provider<ByteBufAllocator> allocator;
        private final Provider<ServerBootstrapConfigurer> bootstrapConfigurer;

        public ServerBootstrapProvider(Provider<Settings> settings, Provider<ByteBufAllocator> allocator, Provider<ServerBootstrapConfigurer> bootstrapConfigurer) {
            this.settings = settings;
            this.allocator = allocator;
            this.bootstrapConfigurer = bootstrapConfigurer;
        }

        @Override
        public ServerBootstrap get() {
            ServerBootstrap result = new ServerBootstrap();
            ByteBufAllocator alloc = allocator.get();
            Settings settings = this.settings.get();
            result.option(ChannelOption.ALLOCATOR, alloc);
            result.childOption(ChannelOption.ALLOCATOR, alloc);
            result.childOption(ChannelOption.TCP_NODELAY, settings.getBoolean(SETTINGS_KEY_SOCKET_TCP_NODELAY, DEFAULT_TCP_NODELAY));

            settings.ifIntPresent(SETTINGS_KEY_SOCKET_MAX_MESSAGES_PER_READ, maxOverall -> {
                settings.ifIntPresent(SETTINGS_KEY_SOCKET_MAX_MESSAGES_PER_INDIVIDUAL_READ, maxIndividual -> {
                    result.option(ChannelOption.RCVBUF_ALLOCATOR, new DefaultMaxBytesRecvByteBufAllocator(
                            nonNegative(SETTINGS_KEY_SOCKET_MAX_MESSAGES_PER_READ, maxOverall),
                            nonNegative(SETTINGS_KEY_SOCKET_MAX_MESSAGES_PER_INDIVIDUAL_READ, maxIndividual)));
                });
            });

            settings.ifIntPresent(SETTINGS_KEY_SOCKET_SO_RCVBUF, val -> {
                result.option(ChannelOption.SO_SNDBUF, nonNegative(SETTINGS_KEY_SOCKET_SO_RCVBUF, val));
            });

            settings.ifIntPresent(SETTINGS_KEY_SOCKET_SO_SNDBUF, val -> {
                result.childOption(ChannelOption.SO_SNDBUF, nonNegative(SETTINGS_KEY_SOCKET_SO_SNDBUF, val));
            });

            settings.ifIntPresent(SETTINGS_KEY_SOCKET_CONNECT_TIMEOUT_MILLIS, val -> {
                result.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        nonNegative(SETTINGS_KEY_SOCKET_CONNECT_TIMEOUT_MILLIS, val));
            });

            settings.ifIntPresent(SETTINGS_KEY_SOCKET_WRITE_SPIN_COUNT, val -> {
                result.childOption(ChannelOption.WRITE_SPIN_COUNT,
                        greaterThanZero(SETTINGS_KEY_SOCKET_WRITE_SPIN_COUNT, val));
            });
            return bootstrapConfigurer.get().configureServerBootstrap(configureServerBootstrap(result, settings), settings);
        }
    }

    @SuppressWarnings("deprecation")
    private static final class CookiesProvider implements Provider<Set<io.netty.handler.codec.http.Cookie>> {

        private final Provider<HttpEvent> ev;

        @Inject
        public CookiesProvider(Provider<HttpEvent> ev) {
            this.ev = ev;
        }

        @Override
        public Set<io.netty.handler.codec.http.Cookie> get() {
            HttpEvent evt = ev.get();
            String h = evt.header(HttpHeaderNames.COOKIE.toString());
            if (h != null) {
                @SuppressWarnings("deprecation")
                Set<io.netty.handler.codec.http.Cookie> result = io.netty.handler.codec.http.CookieDecoder.decode(h);
                if (result != null) {
                    return result;
                }
            }
            return Collections.emptySet();
        }
    }

    @SuppressWarnings("deprecation")
    private static final class CookiesProvider2 implements Provider<Set<io.netty.handler.codec.http.cookie.Cookie>> {

        private final Provider<HttpEvent> ev;

        @Inject
        public CookiesProvider2(Provider<HttpEvent> ev) {
            this.ev = ev;
        }

        @Override
        public Set<io.netty.handler.codec.http.cookie.Cookie> get() {
            HttpEvent evt = ev.get();
            io.netty.handler.codec.http.cookie.Cookie[] cookies = evt.header(COOKIE_B);
            return cookies == null || cookies.length == 0 ? Collections.emptySet()
                    : setOf(cookies);
        }
    }

    static final class Uncaught implements UncaughtExceptionHandler {

        private final Provider<ApplicationControl> ctrl;

        @Inject
        Uncaught(Provider<ApplicationControl> ctrl) {
            this.ctrl = ctrl;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            ctrl.get().internalOnError(e);
        }
    }

    private static class UptimeProvider implements Provider<Duration> {

        private final ZonedDateTime dt;

        @Inject
        UptimeProvider(ZonedDateTime dt) {
            this.dt = dt;
        }

        @Override
        public Duration get() {
            return Duration.between(dt, ZonedDateTime.now());
        }
    }

    protected void onInit(Settings settings) {
    }

    protected void onBeforeStart(Server server, Dependencies deps) {
    }

    protected void onAfterStart(Server server, Dependencies deps) {
    }

    private static class CISC extends TypeLiteral<ChannelInitializer<SocketChannel>> {
    }

    @SuppressWarnings("deprecation")
    private static class CKTL extends TypeLiteral<Set<io.netty.handler.codec.http.Cookie>> {
    }

    private static class ClientDisconnectErrors extends ErrorHandler.Typed<SocketException> {

        private final boolean inTest = Boolean.getBoolean("unit.test");

        @Inject
        ClientDisconnectErrors(ErrorHandler.Registry handlers) {
            super(handlers, SocketException.class, true);
        }

        @Override
        protected boolean doHandle(SocketException t) {
            if (inTest) {
                return false;
            }
            // Avoid logging every connection reset - it generates a lot of
            // logging noise in normal use
            if ("Connection reset".equals(t.getMessage())) {
                return true;
            }
            return false;
        }

        @Override
        protected int ordinal() {
            return Integer.MAX_VALUE - 1;
        }

    }

}
