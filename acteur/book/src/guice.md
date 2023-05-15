# Using Guice with Acteur

Guice has an excellent [users guide](https://github.com/google/guice/wiki/Motivation) - if
you haven't used Guice before, it is highly recommended to become at least a bit familiar
with it and what it does.

There is a subset of Guice features that Acteur utilizes, and none of the Guice extensions
(such as multi-bindings, assisted inject or factory injection - there are much simpler and
clearer ways to do all of those things using vanilla Guice).

A few more dubious features of Guice are actively recommended **against**:

* **`@Provides` Methods on Modules**  They make your code untestable - you can't override these
bindings in subclasses.  You might have a module with 20 bindings that are useful in a test
and one of these critters, and you'll wind up copy/pasting the module just to bind that one
thing differently for tests, and it's guaranteed that the copy for tests won't stay in sync
with the original.  Just don't use `@Provides`.  Particularly with lambdas, so you can just
do `bind(Foo.class).toProvider(() -> ...do what you want...)` they *provide* no value and do
do harm.
* **Field Injection** - except in extraordinarily rare cases, this is simply a bad idea.  For field
injection to work, you have to have mutable fields.  The `final` keyword is one of the most
powerful eliminators of entire classes of *Surprise! Someone changed the value!* bugs on the
planet.  Making that impossible to use for the sake of minor convenience is just not worth it.
It is also very easy to have bugs where code attempts to use fields before they have been set,
since they can only be set *after* the constructor has run.  The bottom line is, nearly any
time field injection is a temptation, the actual problem is a design problem, and you will have
better, more reliable code by revisiting that, not giving in to the temptation.

## Issues New Users of Acteur / Guice Run Into

### Polymorphism and Guice

The key to understanding Guice is that it's all about
[Key](https://google.github.io/guice/api-docs/5.1.0/javadoc/com/google/inject/Key.html)s - a
`Key` aggregates a type (whcih can have generics) plus optionally annotations.

Guice is, effectively, a giant `Map` of `Key<T>` to `Provider<T>`.

Your Guice `Module`s set up *bindings* - map entries - between keys and providers of the key's type.
It's a big map.  

For purposes of looking things up, `Key` might as well be a `String` - that is to say, Guice is
*absolutely literal about types*.  If they key is not an exact match, it will not guess - if you
bind `String` and ask for `CharSequence` as an argument, that's an error.

### Generics and Guice

Where the previous point leads to confusion is when it comes to generics.  Again, `Key` might
as well be a `String`, and Guice will be absolutely literal about matching them.  

You can *create* bindings with generics, but they must be fully reified types.

In a pinch, you *can* bind a provider that shadows the bound type with some type parameter, so
code that wants to inject them doesn't generate compiler warnings - e.g. if you had bound,
say, `List` you could do

```java
Provider<List> rawProvider = binder.getProvider(List.class);
binder.bind(new TypeLiteral<List<Thing>>(){}).toProvider(rawProvider::get);
```


### Generics and Request Scope

The request scope that allows `Acteur`s to provide arguments to subsequent `Acteur`s by
calling `next(Object...)` does **not support generics** currently - lookup is by class.
It could eventually be supported, but the logic required to bind such types (since ability
to use generics in annotations are very limited, and most registration of such types happens
via annotations) would be a fairly baroque for minimal benefit.

In practice, this comes up very infrequently.


### Binding types in request scope

Any type that is going to be passed between `Acteur`s using the request scope **must** be
registered.

The simplest way to do that is to include the type in one or another `@HttpCall` annotation - 
that will cause information about the type to be generated into the registration file in `META-INF/http`,
and it will be loaded reflectively and bound on startup.

Alternately, you can bind a type in request scope programmatically, in a module that has access to
the `RequestScope` instance in use, by calling `scope.bindTypes(ClassA.class, ClassB.class, ...)`.  That
approach also allows you to use `scope.bindTypesAllowingNulls(...)`, which allows a value to be absent
from the request scope without generating an error, assuming you inject a `Provider`, not an object
(Guice doesn't do nullable constructor arguments).

If you're using `ServerBuilder`, just make a `RequestScope` a constructor argument to your module, and
pass the *class* of your module to `ServerBuilder`, and it will find the constructor and use 
when instantiating it (you can also take a `Settings` as a constructor argument).

If you're using the *very* old school approach of subclassing `com.mastfrog.acteur.Application`,
you can annotate it with `@ImplicitBindings` and a list of types (this *only* works on `Application` classes).


### Using RequestScope During Startup

Binding types - particularly using `RequestScope.bindTypesAllowingNulls(Class<?>...)` during startup
appears to present a bit of a chicken-and-egg problem, since the scope is normally instantiated early
in startup, but isn't available earlier than that.

There are several solutions:

1. Use `ServerBuilder` and pass the *class* of your module, not an instance of it, to `ServerBuilder.add()`,
and give it a constructor that takes any combination of a `RequestScope` and/or a `Settings`.
2. Annotate your module with `@GuiceModule`, use `ServerBuilder` without specifying an `Application` subclass,
and it will be found and the same mechanism used to instantiate and install it - just give it a constructor
with what you need.
3. `ServerBuilder` can be passed a `RequestScope` to use.  So can `ServerModule` or `GenericApplicationModule`,
so you could instantiate those with a shared instance and then call 
`injector.getInstance(Server.class).start().await()` to launch the server


### Shutdown and Shutdown Hooks

Giulius, the library that handles settings bindings and injector creation, also handles graceful,
deterministic application *shutdown*.  Ask for an instance of `ShutdownHooks` to be injected, and you
can add tasks to run during shutdown (shutting down, say, database connection pools, ensuring executors
handling requests complete before proceeding, etc.).

To shut down the application, use the instance of `Dependencies` - the thing that creates the injector
and call its `shutdown()` method.

You can even use *Dependencies* to shut down and completely reload an application with all of its
configuration at runtime if you want.  That feature is particularly useful when running tests, where
you want to cleanly exit, and possibly even share a JVM with other concurrently running tests.
