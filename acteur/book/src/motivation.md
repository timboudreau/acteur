# Motivation

Asynchronous programming frequently gets derided as *too hard*, or
*callback hell* and things like that.  And the industry has come up
with various solutions to that perceived problem - for example 
Javascript and Rust's async/await, or JDK 21's green threads.

The problem with both of those solutions is that they are solving *the
wrong problem*.  

Async/await is a way to pretend you're writing synchronous code 
when you aren't - so you can write spaghetti-code ... but with 
fewer curly braces.

Green threads are a way to write multithreaded code that isn't 
actually multithreaded - so when you make a call that does blocking
I/O, you can block a whole bunch of requests, not just one.

Asynchronous programming gets knocked as hard because deeply nested
callback structures become hard to reason about.

They become hard to reason about because of a lack of good abstractions
that make asynchronous code easy to reason about.  

**That is the problem that this framework sets out to solve.**


## The Real Problem is Dispatch

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
requests, if it could be untangled from its surroundings.  

The reason such code usually isn't amenable to reuse, and tends toward complexity
is because there isn't an easy way *not* to do the work inline - the built-in
assumption is that that's the normal way to write such code.

## How Acteur Solves It

Each `Acteur` expresses its requirements in its constructor arguments.

It doesn't know or care where they come from, if they were computed
synchronously or asynchronously.  Each one simply - declaratively - expresses
*what it wants*, not how to go get it.  And it can emit some objects
that will be available as a constructor to any `Acteur` that runs
subsequent to it while processing that request.

`Acteurs` *emit* a new state of the state-machine that is the processing of the
request.  But they don't *have* internal state.  They are (nearly) always
referenced declaratively, as a `Class` object.  You can think of them as a
fancy named lambda function that gets its arguments injected into it.

You declare the chain of `Acteurs` that handles a particular URL-path + HTTP method
simply by assembling a list of those `.class` objects.

## Example

Here's some circa 2010 NodeJS pseudo-javascript with most of the logic omitted, taken
from a blog engine written to learn NodeJS:

```js
function comments () {
    var which = url.parse ( self.req.url ).pathname.split ( '/' )[0];
    blogs.withBlog ( which, !isSecure, function withBlog ( err, blog ) {
        if (err || !blog) return httputils.error ( self.res, err );
        function reviseCacheControl(headers) {
            //...
        }
        blog.commentsHash ( function ( err, hsh ) { // computes etag
            if (err) return httputils.error ( self.res, err );
            if (self.req.headers['if-none-match']) {
                // ...
            } else if (self.req.headers['if-modified-since']) {
                // ...
            }
            var lm = 0;
            blog.withCommentsSorted ( !isSecure, null, function ( err, numberOfComments, comment, done ) {
                if (err) return httputils.error ( self.res, err );
                if (comment) {
                    // ... add the comment to the response
                }   
                if (done) {
                    var headers = {
                        'Last-Modified': httputils.rfcDate ( lm ),
                        'ETag': '"' + hsh + '"'
                    };
                    fillInHeaders ( headers );
                    reviseCacheControl( headers );
                    var etagHeader = self.req.headers['if-none-match'];
                    if (etagHeader && ( etagHeader === hsh ) || etagHeader === ( '"' + hsh + '"' )) {
                        self.res.writeHead ( 304, headers );
                        return self.res.end ();
                    }
                    if (self.req.method.toLowerCase () === 'head') {
                        self.res.writeHead ( 200, headers );
                        return self.res.end ( );
                    }
                    httputils.writeWithCompression ( self.req, self.res, JSON.stringify ( allComments ), headers );
                }
            });
        });
```

Now, that might be a bit pathological, and it *is* old-school Javascript.  There are a lot of
different concerns in here - building the response; creating a hash of sorted comments for
an `ETag` header; comparing the computed hash with any header so we can respond with a tiny
`304 Not-Modified` response (it's difficult to overstate the value of doing HTTP cache headers
really, really well, and Acteur makes that easy); determining if it is a `GET` or `HEAD`
request and doing something different based on that; applying HTTP compression; converting
the response to JSON.

Another thing to notice is that some things need to be computed long before they actually get
used.

So how would such logic look in Acteur?  Well, first, every nested function is going to be
a separate Acteur - and some lines that are synchronous here, are logically better separated
into separate, reusable wads of logic.  It would be a list of class objects that do the
following:

1. Match the URL /blog/comments
2. Look up the blog entry and put an instance of `Blog` into the arguments scope for subsequent steps
3. Figure out if the user is authenticated as a superuser / blog owner and make that info available for
subsequent steps
4. Look up the comments for the blog entry and put those into the arguments scope for susbsequent steps,
filtering out moderated / spam comments unless the caller is a superuser
4. Look up the (usually cached) hash of the comments, sorted, and generate an `ETag` header
5. Find the newest comment and generate a `Last-Modified` header
6. Check if the generated `ETag` header matches the request's `If-None-Match` header and respond there if so
(there is a built in `Acteur` for that - you'd just include `CheckIfNonMatchHeader.class` in the chain)
7. Do the same for the `Last-Modified` / `If-Modified-Since` headers - there is a built-in `Acteur` for that
too
8. Set up any remaining response headers and reply with the comments list as JSON

It's 8 steps, but it's basically a step-by-step-list of *what to do to serve comments for a blog entry* - and
many of the steps are ones you could an should reuse for other http calls in the application.

Imagine if each one of those steps corresponded directly to a Java class that does just that one thing.
And adding it to the application is as simple as giving it a list of those classes in order.

That is pretty much what you'll do.

In practice, you don't spell out the list that explicitly (there *is* an older API, subclassing 
`Page`, where you do exactly that).  What you do is pick one step - usually the writing of the
response - to define the HTTP call, and specify what comes before and after using Java annotations.
So this would look something like:

```java
@Methods(GET, HEAD)
@Path("/blog/*/comments") // ... or @PathRegex("^blog\\/\\S{0-64}\\/comments$")
@Authenticated(optional=true)
@Precursors({LookUpBlog.class, // finds the blog on the filesystem or wherever
            LookUpBlogComments.class, // load the set of comments for that blog
            ComputeCommentsCacheHeaders.class, // compute the cache headers
            CheckIfNoneMatchHeader.class, // 304-Not-Modified if if-none-match req header matches
            CheckIfModifiedSinceHeader.class}) // 304-Not-Modified if if-modified-since req header matches
class CommentsActeur {
    CommentsActeur(HttpMethod method, Comment[] comments) { // or use a list or whatever
        switch(method) {
            case HEAD : 
                ok();
                break;
            case GET :
                ok(comments); // will be written as JSON by default
                break;
            default :
                throw new AssertionError("Can't get here");
    }
}
```

We have a single, tight, focused `Acteur` that is simply passed an array of comments

