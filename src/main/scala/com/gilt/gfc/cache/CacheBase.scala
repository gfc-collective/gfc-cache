package com.gilt.gfc.cache

/**
 * Cache base interface providing start and shutdown functions.
 *
 * @author Gregor Heine
 * @since 30/Jul/2014 14:06
 */
private[cache] trait CacheBase {
  /** Starts the loading of the cache. */
  def start(): this.type

  /** Test if the cache has been started. */
  def isStarted: Boolean

  /** Stop the cache from loading. */
  def shutdown()

  protected def checkStarted(): Unit = if (!isStarted) {
    throw new RuntimeException("You must explicitly start the cache before use. Please call .start() as part of your application's startup sequence.")
  }

}
