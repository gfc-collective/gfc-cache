package org.gfccollective.cache

import scala.concurrent.Future
import com.google.common.util.concurrent.ListenableFuture

/**
  * Trait that describes an asynchronous cache. AsyncCache is expected to be incomplete in memory and thus return value
  * as Future[Option[V]] indicating that the value may need to be lazy-loaded by issuing a remote call.
  *
  * @author Gregor Heine
  * @since 28/Jul/2014 15:58
  */
trait AsyncCache[K, V] extends CacheBase with AsyncCacheEventNotifier[K,V] {
  import org.gfccollective.concurrent.ScalaFutures.Implicits.sameThreadExecutionContext
  import org.gfccollective.guava.GuavaConverters._
  import org.gfccollective.guava.future.FutureConverters._
  import com.google.common.base.Optional

  /**
    * Try to load a value from the cache with the given key. A cache miss may cause a remote call to look for the value,
    * depending on the cache implementation.
    */
  def get(key: K): Future[Option[V]]
  final def jGet(key: K): ListenableFuture[Optional[V]] = get(key).map(_.asJava).asListenableFuture

  /**
    * Peek if the given key is already cached or not. If it is, it returns a Some of cached Future, otherwise None.
    * This method does not trigger a remote call if the given key is not cached already.
    * @param key
    * @return
    */
  def peek(key: K): Option[Future[Option[V]]]

  /**
    * Peek if the given key is cached, resolved and present or not. If it is, it returns a Some of the value, otherwise None.
    * This works by flattening down the return value of peek.
    * This method does not trigger a remote call if the given key is not cached already.
    * @param key
    * @return
    */
  def flatPeek(key: K): Option[V]

  /**
    * View this cache as an immutable Map. The returned Map is a snapshot of the cache, i.e. any changes to the cache,
    * both incremental and bulk-reloads, are not reflected in the returned Map.
    * @return
    */
  def asMap: Map[K, Future[Option[V]]]
}
