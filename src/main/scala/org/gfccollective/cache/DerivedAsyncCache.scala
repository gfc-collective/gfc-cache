package org.gfccollective.cache

import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Success, Failure, Try}

import org.gfccollective.guava.cache.CacheInitializationStrategy
import org.gfccollective.time.Timer
import org.gfccollective.util.Throwables

/**
 *
 * Adds ability to handle async cache events.
 *
 * @tparam K - key type of the cache to reloaded from
 * @tparam V - value type of the cache to reloaded from
 */
trait AsyncCacheEventHandler[K, V] {
  /**
   *
   * Defines callback for cache reload.
   *
   * @param fromCache - the cache that was reloaded
   */
  def onCacheReload(fromCache: Iterable[(K, V)]): Unit

  /**
   * Defines callback for cache miss.
   *
   * @param key -  key for which we have a cache miss
   * @param value - value for which we have a cache miss
   */
  def onCacheMissLoad(key: K, value: V): Unit
}

/**
 *
 * Adds ability to register <code>AsyncCacheEventHandler</code>s.
 *
 * @tparam K - key type for the regisitered <code>AsyncCacheEventHandler</code>s.
 * @tparam V - value type for the regisitered <code>AsyncCacheEventHandler</code>s.
 */
trait AsyncCacheEventNotifier[K, V] {

  /**
   *
   * Register an <code>AsyncCacheEventHandler</code> to be notified for cache events.
   *
   * @param handler - the handler to be notified for cache events
   */
  def registerHandler(handler: AsyncCacheEventHandler[K, V]): Unit
}


trait AsyncCacheEventNotifierImpl[K, V] extends AsyncCacheEventNotifier[K, V] {
  private[cache] val handlers = new CopyOnWriteArrayList[AsyncCacheEventHandler[K, V]]().asScala

  override def registerHandler(handler: AsyncCacheEventHandler[K, V]): Unit = {
    handlers.append(handler)
  }

  /**
   *
   * Notifies all registered <code>AsyncCacheEventHandler<code>'s that provided <code>cache</code> was reloaded.
   *
   * @param cache - cache that was reloaded
   */
  def notifyCacheReloadFor(cache: Iterable[(K, V)]): Unit = handlers.foreach { h =>
    Try(h.onCacheReload(cache))
  }

  /**
   *
   * Notifies all registered <code>AsyncCacheEventHandler<code>'s for a cache miss for the provided <code>key</code>.
   *
   * @param key - cache key
   * @param value - cache value
   */
  def notifyCacheMissFor(key: K, value: V): Unit = handlers.foreach { h =>
    Try(h.onCacheMissLoad(key, value))
  }

}

/**
 * This trait provides an async cache implementation with ability to register <code>AsyncCacheEventHandler</code>s which will be notified when the cache content is changed as a result of reload or a cache miss.
 * Content of this cache is loaded from a parent <code>AsyncCache</code> and it is reloaded on parent cache realod and updated on parent cache miss.
 *
 * @tparam K - key type of the parent cache
 * @tparam V - value type of the parent cache
 * @tparam L - key type of this cache
 * @tparam W - value type of this cache
 */
