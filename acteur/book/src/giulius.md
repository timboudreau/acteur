# Configuration with Giulius

Acteur uses Giulius, a library that for loading and binding the values in properties
files (as well as, optionally, system properties and environment variables) to manage
configuration.  `Settings` is the class at the heart of it - it is a read-methods-only
interface to one or more a backing `Map<String,String>` (or at any rate some key/value
store) with parsing and coercion methods for primitive types, arrays and similar.  It has 
various provisions for loading `Settings` from various sources, in layers that override 
each other - so, it can look for `./someapplication.properties`,
`/etc/someapplication.properties`, overriding each other in that order; and it supports
parsing properties as `--`-prefixed command-line arguments which overrides those.

Giulius-Settings is the library that defines `Settings`.  Giulius is the library that adds
Guice bindings for the contents of `Settings`, and provides a wrapper mechanism for setting
up the Guice `Injector` (and provides the ability to get hold of the injector itself via
injection, which Guice by default disallows - this ability is *why* we're able to have
Acteurs inject things into each other).

*You* don't have to use `Settings` for your own configuration, but Acteur contains a lot
of knobs that can be twiddled via those bindings, so it needs to see at least a `Settings`
bound in the injector.  `ServerBuilder` will set up these bindings for you under the hood.

The Giulius-Help library allows you to generate cli help that lists the available property
names, with descriptions and defaults.

Any setting can be injected using Guice's `@Named` annotation on the constructor argument to
identify the key used to look it up, e.g.

```java
Foo(@Named("maxContentLength") int maxContentLength) { ... }
```

or you can just inject the `Settings` and call it with the keys you need - that approach is
preferable if you want to simply handle the case of a null value, as Guice will not let you
inject a `null` directly.

By default, settings values are bound for the types `String`, `boolean`, `int` and `long`;
if you want additional automatic bindings (say, to `char` or `double` or `byte[]` or `short`),
both `ServerBuilder` and `DependenciesBuilder` have methods that take a varargs array of
the enum `SettingBindings`, which is an enum of all the types that can be bound.  Just bear
in mind that, when adding types, that if you are including system properties, and/or environment
variables in your settings, a separate binding and provider is going to be generated for every
single key times the number of types, and these have a startup performance and runtime 
memory penalty.


## Definining Settings

Acteur and related libraries uses a convention of prefixing the programmatic names of 
string constants used in settings with `SETTINGS_KEY_` so it is clear what they are for.

If you use giulius-help, you can attach a description, cli help and a default value that
will be used if unset using the `@Setting` annotation.  Here is an example from `ServerModule`:

```java
    @Setting(value = "Set the maximum length for inbound requests", tier = SECONDARY,
            pattern="^\\d+$" type = Setting.ValueType.INTEGER, defaultValue = "1048576")
    public static final String MAX_CONTENT_LENGTH = "maxContentLength";
```

The default value you define there will actually be recorded in a defaults `.properties` file
under `/META-INF/settings`, so you can then (assuming you include those properties files in
your settings - a single method call in `SettingsBuilder` will accomplish that) use them
with `@Named` with no fear that a value can be null.

## Configuring settings on startup

A typical acteur application `main` method starts with loading settings and configuring defaults:

```
private static final String APPLICATION_NAME = "TinyMavenProxy";
public static void main(String... args) {
    // First we set some fallback defaults that we want
    Settings settings = new SettingsBuilder(APPLICATION_NAME) // name of the .properties file to look for
        .add(WORKER_THREADS, "6")                             // request handling threads
        .add(EVENT_THREADS, "3")                              // inbound socket select threads
        .add(MAX_CONTENT_LENGTH, "128") // we don't accept PUTs, no need for a big buffer
        .add(PORT, "5956")                                    // by default run on port 5956
        .add("application.name", APPLICATION_NAME)            // Server: header in responses
        .add("cors.enabled", false)                           // Disable CORS
        .add("download-tmp", System.getProperty("java.io.tmpdir"))
        .add(SETTINGS_KEY_DOWNLOAD_THREADS, "24")
        .addFilesystemAndClasspathLocations()                 // load settings from /etc, /opt/local/etc, ~/ and ./
        .parseCommandLineArguments(args).build()              // let command-line arguments override any setting above
        ServerControl ctrl = new ServerBuilder(APPLICATION_NAME)
                .add(new TinyMavenProxy())                    // A module that configures application bindings
                .add(settings)
                .build().start();                             // Start the application
        ctrl.await(); // block the main thread until server shutdown
}
```

