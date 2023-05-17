# What Is An Acteur?

An `Acteur` is a subclass of the class `Acteur`.  In its constructor, it sets its
state.

While there is a `setState()` method, typically setting the state is done by
calling one of the convenience `super` methods that delegate to it:

* `next(Object... )` - passes control to the next `Acteur` in the chain, optionally
providing some objects which can be injected into that or subsequent `Acteurs`
that process this request within the current chain
* `reject()` - aborts any further processing of the request by the current chain
(but leaves it available for other chains in the application)
* `reply(HttpResponseStatus status)` and `reply(HttpResponseStatus status, Object msg)`
set the response code, which ends processing of the request by the `Acteur` chain and
causes the headers to be sent (after the `Acteur` constructor exits).  If no message
was provided, but a `ChannelFutureListener` was set to write the response body manually, it
will be invoked once the headers are flushed.

A number of methods exist over `reply(HttpResponseStatus status, Object msg)` to further
simplify using common response codes:

* `ok()` and `ok(Object msg)` - 200 OK
* `created()` and `created(Object msg)` - 201 Created
* `noContent()` - 204 No Content
* `notFound()` and `notFound(Object msg)` - 404 Not Found
* `badRequest()` and `badRequest(Object msg)` - 400 Bad Request

An `Acteur` can be *annotated* with `HttpCall`, which causes the framework to generate
code at compile time that creates a *chain* of `Acteurs` that is given an opportunity
to process every request.  It can examine the request and reject it or respond to it.