private[cache] trait DerivedAsyncCacheBase[K, V, L, W] extends AsyncCache[L, W] with AsyncCacheEventNotifierImpl[L, W] with AsyncCacheEventHandler[K, V] with DerivedCacheBaseImpl {

  /**
   *  The parent this cache is derived from.
   */
  override def parent: AsyncCache[K, V]

  /**
   * Computes the value for a given key <code>k</code> that is missing from the cache.
   *
   * Note: This function will be called during class instantiation if the parent cache has
   * already been started. Implementations should ensure that anything they depend on is
   * already available, for example, as a constructor parameter or through a separate singleton
   * implementation. Don't use values that are defined in the class itself as they would only
   * be available after this base class has been initialized.
   *
   * @param k - cache key which value needs to be computed
   * @return  - the value for the given key
   */
  def onCacheMiss(k: L): Future[Option[W]]

  @volatile
  private var cache: concurrent.Map[L, Future[Option[W]]] = new ConcurrentHashMap[L, Future[Option[W]]]().asScala

  @volatile
  private var registered = false

  /**
   * Loads the content of this cache from its parent, if the parent is started.
   * Register itself for parent cache change notifications.
   *
   * This method should be called only once, when the cache is ready to receive notifications. Subsequent calls to this method are of no effect.
   *
   *
   * @return this cache ready to receive notifications from its parent
   */
  def register(): this.type = {
    this.synchronized {
      if (!registered && parent != null) {
        parent.registerHandler(this)
        if (parent.isStarted) {
          import org.gfccollective.concurrent.ScalaFutures.Implicits.sameThreadExecutionContext

          info(s"Bootstrapping derived cache from parent $parent")
          val bootstrap: Future[Unit] = Future.sequence(parent.asMap.map {
            case (k, f) => f.map(k -> _)
          }).map { kvs =>
            onCacheReload(kvs.collect { case (k, Some(v)) => k -> v })
          }
          maybeBlockForResult(bootstrap)
        }
        registered = true
      }
    }
    this
  }

  /**
    * Block for the result if the parent has a SYNC init strategy.
    * A bit of a hack, since we don't have API access to the parent's config.
    */
  protected def maybeBlockForResult[A](f: Future[A]): Unit = if (!registered || !isStarted) {
    def parentCacheStrategy(p: CacheBase): CacheInitializationStrategy = p match {
      case config: CacheConfiguration => CacheInitializationStrategy.SYNC
      case derived: DerivedCacheBaseImpl => parentCacheStrategy(derived.parent)
      case _ => CacheInitializationStrategy.SYNC
    }
    if (parentCacheStrategy(parent) == CacheInitializationStrategy.SYNC && !f.isCompleted) {
      import scala.concurrent.Await
      import scala.concurrent.duration.Duration
      info("Waiting for derived cache bootstrap to complete...")
      Await.result(f, Duration.Inf)
    }
  }

  override protected def checkStarted(): Unit = if (!registered) {
    throw new RuntimeException("You must explicitly register the derived cache before use. Please call .register() as part of attaching the cache to it's parent.")
  } else {
    super.checkStarted()
  }

  /**
   * Throws exception if the cache is not started, otherwise it will return the cached value for the provided <code>key</code>.
   * If the cache doesn't contain the value for the key <code>onCacheMiss</code> will be called, value will be cached and all registered <code>AsyncCacheEventHandler</code>s will be notified.
   */
  override def get(key: L): Future[Option[W]] = {
    checkStarted()

    implicit val context = CacheBaseImpl.defaultContext

    cache.getOrElse(key, {
      val result: Future[Option[W]] = onCacheMiss(key).recover {
        case NonFatal(ex) =>
          error(s"Unable to load cache key $key: ${Throwables.messages(ex).mkString(" :: ")}")
          None
      }

      cache.putIfAbsent(key, result)

      result.onComplete {
        case Success(Some(value)) => notifyCacheMissFor(key, value)
        case _ => ()
      }

      result
    })
  }

  override def asMap: Map[L, Future[Option[W]]] = cache.toMap

  override def peek(key: L): Option[Future[Option[W]]] = cache.get(key)

  override def flatPeek(key: L): Option[W] = for {
    entry: Future[Option[W]] <- peek(key)
    futureResult: Try[Option[W]] <- entry.value
    success: Option[W] <- futureResult.toOption
    actualValue <- success
  } yield actualValue

  /**
   *
   * Replaces the cache with the proivided <code>objects</code> and notifies all registered <code>AsyncCacheEventHandler</code>s for cache reload.
   *
   */
  protected def replaceCache(objects: Iterable[(L, W)]): Unit = {
    val tempCache: concurrent.Map[L, Future[Option[W]]] = new ConcurrentHashMap[L, Future[Option[W]]]().asScala
    objects.foreach { case (l, w) =>
      tempCache.putIfAbsent(l, Future.successful(Some(w)))
    }

    cache = tempCache
    notifyCacheReloadFor(objects)
  }

  /**
   *
   * Updates the cache with the proivided <code>objects</code> and notifies all registered <code>AsyncCacheEventHandler</code>s for cache miss for each of those objects.
   *
   */
  protected def addToCache(objects: Iterable[(L, W)]): Unit = {
    objects.foreach { case (k, v) =>
      if (cache.putIfAbsent(k, Future.successful(Some(v))) == None) {
        notifyCacheMissFor(k, v)
      }
    }
  }

  register()
}

/**
  * An implementation of the <code>DerivedAsyncCacheBase</code> where all transformations are synchronous and return an
  * Iterable of transformed key/value pairs for each key/value pair of the parent/source cache.
  *
  * @tparam K - key type of the parent cache
  * @tparam V - value type of the parent cache
  * @tparam L - key type of this cache
  * @tparam W - value type of this cache
  */
trait DerivedAsyncCacheImpl[K, V, L, W] extends DerivedAsyncCacheBase[K, V, L, W] {

