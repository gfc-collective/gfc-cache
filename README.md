# gfc-cache [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.gfccollective/gfc-cache_2.12/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/org.gfccollective/gfc-cache_2.12) [![Build Status](https://github.com/gfc-collective/gfc-cache/workflows/Scala%20CI/badge.svg)](https://github.com/gfc-collective/gfc-cache/actions) [![Coverage Status](https://coveralls.io/repos/gfc-collective/gfc-cache/badge.svg?branch=master&service=github)](https://coveralls.io/github/gfc-collective/gfc-cache?branch=master)

A library that contains scala caching helper code.
A fork and new home of the former Gilt Foundation Classes (`com.gilt.gfc`), now called the [GFC Collective](https://github.com/gfc-collective), maintained by some of the original authors.


## Getting gfc-cache

The latest version is 1.0.0, which is cross-built against Scala 2.12.x and 2.13.x.

If you're using SBT, add the following line to your build file:

```scala
libraryDependencies += "org.gfccollective" %% "gfc-cache" % "1.0.0"
```

For Maven and other build tools, you can visit [search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Corg.gfccollective).
(This search will also list other available libraries from the GFC Collective.)

## Contents and Example Usage

### org.gfccollective.cache.AsyncCache & AsyncCacheImpl

`AsyncCache` represents an asynchronous cache. It may not contain the whole data, and will return `Future[Option[V]]` to show that the value may be lazy-loaded by issuing a remote call.

`AsyncCacheImpl` is an implementation of `AsyncCache` that adds build-load functionality, which requires a load function to be implemented, which is called on cache-miss.


### org.gfccollective.cache.SyncCache & SyncCacheImpl

`SyncCache` represents an in-memory cache, returning values as `Option[V]` indicating that no values are lazy-loaded on cache-miss.

`SyncCacheImpl` is an implementation of `SyncCache` that adds build-load functionality, which requires a load function to be implemented, which is called on cache-miss.

### org.gfccollective.cache.CacheConfiguration

Mix in this trait to provide configuration for the cache. The following parameters are needed:

- `refreshPeriodMs`: how often to reload the cache, in millis.
- `cacheInitStrategy`: how to initialize the cache, either synchronously or asynchronously.

### Code coverage report

    $ sbt clean coverage test coverageReport


## License

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
