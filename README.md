Acteur
------

Acteur is a framework for writing web server applications with 
[Netty](http://netty.io) by composing together reusable chunks of logic called
<code>Acteur</code>s (think of the Actor pattern, but a little bit foreign :-)).

A further description of the framework's aims can be found in
[this blog](http://timboudreau.com/blog/Acteur/read).

It's goal is to make it easy to write small, fast, scalable HTTP servers
while ending up with reusable code as a natural side effect of using
the framework.

It uses a few best-of-breed libraries in addition to Netty to work its magic
(such as Joda Time for dates and times, Jackson for JSON processing and
Guava or MIME types and a few other things).  
Most importantly, it uses Google's Guice dependency injection framework to 
decouple those reusable chunks of logic.

An Acteur application is, a subclass of `Application`.  In a pattern
we will see again, most of the code and work of an `Application` happens in its
constructor, and most of it consists of adding `Page` subtypes (`Class` objects)
to the application.

Essentially we are treating constructors as function objects.  Each Acteur construtor
outputs a ``State`` which can contain additional objects that the next Acteur in
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

    blogs.find(id, function(err, blog) {
        blog.withContent(function(err, content) {
           response.write(content);
        });
    });

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
``RespondWith`` state;  for doing more complicated output processing, you probably
want to implement Netty's ``ChannelFutureListener`` and write directly to the
output channel.

There are a lot of well-done solutions for generating HTML or
JSON, doing templating and that sort of thing.


A Basic Application
-------------------

As mentioned above, an application is composed from Pages, and a Page is 
composed from Acteurs.  Here is what that looks like:

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

Now, say we would like to have this application look for a url parameter `name`
and generate some customized output.  That lets us show off what you can do with
injection.

We'll add one line to the the application (outermost) class itself, to tell Guice
that `java.lang.String` is a type which may be injected from one Acteur into another
(in practice, String is an odd choice, but it works for a demo):

    @ImplicitBindings ( String.class )

The framework lets Acteurs dynamically create objects for injection into subsequent
Acteurs.  Guice demands that all type-bindings be configured at application
start-time.  So in order to have a raw String be allowed by Guice, we need to
tell Guice that String is one of the classes it should bind.

Then we modify our Acteurs and page slightly.

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

So if you run this application and run, say

    curl -i http://localhost:8192/hello?name=Tim

you will get a nice personalized hello page.  Admittedly this example is a bit
contrived;  for more real-world uses, see the two ActeurFactory methods:

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

The above example simply uses a Java ``String`` to send all of its output at
once.

Configuration
-------------

Acteur uses the [Giulius](https://github.com/timboudreau/giulius) library 
to facilitate binding values in properties
files to @Named values which Guice can inject.  While not going into exhaustive
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

    @Defaults({"port=8192","dbserver=localhost"})

This could also be written

    @Defaults("port=8192\ndbserver=localhost")

but the former is more readable.

Technical Details
-----------------

A lot of the heavy lifting in creating Acteurs which are injected by objects from
other Acteurs is handled by the utility class <code>ReentrantScope</code>.  Each
Acteur is instantiated within this scope (available from a getter on the Application).
The scope is entered multiple times, each time contributing any objects provided
by the previous Acteur in the chain.  It is also possible to inject an 
ExecutorService which will wrap any Runnables or Callables posted to it in the
current scope contents, so it is possible to run code on a background thread 
with the same scope contents as when it was posted (and in fact, this is how
Acteurs are run).

An Acteur *must* set its state, either by calling <code>setState()</code> within
its constructor, or overriding <code>getState()</code>.  Three State subclasses
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
