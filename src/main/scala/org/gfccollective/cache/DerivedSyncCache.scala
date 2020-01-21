package org.gfccollective.cache

import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}

import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.util.Try

/**
 *
 * Adds ability to handle sync cache events.
 *
 * @tparam K - key type of the cache to reloaded from
 * @tparam V - value type of the cache to reloaded from
 */
trait SyncCacheEventHandler[K, V] {
  /**
   *
   * Defines callback for cache reload.
   *
   * @param fromCache - the cache that was reloaded
   */
  def onCacheReload(fromCache: Iterable[(K, V)]): Unit
}

/**
 *
 * Adds ability to register <code>SyncCacheEventHandler</code>s.
 *
 * @tparam K - key type for the regisitered <code>SyncCacheEventHandler</code>s.
 * @tparam V - value type for the regisitered <code>SyncCacheEventHandler</code>s.
 */
trait SyncCacheEventNotifier[K, V] {

  /**
   *
   * Register a <code>SyncCacheEventHandler</code> to be notified for cache events.
   *
   * @param handler - the handler to be notified for cache events
   */
  def registerHandler(handler: SyncCacheEventHandler[K, V]): Unit
}

trait SyncCacheEventNotifierImpl[K, V] extends SyncCacheEventNotifier[K, V] {
  private val handlers = new CopyOnWriteArrayList[SyncCacheEventHandler[K, V]]().asScala

  override def registerHandler(handler: SyncCacheEventHandler[K, V]): Unit = {
    handlers.append(handler)
  }

  /**
   *
   * Notifies all registered <code>SyncCacheEventHandler<code>'s that <code>cache</code> was reloaded.
   *
   * @param cache - cache that was reloaded
   */
  def notifyCacheReloadFor(cache: Iterable[(K, V)]): Unit = handlers.foreach { h =>
    Try(h.onCacheReload(cache))
  }
}

/**
 * This trait provides a sync cache implementation with ability to register <code>SyncCacheEventHandler</code>s which will be notified when the cache content is reloaded.
 * Content of this cache is loaded from a parent <code>SyncCache</code> and it is reloaded on parent cache reload.
 *
 * @tparam K - key type of the parent cache
 * @tparam V - value type of the parent cache
 * @tparam L - key type of this cache
 * @tparam W - value type of this cache
 */
trait DerivedSyncCacheImpl[K, V, L, W] extends SyncCache[L, W] with SyncCacheEventNotifierImpl[L, W] with SyncCacheEventHandler[K, V] with DerivedCacheBaseImpl {

  /**
   *  The parent this cache is derived from.
   */
  override def parent: SyncCache[K, V]

  /**
   * This methods defines the process of transforming a key value pair from the parent cache into a iterable collection of key value pairs for the derived cache.
   *
   * @param k - key of the parent cache
   * @param v - value of the parent cache
   * @return -  an iterable collection of derived key,value pairs
   */
  def transformSourceObject(k: K, v: V): Iterable[(L, W)]


  @volatile
  private var cache: concurrent.Map[L, W] = new ConcurrentHashMap[L, W]().asScala

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
          info(s"Bootstrapping derived cache from parent $parent")
          onCacheReload(parent.asMap)
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

  override def get(key: L): Option[W] = {
    checkStarted()
    cache.get(key)
  }

  override def asMap: Map[L, W] = cache.toMap


  /**
   * Reloads the content of this cache from the provided <code>fromCache</code> and notifies all registered <code>SyncCacheEventHandler</code>s for cache reload.
   *
   * @param fromCache - the content of the parent cache that was reloaded
   */
  override def onCacheReload(fromCache: Iterable[(K, V)]): Unit = {
    replaceCache(fromCache.flatMap { case (k, v) => transformSourceObject(k, v) })
  }

  protected def replaceCache(objects: Iterable[(L, W)]): Unit = {
    val tempCache: concurrent.Map[L, W] = new ConcurrentHashMap[L, W]().asScala
    objects.foreach { case (l, w) => tempCache.putIfAbsent(l, w) }

    cache = tempCache
    notifyCacheReloadFor(objects)
  }

  register()
}

/**
  * An implementation of the <code>DerivedSyncCacheImpl</code> that maintains the key type and only transforms the cache values.
  *
  * @tparam K - key type of the parent and this cache
  * @tparam V - value type of the parent cache
  * @tparam W - value type of this cache
  */
trait ValueDerivedSyncCacheImpl[K, V, W] extends DerivedSyncCacheImpl[K, V, K, W] {
  def transformValue(k: K, v: V): Option[W]

  override def transformSourceObject(k: K, v: V): Iterable[(K, W)] = transformValue(k, v).fold(Iterable.empty[(K, W)])(w => Iterable(k -> w))
}
