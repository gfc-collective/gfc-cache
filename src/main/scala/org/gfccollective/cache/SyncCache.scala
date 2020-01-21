package org.gfccollective.cache

/**
  * An synchronous cache.
  *
  * SyncCache is expected to be complete in memory and thus return
  * value as Option[V] indicating that no values are lazy-loaded on
  * cache-miss.
  *
  * @author Gregor Heine
  * @since 28/Jul/2014 15:58
  */
trait SyncCache[K, V] extends CacheBase with SyncCacheEventNotifier[K, V] {
  import org.gfccollective.guava.GuavaConverters._
  import com.google.common.base.Optional

  /**
    * Try to load a value from the cache with the given key.
    *
    * This will try to fetch the value from the in-memory cache with
    * no remote call on cache-misses.
    *
    * @param key unique key of the needed value.
    * @return an optional value if found, None otherwise.
    */
  def get(key: K): Option[V]
  final def getOpt(key: K): Optional[V] = get(key).asJava

  def asMap: Map[K, V]
}
