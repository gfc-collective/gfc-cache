package com.gilt.gfc.cache

/**
  * SyncCache Implementation.
  *
  * @author Gregor Heine
  * @since 28/Jul/2014 15:58
  */
trait SyncCacheImpl[K, V] extends SyncCache[K, V] with SyncCacheEventNotifierImpl[K, V] with CacheBaseImpl[K, V] {
  self: CacheConfiguration =>

  @volatile
  private var cache: Map[K, V] = Map.empty

  override def get(key: K) = {
    checkStarted()
    cache.get(key)
  }

  override protected def buildCache(kvs: Iterable[(K, V)]): Unit = {
    cache = kvs.toMap
    notifyCacheReloadFor(kvs)
  }

  override def asMap = cache
}
