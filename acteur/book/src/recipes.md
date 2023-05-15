# Recipes

These are some recipes for doing common tasks in Acteur:


## Ensuring Resource Cleanup On Abort

Sometimes you'll open a stream or cursor or other finite resource that should be closed
when the request cycle ends, even if that happens by the connection being abruptly closed.
`Closables` is an object you can ask to have injected, which will run some code on connection
closure.  Here's an example doing a directory listing:

```java
public class ListDirectory extends Acteur {
   @Inject
   ListDirectory(Path dir, Closables closer) throws IOException {
     // Assume `dir` is computed by some preceding Acteur and passed along to us
     Stream<Path> listing = Files.list(dir);
     closer.add(listing::close);
     next();
   }
}
```

## "Mounting" the An Application on a Base Path

Sometimes it is desirable "mount" the application on a base path - so all URIs it
respond to must be "underneath" a path.

The `Settings` key/value pair defined in `ServerModule.SETTINGS_KEY_BASE_PATH` as the
string `basepath` can be used to provide a leading path.

The leading path will be stripped from all paths the application sees, either by injecting
a `com.mastfrog.url.Path` object or using `HttpEvent.path()`.

It *will* be visible if you call `uri()` on the raw Netty `HttpRequest` obtainable from
an `HttpEvent`.


## Turning Exceptions Into Appropriate Error Responses

In general, if an exception should cause the request to abort somehow, simply have
your Acteur's constructor throw it.  This keeps Acteurs focused on the happy path,
and keeps business logic clear and simple.

Translating an exception into an error response happens in `ExceptionEvaluator`s.
Many of these can be bound in an application, and registering one is simple - just
implement `ExceptionEvaluator` and bind it as an eager singleton in your Guice module.

Say we have our own special exception:

```java
    static class NonExistentThingamabobException extends RuntimeException {
        NonExistentThingamabobException(String message) {
            super(message);
        }
    }
```

This might be thrown by some library you are using when you attempt to look up a
... Thingamabob based on the request URL.

The `Acteur` that does the looking up, if there's nothing that it can do other than
give up, simply throws `NonExistentThingamabobException`.

To translate that into a nice error message, you simply implement `ExceptionEvaluator`
like this:

```java
class ThingamabobEval extends ExceptionEvaluator {
    @Inject
    ThingamabobEval(ExceptionEvaluatorRegistry registry) {
        super(registry);
    }
    @Override
    public ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, Event<?> evt) {
        if (t instanceof NonExistentThingamabobException) {
            return Err.gone("No such thingamabob.").put("msg", t.getMessage());
        }
        return null;
    }
}
```

Then, in whatever Guice module sets up bindings for your application, just do something
like `binder.bind(ThingamabobEval.class).asEagerSingleton()` and it will get created on
startup and register itself, so whenever an acteur throws an exception, it will be asked
about it, and can intercept and shape the error response that will be returned.

## Deferring The Chain - Pausing Until Some Async Call Is Complete

There are several different ways to defer subsequent acteurs until some work is completed,
depending on your needs:

### Using `Deferral` Directly

`Deferral` has methods for pausing the chain of acteurs and providing a `resumer()` which
continues processing - optionally accepting some new objects to make available to subsequent
acteurs:

```java
MyActeur(Deferral def, SomeAsyncService svc, Path requestPath) {
    def.defer((Resumer resumer) -> {
        svc.lookup(requestPath.toString(), resumer::resume);
    });
    next(); // you still need to call next with Deferral!
}
```

There also exists a method `defer()` which immediately returns a `Resumer`, but that resumer
*must not be called* until the acteur constructor has exited, or bad things will happen - it
is very rarely needed when you need to pass the `Resumer` to some other code that will
definitely obey the above restriction.  So, ordinarily, the synchronous overload of `defer()`
is not used.

### Using `Acteur.defer()` and `CompletableFuture`

A more convenient way to handle deferred processing is by calling one of two `super` methods
on your `Acteur`:

* `defer()` - returns a `CompletableFuture` parameterized on whatever you want.  You do your
work, and then call the returned `CompletableFuture` with either an exception in the event of
failure, or an object to make available to subsequent Acteurs.  Completing the `CompletableFuture`
restarts processing of the request and moves along to the next Acteur in the chain.
* `deferThenRespond(HttpResponseStatus status)` works the same way as `defer()`, but the object
passed to the `CompletableFuture` simply **is** the response payload, so you don't need another
acteur to take it and write it out.