In the above example, we start by configuring some defaults for *this* application, which may be
different than the defaults we'd get otherwise - for example, `TinyMavenProxy` runs by default on
port 5956, and its responses should include the header `Server: TinyMavenProxy`.  We also set up
a few defaults that are used by the application's own code.

`addFilesystemAndClasspathLocations()` then looks for files named `TinyMavenProxy.properties` in
a bunch of locations:

1. On the classpath, under `META-INF/settings/TinyMavenProxy.properties` for annotation-generated settings
2. In `/etc` and `/opt/local/etc` for `TinyMavenProxy.properties`
3. In `$HOME` for the current user
4. In `./`, the process working directory

If the same setting is found with different values in a properties file in any of these places, the
latter overrides the former in the order you see above.

`addFilesystemAndClasspathLocations()` aggregates a bunch of individual locations you could also add
individually, if you wanted settings to override each other with a different precedence or to omit some
locations:

```java
    public SettingsBuilder addFilesystemAndClasspathLocations() {
        return addOriginalDefaultsFromClasspath()
                .addGeneratedDefaultsFromClasspath()
                .addDefaultsFromClasspath()
                .addDefaultsFromEtc()
                .addDefaultsFromUserHome()
                .addDefaultsFromProcessWorkingDir();
    }
```

We then pass the aggregated `Settings` to `ServerBuilder`, which sets up the Guice bindings for it,
initializes the injector and starts the server.


# Settings That Affect Acteur

The following list is generated by giulius-help, showing settings that affect how Acteur works.

### Primary Settings
* `port` - The port to run the server on.
_Default: `8123`_

### Secondary Settings
* `acteur.fork.join` - Use fork-join pools for Netty event executors; this has performance benefits but increases memory requirements.
_Default: `false`_
* `aggregateChunks` - Enable HTTP inbound request chunk aggregation.  If set to false, Netty's default HttpObjectAggregator is not used, and the server will not be able to respond to chunked encoding requests unless individual acteurs handle the protocol chunks manually.  This is sometimes desirable in servers that handle large media-file uploads, which should not be pulled into memory.
_Default: `true`_
* `charset` - The character set to use in response headers.
_Default: `UTF-8`_
* `decodeRealIP` - If true, attempt to decode X-Forwarded-For and similar headers and return the result from remote address methods on HttpEvent
_Default: `true`_
* `delay.failed.login.attempts.after` - BasicAuth: In response n failed auth attempts , use an escalating delay before returning subsequent responses to the offending address
_Default: `7`_
* `failed.login.attempt.response.delay` - BasicAuth: The number of seconds to delay responses
_Default: `7`_
* `http.compression.debug` - Debug: Enable extended logging of HTTP compression.
_Default: `false`_
* `input.stream.batch.size` - If using InputStreamActeur, the number of bytes that should be pulled into memory and written to the socket in a single batch.
_Default: `512`_
* `max.allowed.failed.login.attempts` - BasicAuth: In response n failed login attempts, ban a host
_Default: `7`_
* `max.chunk.size` - The maximum size of inbound or outbound HTTP chunks in chunked encoding
_Default: `8192`_
* `max.header.buffer.size` - The maximum size for the buffer used for inbound HTTP headers
_Default: `8192`_
* `max.request.line.length` - Maximum length for the initial line of an inbound HTTP request (useful for denial of service avoidance)
_Default: `4096`_
* `maxContentLength` - Set the maximum length for inbound requests
_Default: `1048576`_
* `randomSaltLength` - PasswordHasher: Length of random salt to use when generating salted hashes of passwords
_Default: `48`_
* `salt` - PasswordHasher: Fixed password salt for use IN TESTS (will throw a ConfigurationError if used in production mode)
_Default: `48`_
* `tarpit.default.expiration.minutes` - BasicAuth: Number of minutes a host should be banned
_Default: `5`_
* `websocket.frame.max.bytes` - Max bytes per websocket frame
_Default: `5242880`_

### Tertiary Settings
* `acteur.bytebuf.allocator` - The Netty bytebuf allocator to use.  Possible values are 'direct', 'heap', 'pooled', 'pooled-custom' or 'directOrHeap' which chooses by platform.
_Default: `pooled`_
* `default.exception.handling` - Enable the default conversion of exceptions into error HTTP responses (if set to false, you should install an ExceptionEvaluator to do something reasonable).
_Default: `true`_
* `helpHtmlUrlPattern` - Defines a regular expression used to match the URL path for HTML help
_Default: `"^help\\.html$"`_
* `helpUrlPattern` - Defines a regular expression used for the URL path for JSON help
_Default: `^help$`_