  /**
   * Transforms a key value pair from the parent cache into a collection of key value pairs for this cache.
   *
   * @param k - key of the parent cache
   * @param v - value of the parent cache
   * @return -  key,value pairs for this cache
   */
  def transformSourceObject(k: K, v: V): Iterable[(L, W)]

  override def onCacheReload(fromCache: Iterable[(K, V)]): Unit = replaceCache(fromCache.flatMap(kv => transformSourceObject(kv._1, kv._2)))

  override def onCacheMissLoad(key: K, value: V): Unit = addToCache(transformSourceObject(key, value))
}

/**
 * An implementation of the <code>DerivedAsyncCacheBase</code> where all transformations are asynchronous and return a
 * Future[Iterable] of transformed key/value pairs for each key/value pair of the parent/source cache.
 *
 * @tparam K - key type of the parent cache
 * @tparam V - value type of the parent cache
 * @tparam L - key type of this cache
 * @tparam W - value type of this cache
 */
trait DerivedAsyncCacheWithAsyncTransformImpl[K, V, L, W] extends DerivedAsyncCacheBase[K, V, L, W] {

  /**
   * Transforms a key value pair from the parent cache into a future of a iterable collection of key value pairs for the derived cache.
   *
   * @param k - key from the parent cache
   * @param v - the value for this <code>key</key> from the parent cache
   * @return - future of an iterable collection of derived key,value pairs
   */
  def transformSourceObject(k: K, v: V): Future[Iterable[(L, W)]] = transformSourceObjects(Iterable(k -> v))

  /**
   * Transforms an iterable collection of key value pairs from the parent cache into a future of a iterable collection of key value pairs for the derived cache.
   *
   * @param kvs - an iterable collection of key value pairs from the parent cache
   * @return - future of an iterable collection of key,value pairs for this cache
   */
  def transformSourceObjects(kvs: Iterable[(K, V)]): Future[Iterable[(L, W)]]

  override def onCacheReload(fromCache: Iterable[(K, V)]): Unit = {
    implicit val context = CacheBaseImpl.defaultContext
    info("Starting async cache bulk transform...")
    val start = System.nanoTime()
    val future = transformSourceObjects(fromCache).map(replaceCache)
    future.onComplete {
      case Success(transformed) => info(s"Async cache bulk transform succeeded after ${Timer.pretty(System.nanoTime() - start)}")
      case Failure(e) =>           error(s"Async cache bulk transform failed after ${Timer.pretty(System.nanoTime() - start)}: ${e.getMessage}", e)
    }
    // Block if we're currently in the process of registering and the parent has a SYNC init stategy
    maybeBlockForResult(future)
  }

  override def onCacheMissLoad(key: K, value: V): Unit = {
    implicit val context = CacheBaseImpl.defaultContext
    transformSourceObject(key, value).onComplete {
      case Success(transformed) => addToCache(transformed)
      case Failure(e) =>           error("A error occurred during async cache miss transformation", e)
    }
  }
}

/**
  * An implementation of the <code>DerivedAsyncCacheImpl</code> that maintains the key type and only transforms the cache values.
  *
  * @tparam K - key type of the parent and this cache
  * @tparam V - value type of the parent cache
  * @tparam W - value type of this cache
  */
trait ValueDerivedAsyncCacheImpl[K, V, W] extends DerivedAsyncCacheImpl[K, V, K, W] {
  def transformValue(k: K, v: V): Option[W]

  override def transformSourceObject(k: K, v: V): Iterable[(K, W)] = transformValue(k, v).fold(Iterable.empty[(K, W)])(w => Iterable(k -> w))

  override def onCacheMiss(k: K): Future[Option[W]] = {
    import org.gfccollective.concurrent.ScalaFutures.Implicits.sameThreadExecutionContext
    parent.get(k).map(_.flatMap(transformValue(k, _)))
  }
}

/**
  * An implementation of the <code>DerivedAsyncCacheWithAsyncTransformImpl</code> that maintains the key type and asynchronously transforms the cache values only.
  *
  * @tparam K - key type of the parent and this cache
  * @tparam V - value type of the parent cache
  * @tparam W - value type of this cache
  */
trait ValueDerivedAsyncCacheWithAsyncTransformImpl[K, V, W] extends DerivedAsyncCacheWithAsyncTransformImpl[K, V, K, W] {
  override def onCacheMiss(k: K): Future[Option[W]] = {
    import org.gfccollective.concurrent.ScalaFutures.Implicits.sameThreadExecutionContext
    for {
      v <- parent.get(k)
      w <- v.fold(Future.successful(Iterable.empty[(K, W)]))(transformSourceObject(k, _))
    } yield w.headOption.map(_._2)
  }
}