**Note:** In both of these cases, you **must** bind the result's Java type in request scope
(typically done in `@HttpCall(scopeTypes = {SomeClass.class, SomeOtherClass.class})` or
programmatically on startup in a module) - if you get an exception saying no instance of the type
is bound, or that it does not have a default constructor, then it probably is not bound.


## Dynamic Chain Modification

While rarely needed, it *is* possible for an `Acteur` to *insert* one or more `Acteur`s in
the chain to run subsequent to it - to dynamically decide the next step.  It's not a thing
you ought to do unless there is a clear need to, since it defeats such features as dynamically
generated help, which relies on the application's configuration being deterministic at
compile-time.

Database support libraries sometimes do this to, for example, choose a different strategy
for writing out a response depending on how many items a cursor references, using a batching
strategy for large cursors and a just-fetch-and-serialize-everything approach for small
result-sets.

While less than pretty, the argument type you want is 
`Chain<Acteur, ? extends Chain<Acteur, ?>>` - this gives you access to the currently
executing chain of `Acteur`s.  Here is a toy example of using this approach to
alternately return "one" then "two" from responses:

```java
private static int count;

@HttpCall
@com.mastfrog.acteur.preconditions.Path("dyn")
@Methods(GET)
static class DynActeur extends Acteur {
    @Inject
    DynActeur(Chain<Acteur, ? extends Chain<Acteur, ?>> chain) {
        // chain.add() will append after all subsequent Acteurs;
        // chain.insert() will insert immediately after this Acteur.
        if (count++ % 2 == 0) {
            chain.add(One.class);
        } else {
            chain.add(Two.class);
        }
        next();
    }
}
static class One extends Acteur {
    One() {
        ok("one");
    }
}

static class Two extends Acteur {
    Two() {
        ok("two");
    }
}
public static void main(String[] args) throws Exception {
    new ServerBuilder().build().start().await();
}
```


## Instantiated Acteurs for Factories

Once in a great while, you need to do something that requires creating an
instance of an Acteur, rather than referencing it by class.  Typically it
is only needed for things like factories which take varied input and apply
the same logic to a request and whatever that input is.

In fact, under the hood of the `@Methods` annotation, what actually happens is
a class called `ActeurFactory` with methods that generate Acteurs for various
kinds of filtering and constraints the framework does with annotations *is*
creating `Acteur`s that generate their state dynamically in an override of
`getState()` rather than setting it in their constructor.

A simplified version of that factory class's `matchMethods()` would look like

```java
@Singleton
public final class MyActeurFactory {
    private final Provider<Path> events;
    @Inject
    MyActeurFactory(Provider<Path> events) {
        this.events = events;
    }

    public Acteur matchPath(String path) {
        return new Acteur() {
            public State getState() {
                if (events.get().is(path)) {
                    return new ConsumedState();
                }
                return new RejectedState();
            }
        };
    }
}
```

To reiterate, the **only** rationale for doing this sort of thing is the
factory-method use-case - such `Acteur`s are not a normal part of application
code, and where used in the framework, things like help generation also understand
the annotations such `Acteur`s are hidden behind (they should always be), so
the application remains statically analyzable.  This is really an expert-feature
with a single use-case.


## Processing a Request as Soon As The Headers Arrive

By default, unless you tell it not to or do what is about to be described on an
individual request-handling chain, Netty's `HttpObjectAggregator` is used to aggregate
HTTP chunks into a single in-memory `ByteBuf` before your `Acteur` is called.

The `@Early` annotation is used to mark an `@HttpCall`-annotated `Acteur` as wanting
to bypass that aggregation, get called as soon as the request headers have arrived,
and have an opportunity to reject the request before a potentially large payload
has been received.

`@Early` has a field, `chunkHandler` that will be called with each HTTP content
chunk as it arrives, and can, for example, write the data to a temporary file as
it goes, or "sniff" the first chunk to determine if it contains a media header that
matches the request's content-type or is known (in the case of, say, video uploads),
and reject the request, sending a response, as soon as that chunk has arrived in the
case that the data does not appear usable.

