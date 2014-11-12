Acteur FAQ
==========

Answers that say to use an annotation on your Acteur require that you be using GenericApplication and
that the Acteur in question has the @HttpCall annotation.  There is always a programmatic way to do 
these things as well.

How do I...


####Use Acteur in my application?

If you are using Maven, very simply:

 - Add [the Maven repository described here](http://timboudreau.com/builds) to your `<repositories>` in your Maven pom.xml
 - Set the following dependency (check the website above for what is the latest version):

```xml
        <dependency>
            <artifactId>acteur</artifactId>
            <groupId>com.mastfrog</groupId>
            <version>1.5.1</version>
        </dependency>
```


####Handle an HTTP request to a specific URL?

Assuming you're using ServerBuilder/GenericApplication, simply write an Acteur and annotate it with `@HttpCall`, `@Methods` and `@Path` or `@PathRegex`:

```java

@HttpCall
@Methods(GET)
@Path("hello")
public class HelloActeur extends Acteur {
   
   HelloActeur() {
      ok("Hello World");
   }
}

```

The `ok()` method is a shorthand for `setState(new RespondWith(OK, "hello world"))`.  Replace OK with whatever constant on Netty's `HttpResponseStatus` you want.

The `@HttpCall` annotation indicates that this Acteur is an *http endpoint* - if you're using GenericApplication (if you're using ServerBuilder you are by default) it causes a `Page` subclass to be generated under the hood and be added to the application.

Some Acteurs you'll write will be endpoints;  others will be used just to handle *part* of processing a request.  Annotations such as `@Methods` are only meaningful when applied to an endpoint.


#### Is Acteur MVC?

This is kind of the wrong question, but it gets asked.

Acteur is about building up the graph of objects you'll *need* to write a response, whatever that response happens to be and however you chose to write it.  It's about streaming HTTP responses so you can return a billion row result set as JSON without needing a billion rows worth of memory on your server.  It's about being able to answer with a `304 NOT MODIFIED` without having to do all the work to generate a response just to find out you don't need to send it.  It's about factoring the decision tree about how to respond to a request - which usually becomes spaghetti - into focused, reusable chunks of logic you can combine cleanly, and any of which can bail out without doing unnecessary work.  It's about being absurdly scalable and efficient, with an elegant API that keeps your code focused on your objects, not the framework's.

What responses look like is entirely up to you, and you can use whatever kind of output generation, template engine or whatever you want - MVC or not.  Acteur doesn't impose anything on you in that department.


####Start a server?

The simplest way is to use `ServerBuilder`, which lets you add Guice modules, properties-based settings
used with Guice's `@Named` and start the server.

```java
        Server server = new ServerBuilder()
                .add(new TodoListApp())
                .withType(User.class, DBCursor.class)
                .build();
	ServerControl ctrl = .start(port);
	ctrl.await();
```

That does the following:

 - Add a Guice module called TodoListApp
 - Tell the framework that `User` and `DBCursor` will be produced by Acteurs for injection into other Acteurs
 - Build an instance of `Server`
 - Start it, getting back an object that can stop it or wait for it to exit
 - Wait for the server to exit, blocking the main application thread

Note that the call to `await()` is important - all threads spawned by the server will be daemon threads, so if this is your
main thread, it will exit.


####Create an application

Here is the about the simplest possible Acteur web application:

```java
@HttpCall
@Path("/hello")
@Methods(GET)
public class HelloActeur extends Acteur {

    HelloActeur() {
        ok("Hello world\n");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new ServerBuilder().build().start().await();
    }
}
```


####Build an server as a single JAR file you can run with `java -jar`

There are some complicated incantations of the Maven shade plugin and others that would probably let you do this, but Acteur comes with a Maven plugin specifically for this.  In particular, Acteur uses annotation processors to write some metadata into `META-INF/http` and `META-INF/settings`, and this plugin knows how to merge this data correctly.