Typically you use additional annotations to prepend acteurs to the chain which filter
out requests that `Acteur` cannot answer.  At a minimum, `@Methods` and `@Path` or
`@PathRegex` are usually used (though some applications may set up a last `Acteur` in
the chain as a fallback that either sends an error or does some sort of default request
handling (for example, the Tiny Maven Proxy project reserves a few URL paths for
its homepage, and treats all other paths as potential Maven requests).

> #### Is Every Acteur __Really__ Called For Every Request?
>
> No - if you are using `@Methods` and/or `@Path` or `@PathRegex`, then the
> framework uses that information at runtime to winnow the set of chains that could
> possibly be interested in the request, to avoid unnecessary overhead.
>
> Acteurs that do *not* have such annotations *will* be called for every request.


## A Simple Application With Two Acteurs

The below is an extremely simple application.  At this point, it does not use
any annotations other than `HttpCall`, and results in an application with
two chains of `Acteurs` that can possibly respond to a request:

```java

package com.mastfrog.ademo;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.url.Path;
import javax.inject.Inject;

public class ADemo {

@HttpCall
static class HelloActeur extends Acteur {
    @Inject
    HelloActeur(Path path) {
        if (path.is("/hello")) {
            ok("Hello world\n");
        } else {
            reject();
        }
    }
}

@HttpCall
static class FallbackActeur extends Acteur {
    FallbackActeur() {
        ok("Fallback\n");
    }
}

public static void main(String[] args) throws Exception {
    new ServerBuilder()
            .disableCORS() // improves clarity of the output below
            .disableHelp()
            .build().start(8080).await();
}
}
```

(we disable CORS and help to simplify responses in the text below)

If you run the main method, you can test the application with `curl`:

```sh
user@host ~: curl -i http://localhost:8080/hello/
HTTP/1.1 200 OK
content-encoding: identity
content-length: 12
server: GenericApplication
date: Mon, 8 May 2023 03:58:45 Z
X-Req-ID: famst:1

Hello world
```

and, testing the fallback:

```sh
user@host ~: curl -i http://localhost:8080/
HTTP/1.1 200 OK
content-encoding: identity
content-length: 9
server: GenericApplication
date: Mon, 8 May 2023 04:02:07 Z
X-Req-ID: famsx:0

Fallback
```

## Request Processing Order for Acteurs

There's something to note, above:  The only reason `HelloActeur` gets a crack at
the request before `FallbackActeur` is *because of the order they appear in the
source code, and the fact that they appear in that order in a single source file*.
If they were in different files, then the order would be determined by whatever
order `javac` happened to see the files in at compile-time, which would be quite fragile.

As an experiment, try reversing the order the above `Acteur`s appear in; then
do a clean build and run it again, and you'll see both of the above `curl`
calls get answered by `FallbackActeur`.

While filtering by path and method (which we'll get to momentarily) solves a lot,
there are situations in real applications where you may have multiple failover
`Acteur`s of increasing generality, and need to order them.  Fortunately `HttpCall`
has an optional field, `order`, which is an ad-hoc integer.  We can use it to
solve this problem.  Just change the annotation on `HelloActeur` to `@HttpCall(order=100)`
and on `FallbackActeur` to `@HttpCall(order=200)`.  The numbers are arbitrary - in
any system with numeric ordering constraints, is usually a good idea to space
constraints so that later inserations don't require finding and renumbering unrelated
items.

## Using Annotations and Built-In Acteurs

The above example deliberately did *not* use the normal mechanisms for path or
method matching (you'll notice, the demo above gives the same response whether a
request is a `GET`, `HEAD`, `DELETE`, `PUT`, `POST` or `OPTIONS` request).

What that example makes clear, though, is the utter simplicity of the development
model:  It's `Acteur`s all the way down.

The hard part of writing a web server is usually deciding what to do with a given
request, with onion-layers of recognition, validation, authentication, looking things
up to see if you even can answer it - not writing the response once that's decided.
And information that's looked up to answer a question during validation is often
needed again to answer the query, but not until much later - a factor that contributes
to the spaghetti-code like structure of many web applications.

There are a number of annotations built-in to the framework, which can be used on
`Acteur`s annotated with `@HttpCall` to add various forms of request filtering and
validation.

So the way we'd *really* write `HelloActeur` would be like this:

```java
@HttpCall(order = 100)
@Methods(GET)
@com.mastfrog.acteur.preconditions.Path("/hello")
static class HelloActeur extends Acteur {

    @Inject
    HelloActeur() {
        ok("Hello world\n");
    }
}
```

Now, all `@Methods` does is prepend an `Acteur` to the chain which asks for the `HttpEvent`
and calls `reject()` if the method is not `GET`.

All `@com.mastfrog.acteur.preconditions.Path("/hello")` does is prepend another `Acteur`
to the chain that examines the URI path of the request, and if it is not a single path
element named `hello`, rejects the request.  Leading and trailing slashes in this annotation
are ignored if present.

> #### Do You Have To Use Annotations?
> No, but it is probably the clearest and most convenient way to do it.
>
> The older API for assembling a request-processing chain is still available (and is
> sometimes useful in tests).  You subclass a class which, for historical reasons (inspired
> by Apache Wicket) is named `Page`.  Simply subclass `Page` and take an argument of `ActeurFactory`,
> which has methods returning the same `Acteurs` that would be added for each annotation - 
> e.g. `add(theActeurFactory.matchMethods(GET))`.
>
> In fact, a `Page` subclass is generated by the annotation processor that 
> processes any `@HttpCall`-annotated `Acteur`, which you can find in the generated-sources 
> of your project.


### Built-In Annotations

The following annotations are built into the framework and require no special configuration
to use.

#### Filtering Annotations

These perform tests that cause the request to be *rejected* by the current chain, but do
not send a response or prevent other chains from processing the request:

* `@Methods` - a list of HTTP methods
* `@Path` - a glob-style URL path - e.g. `/foo`, `/foo/*/bar`, `/foo/stuff-*/*`, `/blog/*/read`
* `@PathRegex` - A regular expression path (do NOT use leading or trailing /'s), e.g. `^foo$`,
`$^foo\\/[^\\/]+\\/bar$`, `^foo\\/stuff-[^\\/]+\\/bar`
  * You need to escape `/` characters
  * It is important to start your regular expression with `^` and end with `$` unless you
    *really* want open-ended prefix or suffixes to be matched
  * Typical wildcards such as `.+` *will match on intermediate slashes* - if you want to match
    the text of one path element between slashes, used the inverse of the character class of `/`,
    e.g. `[^\\/]`

#### Validating Annotations

* `@Authenticated` - this annotation generically marks a chain as requiring authentication.  What
that means depends on the binding **you** set up for `AuthenticationActeur`, which is just an
empty abstract class.  By default you get HTTP Basic Authentication, and an implementation of
`com.mastfrog.acteur.auth.Authenticator` must be bound (none is by default).  The default
implementation will send the `WWW-Authenticate` header; the realm used is controlled by the `Settings`
key `realm` or the implementation of the class `Realm` bound.
* `@AuthenticatedIf` - Same as `@Authenticated` but does not require authentication if a given
setting is unset or set to false - this can occasionally be useful in tests.
* `@RequiredUrlParameters` - a list of URL parameters that must be present, or the request is
responded to with a `400 Bad Request` response
* `@BannedUrlParamaters` - a list of URL parameters that, if present, should cause the request to
be responded to with a `400 Bad Request` response
* `@ParametersMustBeNumbersIfPresent` - Returns a `400 Bad Request` response if the URL parameter
cannot be parsed as a number - fields on this annotation specify whether floating point and/or
negative numbers are permitted
* `@CORS` - specify that CORS handling should be used for this request (if it was not enabled
globally) and/or modify the set of headers in responses
* `@MaximumPathLength` - Specify the maximum number of *`/`-separated path elements* this chain
should accept (to specify a global maximum character-length use the setting
`ServerModule.SETTINGS_KEY_MAX_REQUEST_LINE_LENGTH`, which is the string `max.request.line.length`
and simply include enough padding to fit the head of an HTTP request line (e.g. `HTTP1/1 GET `)
* `@RequireParametersIfMethodMatches` - like `RequiredUrlParameters` but also has a field for the
HTTP method
* `@MinimumRequestBodyLength` - specify a *minimum* length for request bodies, sending a 400 if
less than that amount
* `@MaximumRequestBodyLength` - specify a *maximum* length for request bodies
* `@UrlParametersMayNotBeCombined` - specify a set of URL parameters only one of which may be present
* `@UrlParametersMayNotBeCombinedSets` - specifies a collection of `@UrlParametersMayNotBeCombined`
annotations representing distinct combinations of parameters only one of each of which may be present
* `@RequireAtLeastOneUrlParameterFrom` - specifies that at least one of a set of URL parameters must be
present
* `@RequireHeader` - require that a header be present, optionally providing one or more regular
expressions it must match.  You can set the property `whenAbsent` to `REJECT` if you want to provide
a fallback chain for handling requests matching the same path, method, etc. that do *not* have the
header - just ensure the `order` parameter of your `@HttpCall` annotation is set to ensure the
fallback gets the process the request only *after* the one that uses the header.
* `@RequireHeaders` - embeds multiple `@RequireHeader` annotation if you want to require more than one
* `@RequireContentType` - provide a list of MIME types that are allowed.  Note that only the primary
and secondary mime type elements are considered - if you specify `text/plain;charset=utf-16` it will
still match any `text/plain` request.


#### Inbound Content Handling Annotations

* `@InjectRequestBodyAs` - decode the inbound request body as JSON (or whatever the `Codec` bound
in Guice decodes things from) so it can be used as a constructor argument.
* `@InjectUrlParametersAs` - this is a slightly quirky way of obtaining URL parameters - you define
an interface with methods named after URL parameters, returning the type you want them parsed as,
and the framework will generate a dynamic proxy of that interface which can be injected as a
constructor parameter to your Acteur


#### Chain-Modifying Annotations

* `@HttpCall` - this is what marks an `Acteur` as defining a chain of `Acteurs` that should be given
the opportunity to respond to requests
* `@Precursors` - ordered set of `Acteur`s to run ahead of the `@HttpCall`-annotated `Acteur` declaring
the annotation.  They are run after built-in annotation acteurs - so, after path-and-method-matching
and any others installed due to any of the validation annotations listed above
* `@Concluders` - ordered set of `Acteur`s to run *after* the the `@HttpCall`-annotated `Acteur` declaring
the annotation.  If you have some sort of object type that can be used as a payload from a number
of `Acteur`s, and standard JSON serialization isn't what's needed, you can simply bind that type to be
available in the request context, write the logic to turn it into an HTTP payload *once* and use
`@Concluders(MyPayloadSerializingActeur.class)` on all of the chains that need it.


### Built-In Acteurs

There are a number of Acteurs which have no input parameters, and can be used - either
via the `@Precursors` annotation (for validation and early responding) or the
`@Concluders` annotation (for specific payload handling).

* `CheckIfModifiedSinceHeader` - Needs to run *after* an `Acteur` you write which *computes* and
*sets* the `Last-Modified` header on the response.  It will then examine the request for an
`If-Modified-Since` header, and return a `304 Not-Modified` response where appropriate.  Deals
correctly with the pitfall of having local timestamps with fractional seconds versus inbound
headers which do not, because the HTTP header spec for dates does not support them.
* `CheckIfNoneMatchHeader` - Needs to run *after* an `Acteur` you write which *computes* and
*sets* the `ETag` header on the response.  Looks for the `If-None-Match` header in the request,
and returns a `304 Not-Modified` where appropriate.  Deals with the quirk that `ETag`s may
be sent back in double-quotes even though they were sent without them - quotes are stipped from
both the inbound and outbound headers before comparing.
* `CheckIfUnmodifiedSinceHeader` - Same story as `CheckIfModifiedSinceHeader`, but for the HTTP
`If-Unmodified-Since` header used in `POST` and `PUT` requests.
* `ObjectStreamActeur` - Used as a concluder to serialize a JDK `Stream` object as a JSON array
in batches
* `InputStreamActeur` - Copies the contents of a JDK `InputStream` in batches to the response - also
used as a concluder


## Communication Between Acteurs

We've mentioned that your own types can be provided by one `Acteur` and consumed by another.
Let's explore that a little bit.

We'll start from our example code from above, deleting `FallbackActeur`.  And we'll add a
`Person` class for this toy example.  One `Acteur` will look up the name as a URL parameter,
and make it available.  The `Acteur` that formats the response will simply accept it as a
constructor argument and use it in the response:

```java
static class PersonFinder extends Acteur {

    @Inject
    PersonFinder(HttpEvent evt) {
        next(new Person(evt.urlParameter("name")));
    }
}

@HttpCall(scopeTypes = Person.class)
@Methods(GET)
@Path("/hello")
@RequiredUrlParameters("name")
@Precursors(PersonFinder.class)
static class HelloActeur extends Acteur {

    @Inject
    HelloActeur(Person person) {
        ok("Hello " + person + "\n");
    }
}

static final class Person {
    private final String name;

    public Person(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}

public static void main(String[] args) throws Exception {
    new ServerBuilder()
            .disableCORS()
            .disableHelp()
            .build().start().await();
}
```

A few things to note:

* `PersonFinder` doesn't need to check if the URL parameter is present - the
annotation `@RequiredUrlParameters("name")` puts an `Acteur` ahead of it that
checks for existence, and if it is not present, returns an error response - so
our `PersonFinder` and `HelloActeur` will never even be instantiated in that
case
* We added `scopeTypes = Person.class` to our `@HttpCall` annotation - that is
what creates a request-scope binding for `Person`.  If you omit it, you will
get an error at runtime when nothing is in scope

That illustrates the rules for types which are bound in this way, to be provided
by one `Acteur` and injected into another:

* The value must be *non-null* - i.e. you need to guarantee that an instance of
your type will always be present in the scope for any `Acteur` that expects it in
its constructor - if one can't be provided, something needs to abort processing
before such an `Acteur` is reached.
* The type must be bound in request-scope.  You can do this by:
  * Including it in `scopeTypes` in at least one `HttpCall` annotation in your application
  * Binding it programmatically at startup-time using `scope.bindTypes()` or
    `scope.bindTypesAllowingNulls()`
    * `bindTypesAllowingNulls()` *does* allow for null values, but you still cannot
      directly inject a value of the type - instead, inject a `Provider<YourType>`
  * ~~Subclassing `Application` and annotating it with `@ImplicitBindings`~~ (deprecated)