The default dance between an HTTP client and a server is that, for file uploads, the
client should send the header `Expect: 100-Continue` and then **not** send the request
body until it gets a preliminary response line telling it to continue.  You can set
the `send100Continue` field on `@Early` to send it automatically if the request was
not rejected.  Bear in mind that there is nothing to stop HTTP clients from *not*
sending `Expect: 100-Continue`, or sending it and then blasting away with the payload
anyway, so be prepared for misbehavior.

Using `@Early` *does* change the order in which response chains are run - all `Acteur`s
annotated with `@Early` get a crack at responding *before* any that do not.

In general, only use this if there is a legitimate need - it has no value on, say,
a `GET` or `HEAD` request which the HTTP parser will not allow to have a payload anyway.


## Observing the Gory Details of Request Processing

The interface `com.mastfrog.acteur.debug.Probe` is an interface that, if bound, the
framework will call on every single step of request processing.

This allows applications that want to do detailed debug logging to do so, without the
framework forcing a particular logging framework on applications.

* `onBeforeProcessRequest(RequestID id, Event<?> req)` - Called immediately after event
creation, before attempting to run any `Acteur`
* `onBeforeRunPage(RequestID id, Event<?> evt, Page page)` - Called before running a single chain
(encapsulated by the `Page` class)
* `onActeurWasRun(RequestID id, Event<?> evt, Page page, Acteur acteur, ActeurState result)` - Called
after a single `Acteur` has been passed a request - the `result` variable contains the output state
* `onFallthrough(RequestID id, Event<?> evt)` - Called if no acteur chain responded to the request,
and a 404 response is about to be sent
* `onInfo(String info, Object... objs)` - Generic info method that allows for passing information that
does not match a particular method - so plugged-in components can contribute information to a Probe
* `onThrown(RequestID id, Event<?> evt, Throwable thrown)` - Called when an exception was thrown
* `onBeforeSendResponse(RequestID id, Event<?> event, Acteur acteur, HttpResponseStatus status,
boolean hasListener, Object message)` - Called immediately before sending a response.  If the message
object is null, but `hasListener` is true, a payload is going to be sent

If you just need to quickly debug how a request is being processed during development,
an implementation that logs every detail to `System.out` is included - just
`binder.install(new LoggingProbeModule())`.

The `Event` interface can be a little clunky to work with in `Probe`, as if you want to call any
`HttpEvent`-specific methods, you will need to cast it (`Event` can also be a `WebSocketEvent` - it's
not just for HTTP requests).  In the case that you are only interested in `HttpEvent`, subclass
`HttpProbe` and `bind(Probe.class).toProvider(YourHttpProbeSubclass.class)`.


## Websocket Support

Basic support for websockets is built in, and continues to use the `Acteur` model for processing
websocket messages.

The way this works is fairly simple:

1. You put `WebsocketUpgradeActeur` in your chain
2. When a request arrives and the handshake succeeds, `WebsocketUpgradeActeur` calls `defer()` on
the chain, and never resumes it
3. Whenever a websocket event arrives, the remainder of the chain will be invoked, and can
ask for a `WebsocketEvent` as a constructor argument to process it.  The original `HttpEvent` and
any other objects from the request scope of the initiating request will be available for injection
into those `Acteur`s.

You can bind an implementation of `OnWebsocketConnect` to provide some additional context objects
that will be injectable into requests (for example, a message bus other parts of the application
can use to pass messages that should be delivered as websocket messages to the client).

A handy library for writing tests of websocket APIs is [`blather`](https://github.com/timboudreau/blather).


## SSL Configuration

By default, when starting a server in SSL mode, a new self-signed certificate is generated.

To set up proper SSL with a real cert, or using a custom cert your clients can be counted
on to know about, simply bind `ActeurSslConfig` and override
`SslContext createSslContext() throws CertificateException, SSLException` to return whatever
sort of `SslContext` you need (that class, part of Netty, has factory methods for certs from
files and much more).


## Using Your Own Annotations

If you want to add your own annotations that insert `Acteurs` in the chain, the same way annotations
like `@Methods` and `@Path` do it, just implement `PageAnnotationHandler` and implement
`<T extends Page> boolean processAnnotations(T page, List<? super Acteur> addTo)`.

That method can add any `Acteur`s it wants to the passed list (you can use `ActeurFactory.wrap(Class<? extends Acteur>)`
to pass an acteur type rather than an instance, or implement them as described in *Instantiated Acteurs for Factories*,
and **must** return true if any items have been added to the list.
