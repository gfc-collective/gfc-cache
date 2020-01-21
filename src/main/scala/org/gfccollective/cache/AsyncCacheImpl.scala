package org.gfccollective.cache

import scala.concurrent.Future
import scala.util.Try
import org.gfccollective.util.SingletonCache

/**
  * AsyncCache Implementation that in addition to a build-load function requires a load function to be implemented
  * that is called on cache-miss.
  *
  * @author Gregor Heine
  * @since 28/Jul/2014 15:58
  */
trait AsyncCacheImpl[K, V] extends AsyncCache[K, V] with AsyncCacheEventNotifierImpl[K, V] with CacheBaseImpl[K, V] {
  self: CacheConfiguration =>

  @volatile
  private var cache: SingletonCache[K] = new SingletonCache[K]

  /**
    * Called when there is a cache miss.
    * @param key - the key for the missing data
    * @return the source data with the given key, or Optional.absent if it doesn't exist
    */
  def getSourceObject(key: K): Future[Option[V]]

  override def get(key: K) = {
    checkStarted()
    cache(key)(sourceObjectInternal(key))
  }

  override def peek(key: K) = asMap.get(key)

  override def flatPeek(key: K) = for {
    entry: Future[Option[V]] <- peek(key)
    futureResult: Try[Option[V]] <- entry.value
    success: Option[V] <- futureResult.toOption
    actualValue <- success
  } yield actualValue

  override def asMap = cache.asMap[Future[Option[V]]].toMap

  override protected def buildCache(kvs: Iterable[(K, V)]): Unit = {
    val newCache = new SingletonCache[K]

    for ((key, value) <- kvs) {
      newCache(key)(Future.successful(Some(value)))
    }

    cache = newCache

    notifyCacheReloadFor(kvs)
  }

  protected def put(key: K, value: Future[Option[V]]): Unit = cache(key)(value)

  private def sourceObjectInternal(key: K): Future[Option[V]] = {
    implicit val context = CacheBaseImpl.defaultContext
    val result: Future[Option[V]] = getSourceObject(key).recover {
      // Recover any failed Future with a successful None
      case ex: Exception =>
        error("Unable to load cache key " + key, ex)
        None
    }

    result.onSuccess {
      case Some(value) => notifyCacheMissFor(key, value)
    }

    result
  }
}