Here is an example - just add this to your build process, and add [this Maven repository](http://timboudreau.com/builds) as a *plugin* repository in your `<pluginRepositories>` section of your pom.xml:

```xml
            <plugin>
                <groupId>com.mastfrog</groupId>
                <artifactId>maven-merge-configuration</artifactId>
                <version>1.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <id>compile</id>
                        <goals>
                            <goal>merge-configuration</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <mainClass>com.timboudreau.trackerapi.Timetracker</mainClass>
                    <jarName>tracker-standalone</jarName>
                </configuration>
            </plugin>
```

replacing `jarName` and `mainClass` appropriately, and add the plugin repository:

```xml
        <pluginRepository>
            <id>timboudreau-plugins</id>
            <name>timboudreau.com plugins</name>
            <url>http://timboudreau.com/builds/plugin/repository/everything/</url>
        </pluginRepository>
```


####Send a JSON Response

Assuming Jackson knows how to serialize your object, simply pass it as the message in your `RespondWith` state:

```java
setState(new RespondWith(CREATED, myObject));
```

You can add serializers to Jackson by implementing `JacksonConfigurer` and annotating it with
`@ServiceProvider(service=JacksonConfigurer.class)` - this makes it available to JDK 6's `ServiceLoader`, which is 
used to discover all `JacksonConfigurer`s on the classpath.


####Retrieve the request payload as JSON

The simplest method is to annotate your Acteur with `@InjectRequestBodyAs(MyType.class)`:

```java

@HttpCall
@Methods(PUT)
@Path("signup")
@InjectRequestBodyAs(SignupRequest.class)
class SignUpActeur {
	SignUpActeur(SignupRequest request) { ... }
}
```

If you are not using annotations, you can use e.g. `HttpEvent.getContentAs(SignupRequest.class)`.


####Access HTTP request headers

Your Acteur should ask for an instance of `HttpEvent` in its constructor.  The `Headers` class provides typesafe constants for common HTTP headers, and Headers.stringHeader() can be used to retrieve non-standard headers.

```java
@Inject
MyActeur(HttpEvent evt) {
	DateTime ifModifiedSince = evt.getHeader(Headers.IF_MODIFIED_SINCE);
	String nonStandardHeadder = evt.getHeader(Headers.stringHeader("X-Whatever"));
	...
}
```


####Set HTTP headers on the response

In your Acteur, use the `add(HeaderValueType, T)` method to add headers to the response.

```java
@Inject
MyActeur(...) {
	add(Headers.LAST_MODIFIED, DateTime.now());
	add(Headers.MAX_AGE, Duration.standardMinutes(3));
}
```


####Specify a response code other than 200 OK?

The `ok()` method is a shorthand for `setState(new RespondWith(OK, "hello world"))`.  Replace OK with whatever constant on Netty's `HttpResponseStatus` you want.


####Handle an error?

For simple errors, the `Err` class makes that easy, and allows you to construct structured error messages which are rendered as JSON by default.

```java
setState(new RespondWith(Err.badRequest("bad bad")));
```

To change the formatting of errors, implement and bind `ErrorRenderer`.  To catch exceptions during Acteur construction or while constructing an Acteur's arguments for injection (say, a database connection unavailable), bind an `ExceptionEvaluator` as an eager singleton:

```java
class MyEval extends ExceptionEvaluator {
	public void ErrorResponse evaluate(Throwable t, Acteur acteur, Page page, HttpEvent evt) {
		if (t instanceof BatchUpdateException) {
			return Err.conflict(t.getMessage());
		}
	}
```
and in your Guice module `bind(MyEval.class).asEagerSingleton()` (eager singleton binding ensures it is instantiated and registers itself on server startup).

Acteurs are allowed to throw exceptions from their constructors - it is not the preferred way of doing things, but since it can happen, the framework accepts that it does.  `ExceptionEvaluator` exists to handle those cleanly.


####Stream a response

In your Acteur, two ways:

 - Call `setResponseWriter(ResponseWriter)` - this uses Acteur's API for streaming responses - you implement ResponseWriter, which gets called with an `Output` you can write to, and return an enum constant that says whether to call you back again or that you're done.  This mechanism is handy for most things, and is object rather than byte buffer oriented
 - Call `setResponseBodyWriter(ChannelFutureListener listener)` - This uses Netty's low level API for writing bytes - call `future.channel()`, write and flush some bytes and if you want to be called again, attach `this` as a listener on the future you get from calling `ChannelFuture.writeAndFlush()`.  Use this method if you're calling another asynchronous API and want to continue writing when you get a callback.

In either case, your response writer will only be called once the response headers for your Acteur are flushed to the socket.

If you do this, pass no response message when you call `ok()` or `setState(new RespondWith(...))`.


####Write a single Acteur that handles all URLs

Unlike most frameworks, Acteur imposes no semantics on the meaning of URL paths - it doesn't even care about / characters unless you want it to.  Acteurs process requests in the order they're added to the application, until one accepts it by setting a state of `ConsumedLockedState` or setting an HTTP status.  The `@Path` or `@PathRegex` annotations are optional, and you can also write your own URL-path interpreting code instead.

So, by default, an Acteur receives all requests an earlier one didn't claim.  If you don't set a `@Path` or `@PathRegex` it will receive all URL paths;  if you don't set `@Methods` it will receive all HTTP methods.  You can always do this stuff programmatically, e.g.

```java
@Inject
public MyActeur(HttpEvent evt) {
	if (evt.getMethod() != Method.GET) {
		setState(new RejectedState());
		return;
	}
        ...
}
```

which is functionally identical to annotating your Acteur with `@Methods(GET)`.

If you're using annotations and you want to write a failover Acteur that handles requests nothing else has handled, annotate it with `@HttpCall(Integer.MAX_VALUE)`.

####Find Example Applications

Most of the uses of Acteur have been in commercial applications;  two good sources of examples on GitHub are:

 - [Acteur Timetracker](https://github.com/timboudreau/acteur-timetracker) - a generic web api for creating and modifying events that have durations, which can have ad-hoc metadata associated with them (the author of the framework uses it to track consulting hours)
 - [Meta Update Server](https://github.com/timboudreau/meta-update-center) - a server for NetBeans plugins - a live instance on the web can [be found here](http://timboudreau.com/modules)


####Do Basic Authentication

To use built-in support for HTTP Basic Authentication, simply bind implement and bind `Authenticator`.  Then annotate Acteurs that are HTTP endpoints with `@Authenticated`.

```java
final class AuthenticatorImpl implements Authenticator {

    private final DBCollection users;
    private final PasswordHasher hasher;

    @Inject
    AuthenticatorImpl(@Named(value = "users") DBCollection users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    @Override
    public Object[] authenticate(String realm, BasicCredentials credentials) throws IOException {
        BasicDBObject query = new BasicDBObject("name", credentials.username);
        DBObject userRecord = users.findOne(query);
        if (userRecord != null) {
            String password = (String) userRecord.get("password");
            if (hasher.checkPassword(credentials.password, password)) {
                User user = new User(userRecord.get("_id") + "",
                        (String) userRecord.get("name"),
                        (String) userRecord.get("displayName"));
                System.out.println("RETURN USER " + userRecord);
                return new Object[]{user};
            }
        } else {
            // Security - ensure someone can't probe for what user ids are
            // valid by seeing that requests for non-existent users take less
            //time
            hasher.checkPassword("abcedefg", credentials.password);
        }
        return null;
    }
}
```

You can also use other authentication mechanisms, and still use the `@Authenticated` annotation.  Just implement and bind `AuthenticationActeur` to your authentication Acteur and it will be used wherever the `@Authenticated` message appears.

The above example uses `PasswordHasher` to store hashed versions of passwords rather than cleartext.  The algorithm used by PasswordHasher are configurable.


####Chain together multiple Acteurs

Part of the design of Acteur is that any one Acteur should do *only one thing* - that way, each Acteur is reusable from all the HTTP requests that need to do that task.

Consider the case where you want to look up a user by a name which is part of the URL path.  You don't want to write that logic multiple times.  The `@Precursors` annotation lets you specify a list of Acteurs which come before this one in the chain.  So you write one Acteur that does that lookup, makes the user available (or rejects the request if there is none such):

```java
public class LookupTheUser extends Acteur {
   @Inject
   public LookupTheUser(HttpEvent evt, @Named("users") DBCollection collection) {
	String userName = evt.getPath().getElement(2).toString();
	DBObject user = collection.findOne("name", new ObjectId(userName));
	if (user == null) {
		setState(new RespondWith(Err.badRequest("No such user"));
	} else {
		setState(new ConsumedLockedState(new MyUser(user));
	}
   }
}

@HttpCall(scopeTypes=MyUser.class)
@Precursors(LookupTheUser.class)
@PathRegex("^do\/.*?/something")
@Method(GET)
public class DoSomething extends Acteur {
	@Inject
	public DoSomething(MyUser user) { ... }
}
```

So, the DoSomething acteur specifies that before it comes the LookupTheUser Acteur.  It, in turn, looks up the user, and if one is found,
constructs a `MyUser` object and makes that available for injection into subsequent Acteurs by passing it as part of its `ConsumedLockedState`.

That is the magic trick in chaining together Acteurs - an earlier Acteur can provide objects for injection into later ones by passing them in a `ConsumedLockedState` it sets for its state.  The meaning of that state is "I recognize this request, so don't pass it to other chains of acteurs - I've got this.  Here are some objects later Acteurs may want."

Then, any Acteur that needs to know the user specified in the URL can simply set `LookupTheUser` as a precursor and ask for a `MyUser` to be injected as a constructor argument.

One thing to note here is the `scopeTypes` parameter to the annotation.  This is necessary so the the framework knows that `MyUser` is a class that should be available for injection.  Without that, Guice will try to use the default constructor, which will either fail or have surprising results.


####Pause processing a request until some background work is done

Ask for an instance of `Deferral` in your constructor, and call its `defer()` method.  Your Acteur will still need to set its state.

```java
@HttpCall
@Path("delayedHello")
@Methods(GET)
@Concluders(HelloActeur.class)
class DelayActeur extends Acteur {

	@Inject
	public DelayActeur (Timer timer, Deferral deferral) {
		final Resumer resumer = deferral.defer();
		timer.schedule(new TimerTask() {
			public void run() {
				resumer.resume();
			}
		}, 2000);
		setState(new ConsumedLockedState());
	}
}
```

If you are calling another asynchronous API that will call you back when some data is ready, and that data will affect the response code, `Deferral` is how to do that.  It will simply restart processing of the remaining Acteurs to send the response.

Note that this will *delay sending the response code and response headers* - if you need to just wait for something to process output, use `setResponseWriter()` to stream the response and let the headers be sent earlier.  Delaying a response is sometimes useful, for example with login attempts to make dictionary attacks too expensive to be worth it.


#### Create my own annotation that affect how requests are processed?

You can provide your own annotations that can be applied to Acteurs that are HTTP endpoints, and are processed in the same way as built-in ones like `@Methods` or `@InjectUrlParametersAs`.

To do that, implement `PageAnnotationHandler` and bind it as an eager singleton.  It will be called with a list of Acteurs you can add to.  That's right - all any of these annotations do is insert another Acteur into the chain that comes before the one with the annotation.

```java
    static class PAH extends PageAnnotationHandler {
        @Inject
        PAH(PageAnnotationHandler.Registry reg, Dependencies injector) {
            super(reg, InjectUserFromURL.class);
            this.injector = injector;
        }

        @Override
        public <T extends Page> boolean processAnnotations(T page, List<? super Acteur> addTo) {
            if (page.getClass().getAnnotation(InjectUserFromURL.class) != null) {
                 addTo.add(Acteur.wrap(LookupTheUser.class, injector));
            }
            return true;
        }
    }
```

This is functionally equivalent to `@Precursors(LookupTheUser.class)`, but has benefits for readability and the ability to find all places that its used unambiguously by running find-usages on the annotation.


#### What about threading and immutability?

Acteur encourages functional-style programming - essentially an Acteur is a callback.  Therfore the framework makes *no* guarantees that two acteurs in a chain will be called by the same thread, or that the next call to write output will happen on the same thread as the previous one (it does guarantee that only one thread will be in such methods).

Therefore, if you are passing values between Acteurs, the smartest thing you can do is to make them immutable.  This is not a Java Beans oriented framework (and you will have fewer bugs because of it).

If you must have stateful objects that get modified by multiple Acteurs, take the usual thread-safety precautions.


#### What about session state?

First answer:  There isn't any, and you should avoid that like the plague.  The reason you don't see Java EE in companies like Twitter is very simple:  Java web frameworks all compete on how to make session state easy to program and look like it's free - and it isn't.  Server-side state terribly limits the scalability of web applications.  REST eliminates a lot of the need for it by encoding state in URLs.

Second answer:  It would be trivial to implement if you wanted to:

1.  Implement an Acteur that looks up a serialized array of objects in some backing store, tied to a random cookie that is your session cookie
2.  Provide those objects in its `ConsumedLockedState`
3.  Provide a way to put objects into that store

and presto, all your session state is injectable.

That being said, it's really, really preferable to avoid session state at all costs.


#### What happens under the hood when a request is processed?

Acteur uses Netty, so a ChannelHandler receives the request and creates an HttpEvent for it.

An application is an `Iterable&lt;Page&gt;` - you can think of a `Page` as "thing that can satisfy an HTTP request" (there wasn't a better name for it).

Typically you don't implement `Page` directly anymore (you can if it's useful to, and once in a while it is) - a `Page` is generated for you by the annotation processor for `@HttpCall`.

A `Page` is an `Iterable&lt;Acteur&gt;` - acteurs are instantiated on the fly by Guice as you iterate.  The framework takes care of setting up the injection context, and adding into it objects provided by Acteurs that contribute to it.

When a request arrives, 

 - The first Page is gotten from the application's iterator (typically a new one is created each time).
 - Its first Acteur is instantiated and `getState()` is called on it
 - If the request hasn't been rejected, it continues to the next Acteur and so forth until either a response code is set, or an Acteur rejects the request
 - If the request was rejected, it tries the next page
 - If there are no more pages, it sends a 404
 - If an acteur set up a ChannelFutureListener or ResponseWriter to write a response body, that gets called when the socket flush of the HTTP headers completes

Each Acteur is dispatched separately to a thread-pool.  This has several effects:

 - An application can concurrently process more requests than it has threads - work on individual Acteurs for one request is interleaved with work for other requests
 - It is not guaranteed that all processing of a request happens on the same thread (though it is likely to)

Acteurs are stateful - each one has a response object, which it can write things like header values to.  Only after an acteur succeeds in returning its state are those merged into the response belonging to the Page object, which will only be used if that Page answers the request (so setting headers in an Acteur where a later one will reject processing does not leave behind stray headers).


#### How do I carve up processing a request into Acteurs?

Think through the steps that it takes to answer the request.  Ask yourself:

 - What things are orthagonal? I.e. stuff you need to do, but that doesn't really have much to do with each other
 - What of those things will be needed in more than one request?  You can reuse intermediate Acteurs in multiple endpoints - they are reusable chunks of logic that do one thing and make the results available to subsequent acteurs.
 - Which of those things may enable you to bail out of processing a request early (say, sending 400 BAD REQUEST in response to bad input, or sending 304 NOT MODIFIED because the `If-Modified-Since` header matches?)

So, if you have a sequence like

 - Look up the calling user
 - Look up the user whose data is to be modified
 - Check if the data-user has authorized the calling user to modify their data
 - Check that the `If-Unmodified-Since` header shows that the data hasn't changed since the last time the caller saw it
 - Do a modification

then each one of those can be a reusable Acteur that you can use in any endpoint that needs any of those things.  For example, here is an example from the [acteur-timetracker demo application](https://github.com/timboudreau/acteur-timetracker)

```java
@Precursors({AuthorizedChecker.class, CreateCollectionPolicy.CreatePolicy.class, TimeCollectionFinder.class})
```

This defines a bunch of steps done as a lead up to answering the request:

 - Check that the user is authorized to perform the modification
 - Inject a policy for whether the requested MongoDB collection should be created if it doesn't exist
 - Look up or create the DBCollection and make it available to the final endpoint Acteur as a constructor argument


#### Document my web api

Since you are using annotations, your web api is somewhat self documenting.  If you subclass `Application` or `GenericApplication` and annotate it with `@Help`, HTML documentation will be available at the URL `/help?html=true` and JSON documentation at `/help`.

Annotate your Acteurs and custom annotations with the `@Description` annotation to include a description of the call.


#### Validate URL parameters or JSON data

Acteur uses [Numble](https://github.com/timboudreau/numble) for parameter validation.  So you can annotate your Acteur with `@Params`, define some parameters describing how they are to be validated, and the result will be a generated Java class you can inject into your Acteur with `@InjectUrlParamsAs` or `@InjectRequestBodyAs` and the validation code will be run before your Acteur is instantiated, and errors handled by the error processing framework.

The importance of this approach can't be overstated - typically web code winds up being a tangled mixture of validation code and create-the-response code, and that leads to unmaintainability.  By factoring your validation out and making it declarative, code that handles requests can simply assume the incoming data is good, and stay focused on the logic of the application.


#### Allow my application to be configured?

Acteur uses the [Giulius](https://github.com/timboudreau/giulius) framework for loading settings.  You can provide those settings to your `ServerBuilder`.  A `Settings` is like a `Properties` object, minus the setters/mutators.

With Giulius you can:

 - Provide a name for properties files that will be loaded
 - Define default values using the `@Defaults` annotation, on the class that uses them, so default values are visible in code
 - Layer up properties files, overriding each other - by default, looking in `/etc`, `/opt/local/etc`, `~/` and `./` in that order
 - Parse command-line arguments into settings which override others
 - Request strings, ints, booleans to be injected by annotating them with Guice's `@Named` annotation

Acteur itself can be configured in various ways using `Settings`.  Constants for settings keys that can affect its behavior can be found as static fields on [ServerModule](http://timboudreau.com/builds/job/mastfrog-parent/lastSuccessfulBuild/artifact/acteur-modules/acteur-parent/acteur/target/apidocs/com/mastfrog/acteur/server/ServerModule.html).

Here is an example from the [acteur-timetracker](https://github.com/timboudreau/acteur-timetracker) demo application:

```java
        Settings settings = SettingsBuilder.forNamespace(TIMETRACKER)
                .add("port", "7739")
                .addDefaultLocations()
                .add(PathFactory.BASE_PATH_SETTINGS_KEY, "time")
                .parseCommandLineArguments(args)
                .add(loadVersionProperties())
                .build();

        // Set up the Guice injector with our settings and modules.  Dependencies
        // will bind our settings as @Named
        Server server = new ServerBuilder()
                .add(new JacksonModule())
                .add(new ResetPasswordModule())
                .applicationClass(Timetracker.class)
                .add(settings).build();
                
        server.start().await();
```

What this does:

 - Create a SettingsBuilder that will look for files named timetracker.properties
 - Set a default value for "port" - the port the server will open when it starts
 - Add any properties files in `/etc`, `/opt/local/etc`, `~/` and `./` named timetracker.properties if they exist
 - Set a default for "basePath" (base URL for the application)
 - Override any already set properties with the command-line arguments (so, say, the value of "port" could be overridden by passing `--port 80` on the command-line)
 - Add a Properties object loaded from the Maven version, so the application knows its version
 - Build the Settings
 - Create a ServerBuilder
 - Add the `JacksonModule` that will look for `JacksonConfigurer`s and use them to configure the `ObjectMapper` available for injection
 - Add an internal module for resetting passwords from the command-line
 - Set the application class to Timetracker's subclass of `GenericApplication`
 - Include the settings
 - Create an instance of Server
 - Start it

That's a lot of functionality, tersely but readably coded.


#### Test an Acteur application?

A sibling project is the Netty-based HTTP client and [test harness](https://github.com/timboudreau/netty-http-client#test-harness-netty-http-test-harness).  It integrates with [giulius-tests](https://github.com/timboudreau/giulius-tests) to enable you to write tests with almost no setup code, since you define your modules in an annotation, and your test methods can take arguments.  

Just include `TestHarnessModule` and some subclass of `ServerModule` and whatever else your application needs in your test class's `@TestWith` annotation (frequently your test needs a custom subclass of `ServerModule` that passes your application class the the superclass constructor), and add `@RunWith(GuiceRunner.class)` to tell JUnit to use the guice test runner.

In your test method, take an argument of `TestHarness`.  `TestHarness` detects the instance of `Server` available, starts it on an available port and allows you to make requests to the server, do assertions about the results, etc.  So test code is very clean and boilerplate-free.

[Here is one of Acteur's own tests as an example.](https://github.com/timboudreau/acteur/blob/master/acteur/src/test/java/com/mastfrog/acteur/PutTest.java)


#### Can I have more than one Server in a VM?

Yes - you can start as many as you want, on different ports or whatever, and they will not interfere with each other.  Assuming you don't use static variables in your code (Guice exists so that you don't need to), they will be completely independent of each other.

Additionally, `Dependencies` - the Giulius wrapper for the Guice injector - has a `shutdown()` method, which will shut down everything that uses it (i.e. closing thread pools and database connections).

There is an experimental subproject of Giulius called `signalreload` which will allow you to shut down, reload and restart an Acteur server when the unix signal `HUP` is sent (similar to what NginX or Apache do), which works on Linux but not on Solaris/Illumos.


#### What files are generated from annotations?

Code and metadata is generated liberally from annotations - that is why Acteur needs little explicit configuration or setup code.

 - Giulius
    - The `@Defaults` annotation causes properties files to be generated into `META-INF/settings`, which are loaded by `SettingsBuilder.addDefaultLocations()` or `SettingsBuilder.addDefaultsFromClasspath()`
    - A `/META-INF/settings/namespaaces.list` file may be generated if you use the `@Namespace` annotation to name settings files (namespacing is experimental)
 - Numble
    - The `@Params` annotation causes `/META-INF/http/numble.list` to be generated listing all generated classes so they can be bound for injection
    - It also causes a class named `$CLASS_WITH_ANNOTATIONParams` to be generated
 - Acteur - `@HttpCall`
     - Generates a page class named `$ACTEUR_CLASS_NAME__GenPage`
     - Generates `/META-INF/http/pages.list` which lists page classes which are to be loaded by GenericApplication
 - `@GuiceModule` can specify Guice modules which should be automatically added by GenericApplication if they're on the classpath
 - `@ServiceProvider` - lists classes in `/META-INF/services` that should be available to ServiceLoader/Lookup


