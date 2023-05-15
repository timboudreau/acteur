# Introduction

Acteur is a framework for writing web servers using [Netty](https://netty.io), with
a radically simple programming model.  It is designed from the ground up for 
scalability, simplicity and code-reuse.

Development of it began in 2011, as a way to get some of the simplicity of 
NodeJS for Java, and as a way to expore a different, clearer, simpler async
programming model that encourages well-structured applications and code-reuse.

## Architecture

Responding to a request - the thing servers do - is a decision tree.  Typically the
first few steps are about classifying the request, followed by authentication and
validation steps, and then obtaining the data needed to answer the request.

Acteur introduces a single, simple, reusable abstraction for a step in the decision
tree of responding to a request - the *`Acteur`*.  The code that responds to a
request is a state-machine.  Each step makes a decision - reject the request,
respond to the request, or keep-going with next `Acteur`.  Effectively, an `Acteur`
examines a request and says *yes*, *no* or *maybe*.

Along the way, `Acteur`s can decorate the eventual response, adding headers - and - this
is key to de-spaghettifying the process of responding to requests and creating reusable
steps for common logic - provide objects that can be injected as arguments into
subsequent steps.

They are stateless, referenced by `.class`, created on-demand per request and
nearly immediately disposed of.  They are, themselves, stateless, but each one emits
a new state for the decision tree engine that called it.

An application may contain many *chains* of Acteurs, each of which examines an inbound
request and gets a crack at responding to it.  If one chain rejects the request (as
opposed to *responding* with an error code), the next one gets a crack at it.

An `Acteur` might do something incredibly simple, such as *See if the request's HTTP
method is `GET`* or *See if the request's path matches this regex*, or far more complex,
such as *Build a database query from the auth info you are passed* or *Invoke the database
query you are passed and make the results available to subsequent `Acteurs`*.

Structured as `Acteurs`, the
[single responsibility principle](https://en.wikipedia.org/wiki/Single-responsibility_principle) is
trivial to follow - each step is naturally making a single decision.

`Acteur`s may be - and usually are - called asynchronously.  Messages between acteurs
are objects - *your* objects with *your* types, with no elaborate message buses 
or layer of abstractions.  Want to make a `Thingamabob` you've computed available
to an `Acteur` that will compute a response much later down the chain?  Just call
`next(new Thingamabob(...))`.

The framework expects you to make those message objects either immutable or thread-safe,
but it does not attempt to save you from yourself by erecting elaborate barriers from
your code talking to ... other parts of your own code, as some frameworks do.

Where necessary, an `Acteur` can pause further processing of the request, in order to
make some asynchronous call (or do blocking I/O in a background thread).


## Inspiration and Influences

It has diverse inspirations and influences:

* NodeJS's general approach to asynchronous servers
* NetBeans' [Visual Library's](https://netbeans.apache.org/tutorials/nbm-visual_library.html)
concept of *action chains*
  * Which, in turn, was heavily influenced by Apple's Cocoa [responder
pattern](https://developer.apple.com/library/archive/documentation/General/Conceptual/Devpedia-CocoaApp/Responder.html)
* [Apache Wicket's](https://wicket.apache.org/) lack of fear of doing heavy lifting in constructors
* The observation that [Guice](https://github.com/google/guice), if you look at it sideways, is really a *constructor
dispatch engine* - like, that's what it actually **is**

In Acteur, the code that responds to a request is a state machine.  You structure
building that response as a series of steps called *acteurs*.  Each one makes a
single decision among three alternatives

* Reject the request
* Continue processing the request, optionally computing some data that will be
available for injection into subsequent Acteur`s
* Respond to the request

The application is structured as a series of such chains of acteurs - typically
each chain handles a different URL path or verb or both.  If one chain rejects a
request the next one gets the opportunity to respond to it.

The work a single `Acteur` does can be as simple as testing if the HTTP method or URL
path is one it recognizes - typically these are the first steps in a chain (and
there are built in `Acteur`s for that, so you can just use an annotation like
`@Method(GET)` or `@Path("/foo")` - as there are for many common tasks), or as
complex as pausing the chain of acteurs, making an async call to a database or
foreign web service, then resume it, making the result of that call available to
any subsequent `Acteur` in that chain.

Think of an `Acteur` as the thing that would be a *callback* in old-school NodeJS.
Only, Java being a strongly typed language, it doesn't have to be inline - it just
has to express the dependencies it needs in its constructor, and the framework
will call it with the arguments it needs.

Acteur aims to be conceptually lightweight - you can get pretty far writing an 
Acteur application, knowing only three methods on `Acteur`, `reject()`,
`next(Object...)` and `reply(HttpResponseStatus, message)`, and a few self-explanatory
methods on `HttpEvent` for getting headers, paths and URL parameters out of
the request.

Up until the point of writing the response payload, there is a *single* abstraction
you deal with - acteurs.  Each one emits a state that amounts to "yes", "no" or
"maybe".  Each one can provide arguments to be consumed by subsequent ones, and
can decorate the pending response with headers (these will be aggregated at the
point of sending the response, and discarded if a later acteur rejects it).


Design Philosophy
-----------------

A few maxims guide the design of Acteur - most of them are things to avoid, based
on experience using and writing other frameworks:

* **Stay Out Of The Way** - Avoid elaborate abstractions if there's a way to do without
them - for example, Acteurs can pass messages between themselves.  What's the simplest
expression of passing something?  `next(Object...)`.  What's the simplest expression of
*receiving* an argument?  Having it simply *be* an argument to a constructor or method.
Where simple is possible, keep things simple.
* **Objects Are Messages** - application authors should get to use *their* types, as
simply and directly as possible, without the framework imposing boilerplate on them.
* **The Power of a Threading Model is Inversely Proportional to its Visibility** - exposed
locks, mutexes, or insistence about doing things on certain threads locks the framework
and its users into assumptions that often don't age well.  Embrace
the [*Hollywood principle*](https://wiki.c2.com/?HollywoodPrinciple) - you write your
logic, express its dependencies and *describe when to execute it*, no how - and the
framework calls it.  In fact, there *is* a switch to put the framework into
synchronous-mode - not that you'd ever need to, but to prove that you can.  Its tests
pass either way - and what that proves is that it is possible to *replace the threading
model* of Acteur without breaking applications.  As soon as you start surfacing details
of it, that becomes impossible.
* **Don't Infantilize the Application Author**  If you *want* to grab hold of
the raw socket and shovel bytes down it in Acteur, you can.  You shouldn't need to, but
the framework isn't in the business of telling you what you can and can't do, or
restricting the set of things that are possible.  Be a framework for adults, as opposed
to, say, inventing a byzantine classloader partitioning and message-passing scheme because
someone *might* pass a not-thread-safe object between threads, so they must be saved from
themselves.
* **Don't Boil the Ocean** - if Netty or some other library has a good abstraction for something,
*use* it, don't create a wrapper just to pretend it was invented here.  `Acteur` is, 
fundamentally, a dispatch layer over Netty and some convenience types, Application code should enot a cargo-cult
that wants to be the source of all types applications see - there are enough frameworks
like that.  Do one thing, and do it really, really well.
