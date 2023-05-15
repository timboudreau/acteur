# Writing Output

There are several ways of writing response output from a request.

The simplest is to use the built in support for strings, byte buffers, byte arrays and
other similar types, and writing JSON (using Jackson under the hood).  That simply
looks like

```java
ok(someObject);
```

or

```java
reply(HttpResponseStatus.CREATED, someObject);
```

and the right thing generally happens (the default behavior uses the 
`netty-content-marshallers` library, which has an SPI to plug in handling for
additional types).

If you need to do something unusual with output, you can simply do whatever you
need to do to produce your output, turning it into a string or byte array and passing
it as seen above.


## Codec and ObjectMapper

Built in support for JSON reading and writing uses [Jackson](https://github.com/FasterXML/jackson).

Code in the framework that is intended to let the application author choose how
things are serialized uses the `Codec` interface (which is a subset of Jackson's
`ObjectMapper`'s API).  By default, you get a binding to `ObjectMapper`, but it
could be implemented and bound to any implementation of it.

`Acteur` does not actually create any bindings for `ObjectMapper` - it simply utilizes
the fact that `ObjectMapper` has a default no-argument constructor, so Guice can
create them as needed.

If you want to customize the way JSON is interpreted or how Jackson is configured,
you can simply *create a binding to ObjectMapper* configuring it how you want.

Another option for configuring Jackson is the [com.mastfrog:jackson](https://github.com/timboudreau/giulius-web/tree/master/jackson)
library, which contains guice bindings for Jackson and ways of adding to a registry
of things that configure your `ObjectMapper`s with serializers and deserializers, etc.


## Scalable Output Writing

The above is sufficient for objects you know will be small enough at runtime that
you aren't worried about running out of heap if the wrong combination of concurrent
requests arrive at the same time.

But sometimes, you need the ability to write arbitrarily large output in a finite
amount of memory.  The general pattern is to write a small batch of output, listen
for when the bytes are flushed to the socket, and only then write some more, and
keep going like that until done.

Such code is fussy (there are potential races to guard against), and involves
directly using Netty's HTTP codec.  We'll cover
that at the end of this section.  For two common, general-purpose cases, there is
built-in support.

First, though, the following may meet your needs (bear in mind, in both cases, you
need to bind the target type - `InputStream` or `Stream` in the request scope for
this to work - add it to `scopeTypes` in one of your `@HttpCall` annotations):


### Built-In Support - InputStreams

For things that can be made into an InputStream (without pulling all the data into
memory), you can simply write your `Acteur` to put an `InputStream` into the request
context, and add the `@Concluders(com.mastfrog.acteur.output.InputStreamActeur.class)`
annotation to your http call `Acteur`.

The `Settings` key-value pair for `input.stream.batch.size` determines how many bytes
will be written per-batch.


### Built-In Support - Stream

General support for the JDK's `Stream` interface is built-in as well - as with `InputStream`
above, simply bind `Stream` in request scope, and make `ObjectStreamActeur.class` the 
concluder of your request processing chain.

This will use the `Codec` bound at startup (Jackson's ObjectMapper unless you bind something
else) to serialize individual objects.

`ObjectStreamActeur` also expects an instance of `ArrayFormatting` to be in scope,
which describes the delimiters to use - since the response is effectively a JSON array,
but no JSON library is used to write the opening, closing and inter-item delimiters.  That
way, if you have `Codec` bound to either do something interesting with JSON formatting,
or to do something other than JSON entirely, you have full control over how things
are written.

You can easily customize the default instance - e.g., if you wanted a newline after the
comma between items, you could just do

```java
next(someStream, ArrayFormatting.JSON.withInterItemDelimiter(",\n"));
```

## Writing Incremental Output The Hard Way

If none of the above options are a good fit for your use case, you can take full control
of how the response bytes are written - just call one of your Acteur's super methods:

* `setResponseBodyWriter(Class<ChannelFutureListener> type)` - takes a class, and the instance
will be created by Guice (make sure all of the arguments it will need are available in the request
scope!)
* `setResponseBodyWriter(ChannelFutureListener listener)` - takes a listener instance

[`ChannelFutureListener`](https://netty.io/4.1/api/io/netty/channel/ChannelFutureListener.html) is
Netty's own interface for getting a notification once a write operation has been flushed to the
socket.  After an `Acteur` sets its response code, the response *headers* will be written and
flushed.  Your `ChannelFutureListener` will be called once the headers have been flushed. The 
listener gets passed a `ChannelFuture` and can simply call `future.channel().writeAndFlush(someObject)`
to write more data to the socket.  That returns a new `ChannelFuture` and you can listen on it
to write more output, and so forth.

> ### To chunk or not to chunk
> Http protocol supports *three* ways of sending a response payload to clients:
>
> * **Chunked encoding** - there is no `Content-Length` header, and the response payload comes
> in (typically) fixed-length chunks with a couple byte header that indicates the number of bytes
> in the following chunk.  At the time the response headers are received, the client 
> no idea how many bytes will follow.  This is the default, and for many cases, is perfectly
> adequate.  Chunked encoding also supports "trailing headers" that let you append some
> headers computed while sending the response.
> * **Streaming output with `Content-Length`** - the `Content-Length` header says exactly how
> many bytes will be in the payload, and the payload is sent verbatim, with no chunks.  This
> has the advantage that clients know *exactly* how many bytes they need space for, as soon
> as the response headers arrive, and can allocate resources or abort as-needed.
> * **HTTP 1.0 Style Output** - this is not really recommended, but browsers must support it:
> This is where there *is* no `Content-Length` header, and no chunks;  you stream the response
> bytes and the client knows it's got all of them when the server closes the connection.  This
> approach has obvious problems - if the connection is interrupted for some reason other than
> the server closing it, the client has no indication that it didn't receive all the bytes.
> If you do this (don't!), you **absolutely must close the stream** or the client will hang,
> possibly indefinitely, waiting for more bytes when there aren't any.  Hint:  After writing
> the last batch of bytes, call `future.addListener(ChannelFutureListener.CLOSE)` to ensure
> the socket does get closed, but not until your final bytes have been sent.
>
> If you want to do streaming output, you need to call `setChunked(false)` in your `Acteur`,
> **and you must supply a `Content-Length` header**.
>
> **Important:** If you use *chunked* encoding, the objects you `writeAndFlush` to the Netty
> channel need to be Netty's `HttpContent`, which are part of its HTTP codec - use DefaultHttpContent
> and `DefaultLastHttpContent` for the final chunk.
>
> If you used streaming, the objects you `writeandFlush` should be Netty `ByteBuf` instances.


At this point you are interacting directly with Netty's pipeline and codec, which Netty's own
documentation covers quite well.  But a few caveats can help you write this sort of thing effectively:

1. This involves using Netty's `ByteBuf` class - basically a better, separate-read-and-write-cursor
`ByteBuffer`.  Ask for the application-wide `ByteBufAllocator` to be injected, and use 
`allocator.ioBuffer()` to get instances of `ByteBuf` to write to - by default, `Acteur` uses
Netty's pooled, direct-memory-allocated allocator, so writing to the socket is zero-copy (the
allocator used is controlled using `Settings`).
2. If using **chunked encoding** (the default), the objects you write to the socket should be
Netty's `HttpContent` objects, which model a single HTTP-chunked-protocol chunk.  These just take
a `ByteBuf`.
3. If you recursively attach a listener to write batches of output, Netty *can* call you back
on another thread *while the thread attaching the listener is still in your 
`operationComplete(ChannelFuture)` method* if the flush occurs very rapidly.  There are various
ways to guard against this; the simplest is to make the method synchronized.  If there is no
state in your listener and the listener is attached at the very end of `operationComplete()`,
this reentrancy race may be harmless.

