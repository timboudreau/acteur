Acteur - Async Web Development Made Simple and Maintainable
===========================================================

Acteur is a framework for writing async web server applications in Java.

Asynchronous programming frequently gets derided as *too hard*, or
*callback hell* and things like that.  And the industry has come up
with various solutions to that perceived problem - for example 
Javascript and Rust's async/await, JDK 21's green threads.

The problem with both of those solutions is that they are solving *the
wrong problem*.  The former is a way to pretend you're writing synchronous
code, and the latter is a way to write code that looks multithreaded but
isn't.

The *actual problem* - and the one Acteur addresses - is *dispatch*:
What's actually needed isn't a way to let you pretend asynchronous code is
synchronous - it's having the right abstractions to make the distinction
of synchronous vs asynchronous **disappear**:

* You write some chunks of (usually) synchronous logic
* You describe a choreography of how and when they get called
* A dispatch engine calls them with the appropriate arguments when they are needed

Take a look at some asynchronous code in Javascript or Rust that uses async/await,
at what is going on at those boundaries where something is awaited.  Nearly every
time, that boundary divides two logically separated activities - and often at least
one of those activities is something that could be reused across multiple kinds of
requests, if it could be untangled from its surroundings.  Async/await lets you
write spaghetti-code ... with fewer curly braces. Yay!

What's needed isn't a way to play make-believe that asynchronous code is
synchronous, it's a way to *structure* your application *as* a set of steps - callbacks
in a sense - that each do one step of a decision tree.  And a thing that simply
calls them and lets them feed arguments to each other.

Done right, you get a framework that largely just gets out of the way and lets you
write business logic.  Other than typing `extends Acteur` and the occasional helper
type, there just aren't a lot of must-learn abstractions involved.

Take responding to an HTTP request (or really any decision tree), and carve
it up into a series of steps.  What do they look like?  Here's an example
from a video uploading app:

1. Match the URL method to one the application responds to
2. Match the URL path to one the application responds to that method on
3. Examine an authorization header (cookie, whatever) and figure out if the user is authorized, and identify them
4. Validate some URL parameters of the request
5. Examine the content-type of the request (and size, etc.) to validate the request
6. As the first few bytes of the payload arrive, validate that they match a known media file header structure
7. As the remaining bytes arrive, update a running hash of the bytes, writing them all to a temp file
8. When the upload concludes, move the temporary file to a staging area for conversion and put a message in a queue
9. Compose a response that identifies the queue job to process the video and send it as the response payload

There are a few things to notice about these steps - early every one is making one of four choices:
* Reject the request without prejudice (meaning a different chain of steps get a crack at it if there is another)
* Compute some data to make available to subsequent steps in the chain and move forward with the chain
* Return a response
* Defer further steps in the chain until some work is complete, then resume

