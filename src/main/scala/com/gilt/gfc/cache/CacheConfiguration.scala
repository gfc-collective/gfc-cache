package com.gilt.gfc.cache

import com.gilt.gfc.guava.cache.CacheInitializationStrategy

/**
 * Configuration values (all are mandatory):
 *  - refreshPeriodMs: How long (in millis) to sleep between cache refreshes
 *  - cacheInitStrategy: Whether to initially load the cache asynchronously or synchronously (async/sync)
 *
 * @author kmcgregor
 * @since 7/23/13
 *
 */
trait CacheConfiguration {
  def refreshPeriodMs: Long
  def cacheInitStrategy: CacheInitializationStrategy
}
