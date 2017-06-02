package com.gilt.gfc.cache

import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent
import scala.collection.JavaConverters._
import scala.concurrent.Future
import com.gilt.gfc.guava.cache.CacheInitializationStrategy

/**
  * Adapts an AsyncCache to the SyncCache interface. Updates to the async parent cache become visible as the values become available and cache lookups
  * to the sync cache trigger async on-cache-miss. But due to the sync nature of the adapter, these cache misses only become visible when the
  * async call has returned, i.e. some time later. The behaviour will be that a cache miss will return None initially and Some some time later.
  *
  * @author Gregor Heine
  * @since 19/Apr/2016 17:55
  */
trait AsyncToSyncCacheAdapter[K, V] extends SyncCache[K, V] with SyncCacheEventNotifierImpl[K, V] with AsyncCacheEventHandler[K, V] with DerivedCacheBaseImpl {
  /**
    *  The parent this cache is derived from.
    */
  override def parent: AsyncCache[K, V]

  @volatile
  private var cache: concurrent.Map[K, Option[V]] = new ConcurrentHashMap[K, Option[V]]().asScala

  override def onCacheReload(fromCache: Iterable[(K, V)]) = {
    val tempCache = new ConcurrentHashMap[K, Option[V]]().asScala
    fromCache.foreach { case (k, v) => tempCache.putIfAbsent(k, Some(v)) }
    cache = tempCache
    notifyCacheReloadFor(fromCache)
  }

  override def onCacheMissLoad(key: K, value: V): Unit = {
    cache.putIfAbsent(key, Some(value)).fold(notifyCacheReloadFor(asMap)) {
      case None => if (cache.replace(key, None, Some(value))) { notifyCacheReloadFor(asMap) }
      case Some(v) => Unit
    }
  }

  override def get(key: K) = {
    checkStarted()
    val value = cache.get(key)
    if (value == None) {
      parent.get(key)
      cache.putIfAbsent(key, None)
    }
    value.flatten
  }

  override def asMap = cache.collect {
    case (k, Some(v)) => k -> v
  }.toMap

  @volatile
  private var registered = false

  /**
    * Loads the content of this cache from its parent, if the parent is started.
    * Register itself for parent cache change notifications.
    *
    * This method should be called only once, when the cache is ready to receive notifications. Subsequent calls to this method are of no effect.
    *
    * @return this cache ready to receive notifications from its parent
    */
  def register(): this.type = {
    this.synchronized {
      if (!registered && parent != null) {
        parent.registerHandler(this)
        if (parent.isStarted) {
          import com.gilt.gfc.concurrent.ScalaFutures.Implicits.sameThreadExecutionContext

          info(s"Bootstrapping sync cache from async parent $parent")
          val bootstrap: Future[Unit] = Future.sequence(parent.asMap.map {
            case (k, f) => f.map(k -> _)
          }).map { kvs =>
            onCacheReload(kvs.collect { case (k, Some(v)) => k -> v })
          }
          // Block for the result if the parent has a SYNC init strategy.
          // A bit of a hack, since we don't have API access to the parent's config.
          def parentCacheStrategy(p: CacheBase): CacheInitializationStrategy = p match {
            case config: CacheConfiguration => config.cacheInitStrategy
            case derived: DerivedCacheBaseImpl => parentCacheStrategy(derived.parent)
            case _ => CacheInitializationStrategy.SYNC
          }
          if (parentCacheStrategy(parent) == CacheInitializationStrategy.SYNC) {
            import scala.concurrent.Await
            import scala.concurrent.duration.Duration
            info("Waiting for async to sync cache bootstrap to complete...")
            Await.result(bootstrap, Duration.Inf)
          }
        }
        registered = true
      }
    }
    this
  }

  override protected def checkStarted(): Unit = if (!registered) {
    throw new RuntimeException("You must explicitly register the derived cache before use. Please call .register() as part of attaching the cache to it's parent.")
  } else {
    super.checkStarted()
  }

  register()
}