Each step is a small state machine that emits one of those states, and possibly some
data that should be available to a later step - often not used immediately (for example,
the user identity computed in step 3 may not be used again until step 8.

Some of the steps involve asynchronous computation, like calling a database or another web
service - step 3 is definitely an example of that - where request processing needs to pause
until the new state is computed.  Steps 6 and 7 definitely are, since it is probable that
there are no payload bytes to examine at the time the request arrives, and the caller controls
when they do.  Other steps don't *have* to be synchronous, but it is harmless if they are.

And many of the steps will be useful for many different path+method combinations - even more
so if they can be made a little bit generic.

In fact, the boundaries where async I/O happens are almost always also the boundaries where
you'd want to carve logic up for reuse.  You only wind up in callback-hell if you don't have
the abstractions available to let you write it as a single step, for reuse.

So what we need is

* A way to encapsulate each step in a wad of logic that has a name that lets it be referenced declaratively
* A way to compose a list of those steps
* A thing that will dispatch one step, then the next, handle the states they emit, and when one
step takes as input a type that a previous step generated, propagate that data to it

With those things, 99% of the time, you literally *don't care* whether the code is asynchronous
or not.  A step gets called, with the arguments it has declared, and emits its state.

It turns out that Java and its ecosystem already has a pretty ideal mechanism for that: **constructors**.
A constructor guarantees that code is run once and only once for the lifetime of an object;  it
expresses its dependencies as the set of its arguments.  And there is a wonderful, mature, existing
mechanism for dispatch:  Guice.

That's right - take Guice and turn it sideways, and what is it *really*?  A *constructor dispatch engine* -
that's the thing it actually does.

The Framework
-------------

An `Acteur` is a single step in processing a request.  It does its work in its constructor - in fact,
that's the only part of it that does work, and once the constructor is run, the acteur is discarded.
It must make a choice to emit some state (there is a `setState()` method, but usually you call something
else that implicitly sets it, like `next()` or `reply(OK, "Hello world")`.

An Acteur is a function object.  But unlike lambdas (which would put us back in callback-hell), it
is a *named type* that can be referenced by its `class`, which lets us put it into a list (or many lists!)
of steps to validate or respond to a request.  And since it is a constructor, Guice can find its arguments
for it trivially.

The earliest examples of Acteur applications had you explicitly add a bunch of `Acteur`s to a `Page` object
(the name inspired by Apache wicket's Page type) - and you can still do that today, though it is more
common and simpler to use annotations and let the code for that be generated.  But it is still used in
Acteur's own tests, and does paint a complete picture of what really is going on, so here is an old-school
application with one http call:

```java
public static class HelloApp extends Application {
  HelloApp() {
    add(HelloPage.class);
  }
  static class HelloPage extends Page {
    @Inject
    HelloPage(ActeurFactory builtins) {
      add(builtins.matchPath("^hello$")); // only respond to /hello
      add(builtins.matchMethod(GET)); // only respond to HTTP GET requests
      add(AuthenticationActeur.class); // We will have bound Authenticator to handle basic auth
      add(HelloActeur.class); // Compute the response
    }
  }
  static class HelloActeur {
    @Inject
    public HelloActeur(BasicCredentials auth, HttpEvent event) {
        ok("Hello " + auth.username + " at " + event.remoteAddress());
    }
  }
}
```

It's pretty clear what's going on here.  The `Application` is a list of `Page`s - state machines
that can process a request, and a `Page` is a list of `Acteur`s - steps which can compute a state
change.  An inbound request gets tried against each `Page` type in the application, and if none
of them respond, a `404 Not Found` response is generated.

```
Application
    page-1
        acteur-1-a
        acteur-1-b
        acteur-1-c
        ...
    page-2
        acteur-2-a
        acteur-2-b
        acteur-2-c
    ...
```

Where the magic happens is in how the `BasicCredentials` *generated by AuthenticationActeur* finds
its way to `HelloActeur`.  These steps are not necessarily even run on the same thread.  Suffice it
to say that it does - an `Acteur` can call `next(someObject, someOtherObject)` and those objects will
be available to any subsequent `Acteur` that knows their type.

This is, effectively message-passing - done the simplest way possible, with no high-cognitive-overhead
abstractions:  Messages are objects.  Your objects.  Whatever you want them to be (just make them
immutable or thread-safe).

Another thing to notice is, *this code has a threading model* but it is completely invisible to
the application author (in fact `Acteur` has a system property that will make it run request steps
synchronously instead of asynchronously - your application will work either way, *because the threading
model is not exposed*).

Now, the way you'd write this in a modern Acteur application is much simpler:

```java
@HttpCall
@Methods(GET)
@Path("hello")
@Precursors(AuthenticationActeur.class)
public class HelloActeur {
    @Inject
    public HelloActeur(BasicCredentials auth, HttpEvent event) {
        ok("Hello " + auth.username + " at " + event.remoteAddress());
    }
}
```

In fact, if you build this code (make sure `acteur-annotation-processors` is a `provided` scope dependency),
you'll find something a lot like that old-school code above gets generated into `target/generated-sources`.

The point is, we've made all of the boilerplate disappear - we've just written a class that is our business
logic.  `@HttpCall` registers our generated `Page` in a file under `META-INF/` in our JAR.  Launching our
application is as simple as

```java
public static void main(String... args) throws Exception {
   new ServerBuilder().build().start().await(); 
}
```

In practice, you write one `Acteur` which is the main logic of a given HTTP call, and annotate it with
`@HttpCall` to cause a `Page` to be generated for it, and decorate that `Acteur` with annotations that describe
what to run ahead of it (and optionally, with `@Concluders`, after it).  And we can reuse them.  Let's
generalize things a little to see how that works:

```java
enum Verb {
  hello,
  goodbye;
}
static class VerbFinder extends Acteur {
  @Inject
  VerbFinder(HttpEvent evt) {
    next(Verb.valueOf(evt.path().toString()));
  }
}
@HttpCall(scopeTypes=Verb.class) // tell the framework some acteur wants a Verb as an argument
@Methods(GET)
@Path({"/hello", "/goodbye"})
@Precursors({VerbFinder.class, AuthenticationActeur.class})
static class GreetingActeur {
    @Inject
    public GreetingActeur(BasicCredentials auth, Verb verb, HttpEvent event) {
        ok(verb + " " + auth.username + " at " + event.remoteAddress());
    }
}
```

It's a little silly, but perhaps you see the point - we've now made emitting a greeting
generic with respect to *what* greeting it emits.  And we can compose that code into
any request.

Where this becomes powerful is when you're integrating calls to other services that *are*
async into the declarative workflow.  An `Acteur` has a few options that let you stop the
processing of the request until some code has completed and let you resume it, *with some
new objects for injection into later `Acteur`s*.  The simplest way is to call `defer()`
which returns a `CompletableFuture` - or `deferThenRespond(status)` if the object you will
complete the `CompletableFuture` with simply *is* the HTTP payload.

Say we want to look up an object in a remote database.  We can make that *really* generic,
by writing an `Acteur` that simply takes a query object of some sort, defers responding,
makes an async call to the database and completes the `CompletableFuture` with the query
result:

```java
class DBQuery {
  @Inject
  DbQuery(Query query, Provider<DatabaseConnection> conn) {
     CompletableFuture<QueryResult> future = defer();
     conn.get().query(query, (error, results) -> {
        if (error != null) {
            future.completeExceptionally(error);
        } else {
            future.complete(results);
        }
     });
  }
}
```

So, yes, we did have to write a little bit of callback async logic here - precisely
because we're calling someone else's async API.  But the key is *nobody else does!*.
From here out, any acteur that needs the answer to a database query just lists some
acteur that constructs a query out of the request URI or payload or whatever, followed
by `DbQuery` - `@Precursors(CreateTheQuery.class, DbQuery.class)` and take database
result type as an argument.

And that is exactly what the async Postgres and MongoDB support libraries for Acteur
do.

Design Philosophy
-----------------

A few maxims guide the design of Acteur:

* Stay out of the way
* Objects are messages - the application author's own types, not a giant pile of abstractions we force on them
* The power of a threading model is inversely proportional to its visibility to callers
* Don't force the details on applications, but don't lock them away either - if you *want* to
grab hold of the raw socket and shovel bytes down it in Acteur, you can.  You shouldn't need to, but
the framework isn't in the business of telling you what you can and can't do.

Recipes
-------

#### Ensuring Resource Cleanup On Abort

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

#### Turning Exceptions Into Appropriate Error Responses
--------------------------------------------------------

Original Readme
---------------

A further description of the framework's aims can be found in
[this blog](http://timboudreau.com/blog/Acteur/read).  This project uses 
[Netty's](http://netty.io) 4.x (with its revised API) for HTTP.

Read the [acteur tutorial](../../../acteur-tutorial) for a step-by-step
description of building a web API.

Or read the [FAQ](blob/master/FAQ.md).

It's goal is to make it easy to write small, fast, scalable HTTP servers
while ending up with reusable code as a natural side effect of using
the framework.

If you think this project is worthwhile, or there are features you'd like
to see, consider [donating](http://timboudreau.com/donate.html).

It uses a few best-of-breed libraries in addition to Netty to work its magic
(such as Joda Time for dates and times, Jackson for JSON processing and
Guava or MIME types and a few other things).  
Most importantly, it uses Google's Guice dependency injection framework to 
decouple those reusable chunks of logic.

An Acteur application is a subclass of `Application`.  In a pattern
we will see again, most of the code and work of an `Application` happens in its
constructor, and most of it consists of adding `Page` subtypes (`Class` objects)
to the application.

Essentially we are treating constructors as function objects.  Each Acteur construtor
outputs a `State` which can contain additional objects that the next Acteur in
the chain can request by mentioning them in their constructor arguments.

An Application is a list of Pages, and each Page is a list of Acteurs.  Typically
these are all specified by passing Class objects, and instantiated on-demand, once
per request.

Similarly, most of the work of a `Page` also happens in its constructor, in the
form of adding Acteur subclasses (instantiated, or just the types) to the Page.
At runtime, the `Page` instance can be used to set the headers of
the response.  Actual Page and Acteur instances are constructed on a per-request 
basis.

There are a few things you can override if you want to, but the constructor is
where the action is.

This breaking of logic into small classes instantiated by Guice accomplishes
several things:

 * The natural pattern is logic reuse, not copy/paste programming
 * By composing the act of responding to requests into small wads of 
reusable logic, the system can take care of threading issues and interleave
responses to many requests, making it easy to write asynchronous, highly
scalable code from chunks of single-threaded, imperative code - so fewer threads
can handle more requests
 * Individual pieces of logic form natural units for tests
 * Encouraging the use of constructors naturally leads to using final fields.
Many bugs in all types of applications come from having mutable state, or having 
state diffused throughout a lot of objects. So, unlike a lot of JavaBean-based
frameworks, the natural way to do things in Acteur also happens
to be a way that maximizes the help you can get from the compiler to ensure
your code is correct.

This ends up adding up to something resembling recursive callbacks, without the
mess.  Whereas in Javascript one might write

```java
blogs.find(id, function(err, blog) {
    blog.withContent(function(err, content) {
       response.write(content);
    });
});
```

with the caveat that in fact, not of the nested functions run sequentially,
in Acteur, one would write one Acteur to locate the blog and inject it into
the next, and so forth - small, readable, reusable chunks of logic run just
as asynchronously.  Incidentally, since the objects which need to be in-scope
are (literally) contained in the scope, the required memory footprint can be
smaller (only variables that will actually be used are included, rather than
every variable visible from the current call).


Status
------

Acteur *is* in use in a few production web applications in-house - which is
to say it _is_ somebody's _job_ to work on and improve it. 
Recently Netty has undergone major incompatible refactoring.  This 
project will keep up with
such things, but occasionally changes in Netty may make it uncompilable until
Acteur can be updated.  Hopefully the Netty folks will finish their refactoring
soon.


Inspiration
-----------

This framework emerged from a bunch of heterogenous things:

 * A desire to use Netty, and finding it was very low-level
 * Experience with asynchronous programming using [Node.js](http://nodejs.org) and
    wanting to get some of the goodness of its programming model for Java
 * The knowledge that Servlet-based frameworks are not going to play nicely with
    async servers any time soon because to be useful, the whole stack (file and
    database I/O, you name it) needs to be non-blocking or a single-threaded server
    is worse than useless
 * A suspicion that (unlikely as it sounds) NetBeans Visual Library's model of action response 
    chains would actually be a fertile model for the switching required for
    responding to events from an evented server
 * Apache Wicket's lack of fear of constructors and doing work in constructors,
    as compared to the factory fetish of most Java frameworks
 * The observation that if you want Guice to run some code for you, the best bet
    is to put it in a constructor (setter injection is evil anyway)
 * Lots of experience with Guice, and the need for a way to create objects that
    can supply objects to inject into other objects


What Acteur Is
--------------

Acteur takes a page from Apache Wicket in terms of being built around Page
and Application classes which you actually subclass (as opposed to typical
Java web applications with scads of XML and factories for factories).  It is
explicitly not trying to be a declarative framework - someone *could trivially*
create single Page or Application classes that read declarative data and
populate themselves from that.  Rather Acteur is the bones you would build 
such a thing on top of.

Netty supports all sorts of protocols, not just HTTP.  Acteur is specifically
solving the problem of HTTP, at least for now (SPDY is a glimmer in its author's
eye).

Acteur is not in the business of dictating to you how you model data - in fact,
it is focused more on the steps that happen _before_ you get around to producing
a response body (Netty's `ChannelFutureListener`s work quite well for that) - i.e.
doing handling cache-related headers simply, well and up-front rather than as
an afterthought.  And making sure that any logic this will be shared by multiple
HTTP calls can be implemented cleanly as separate pieces.

So, Acteur does not hide Netty's API for actually writing data.  Very simple
HTTP responses can be composed by passing a string to the constructor of the
`RespondWith` state;  for doing more complicated output processing, you probably
want to implement Netty's `ChannelFutureListener` and write directly to the
output channel.

There are a lot of well-done solutions for generating HTML or
JSON, doing templating and that sort of thing.


A Basic Application
-------------------

As mentioned above, an application is composed from Pages, and a Page is 
composed from Acteurs.  Here is what that looks like:

```java
public class App extends Application {

    App() {
        add(HelloPage.class);
    }

    static class HelloPage extends Page {

        @Inject
        HelloPage(ActeurFactory factory) {
            add(factory.matchMethods(Method.GET));
            add(factory.matchPath("^hello$")); // A regular expression
            add(SayHelloActeur.class);
        }
    }

    static class SayHelloActeur extends Acteur {

        @Inject
        SayHelloActeur(Page page, Event evt) {
            page.getReponseHeaders().setContentType("text/html; charset=utf8");
            setState(new RespondWith(HttpResponseStatus.OK,
                    "<html><head><title>Hello</title></head><body><h1>Hello World</h1>"
                    + "Hello from Acteur</body></html>"));
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerModule<App> module = new ServerModule<>(App.class);
        module.start(8192);
    }
}
```

Now, say we would like to have this application look for a url parameter `name`
and generate some customized output.  That lets us show off what you can do with
injection.

We'll add one line to the the application (outermost) class itself, to tell Guice
that `java.lang.String` is a type which may be injected from one Acteur into another
(in practice, String is an odd choice, but it works for a demo):

```java
@ImplicitBindings ( String.class )
```

The framework lets Acteurs dynamically create objects for injection into subsequent
Acteurs.  Guice demands that all type-bindings be configured at application
start-time.  So in order to have a raw String be allowed by Guice, we need to
tell Guice that String is one of the classes it should bind.

Then we modify our Acteurs and page slightly.

```java
static class HelloPage extends Page {
    @Inject
    HelloPage(ActeurFactory factory) {
        add(factory.matchMethods(Method.GET));
        add(factory.matchPath("^hello$"));
        add(factory.requireParameters("name"));
        add(FindNameActeur.class);
        add(SayHelloActeur.class);
    }
}
    
static class FindNameActeur extends Acteur {
    @Inject
    FindNameActeur(Event evt) {
        // name will always be non-null - requireParameters() will have
        // aborted the request with a 400 BAD REQUEST response before we
        // get here if it is missing
        String name = evt.getParameter("name");
        // name will be injected into SayHelloActeur's constructor
        setState(new ConsumedLockedState(name));
    }
}

static class SayHelloActeur extends Acteur {
    @Inject
    SayHelloActeur(Page page, String name) {
        page.getReponseHeaders().setContentType("text/html; charset=utf8");
        setState(new RespondWith(HttpResponseStatus.OK,
                "<html><head><title>Hello</title></head><body><h1>Hello " + name + "</h1>"
                + "Hello from Acteur to " + name + "</body></html>"));
    }
}
```

So if you run this application and run, say

    curl -i http://localhost:8192/hello?name=Tim

you will get a nice personalized hello page.  Admittedly this example is a bit
contrived;  for more real-world uses, see the two `ActeurFactory` methods:

 * `injectRequestBodyAsJSON(Class<T> type)` - this uses Jackson to parse the 
request body into the type requested (if an error occurs, you can handle it by
overriding `Application.onError`)
 * `injectRequestParametersAs` - allows you to provide an Java interface which
should be used to represent the request parameters;  *literally* the method names
should match parameter names;  supported types are Java primitive types,
DateTime and Duration.  So you can simply add the Acteur produced by 
calling `acteurFactory.injectRequestParametersAs(MyType.class)` and then
write an Acteur that requests a `MyType` in its constructor.


More Complex Output
-------------------

The above example simply uses a Java `String` to send all of its output at
once.  If you want to do something more complex, you will simply use Netty's clean and
simple API for writing output to a channel.  Instead of passing the string to
the `RespondWith` constructor, leave it out.  Say you want to pipeline a bunch
of output which may take some time to compute:

```java
static class MyOutputter implements ChannelFutureListener {
    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        future = future.channel().write(Unpooled.wrappedBuffer("The output".getBytes()));
        // add this as a listener to write more output, or add
        // ChannelFutureListener.CLOSE
    }
}
```


Configuration
-------------

Acteur uses the [Giulius](https://github.com/timboudreau/giulius) library 
to facilitate binding values in properties
files to `@Named` values which Guice can inject.  While not going into exhaustive
detail here, what this does is it makes it easy to configure an application
using a hierarchy of properties files.  The default file name is `defaults.properties`
(but you can use something different by specifying it at startup time).  In 
a nutshell, how it works is this:

 * At startup time, the system will look for and merge together properties
files from the following locations, in this order (so values in subsequent files
override earlier ones):
    * All files in JARs (classpath order) named 
`META-INF/settings/generated-defaults.properties` - these are generated using
the `@Defaults` annotation
    * All files in JARs (classpath order) named `META-INF/settings/defaults.properties`
    * `/etc/defaults.properties`
    * `~/defaults.properties` (a file in the process` user's home dir)
    * `./defaults.properties` (a file in the process working dir)

To set base values for things, the easy (and reliable) way is to use the
`@Defaults` annotation - this guarantees that configuration files are optional
and there are always sane default values.  `@Defaults` simply takes an array
of strings, and uses properties-file syntax, e.g.

```java
@Defaults({"port=8192","dbserver=localhost"})
```

This could also be written

```java
@Defaults("port=8192\ndbserver=localhost")
```

but the former is more readable.

Technical Details
-----------------

A lot of the heavy lifting in creating Acteurs which are injected by objects from
other Acteurs is handled by the utility class `ReentrantScope`.  Each
Acteur is instantiated within this scope (available from a getter on the Application).
The scope is entered multiple times, each time contributing any objects provided
by the previous Acteur in the chain.  It is also possible to inject an 
`ExecutorService` which will wrap any Runnables or Callables posted to it in the
current scope contents, so it is possible to run code on a background thread 
with the same scope contents as when it was posted (and in fact, this is how
Acteurs are run).

An Acteur *must* set its state, either by calling `setState()` within
its constructor, or overriding `getState()`.  Three State subclasses
are provided as inner classes of the superclass (so they can only be instantiated
inside an Acteur subclass):

 * `RejectedState` - the Acteur says it doesn't know what to do about the event
 * `ConsumedState` - the Acteur does recognize the request, and perhaps is providing
some objects for the next Acteur to use
 * `ConsumedLockedState` - the Acteur recognizes the request to the degree that it
accepts responsibility for responding - no other Pages will be tried if subsequent
Acteurs reject the request
 * `RespondWith` - processing of the event is complete, and the response should
be sent
