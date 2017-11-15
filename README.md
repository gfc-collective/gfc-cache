# gfc-cache [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gilt/gfc-cache_2.12/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/com.gilt/gfc-cache_2.12) [![Build Status](https://travis-ci.org/gilt/gfc-cache.svg?branch=master)](https://travis-ci.org/gilt/gfc-cache) [![Coverage Status](https://coveralls.io/repos/gilt/gfc-cache/badge.svg?branch=master&service=github)](https://coveralls.io/github/gilt/gfc-cache?branch=master) [![Join the chat at https://gitter.im/gilt/gfc](https://badges.gitter.im/gilt/gfc.svg)](https://gitter.im/gilt/gfc?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A library that contains scala caching helper code. Part of the [Gilt Foundation Classes](https://github.com/gilt?q=gfc).

## Getting gfc-cache

The latest version is 0.1.0, which is cross-built against Scala 2.10.x, 2.11.x and 2.12.x.

If you're using SBT, add the following line to your build file:

```scala
libraryDependencies += "com.gilt" %% "gfc-cache" % "0.1.0"
```

For Maven and other build tools, you can visit [search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Ccom.gilt%20gfc).
(This search will also list other available libraries from the gilt fundation classes.)

## Contents and Example Usage

### com.gilt.gfc.cache.AsyncCache & AsyncCacheImpl

`AsyncCache` represents an asynchronous cache. It may not contain the whole data, and will return `Future[Option[V]]` to show that the value may be lazy-loaded by issuing a remote call.

`AsyncCacheImpl` is an implementation of `AsyncCache` that adds build-load functionality, which requires a load function to be implemented, which is called on cache-miss.


### com.gilt.gfc.cache.SyncCache & SyncCacheImpl

`SyncCache` represents an in-memory cache, returning values as `Option[V]` indicating that no values are lazy-loaded on cache-miss.

`SyncCacheImpl` is an implementation of `SyncCache` that adds build-load functionality, which requires a load function to be implemented, which is called on cache-miss.

### com.gilt.gfc.cache.CacheConfiguration

Mix in this trait to provide configuration for the cache. The following parameters are needed:

- `refreshPeriodMs`: how often to reload the cache, in millis.
- `cacheInitStrategy`: how to initialize the cache, either synchronously or asynchronously.

### Code coverage report

    $ sbt clean coverage test coverageReport


## License
Copyright 2017 Gilt Groupe, Inc. &  HBC Digital.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
